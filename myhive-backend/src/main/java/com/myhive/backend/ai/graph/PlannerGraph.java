package com.myhive.backend.ai.graph;

import com.myhive.backend.ai.graph.nodes.ChatTurnNode;
import com.myhive.backend.ai.graph.nodes.ComposeNode;
import com.myhive.backend.ai.graph.nodes.FallbackNode;
import com.myhive.backend.ai.graph.nodes.PersistResultNode;
import com.myhive.backend.ai.graph.nodes.RepairNode;
import com.myhive.backend.ai.graph.nodes.SelectNode;
import com.myhive.backend.ai.graph.nodes.SnapshotCatalogNode;
import com.myhive.backend.ai.graph.nodes.ValidateNode;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.state.StateSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The whole conversation as one graph; one checkpoint thread per session token. The three
 * {@code await*} nodes are the HTTP boundaries: the run parks before them and a service resumes it
 * after writing a {@link ResumeReason} into the state.
 *
 * <pre>
 *   START            -> chatTurn
 *   chatTurn         -> awaitGeneration  (action = GENERATE)  | awaitUser       (otherwise)
 *   awaitUser        -> awaitGeneration  (resume = GENERATE)  | chatTurn        (otherwise)
 *   awaitGeneration  -> snapshotCatalog -> compose -> validate
 *   validate         -> persistResult    (no violations)      | repair (first failure) | fallback
 *   repair           -> validate
 *   fallback         -> persistResult
 *   persistResult    -> awaitSelection
 *   awaitSelection   -> select           (resume = SELECT)    | awaitGeneration (resume = GENERATE)
 *                                                             | chatTurn        (otherwise)
 *   select           -> awaitSelection
 * </pre>
 */
@Slf4j
public class PlannerGraph {

    public static final String CHAT_TURN = "chatTurn";
    public static final String AWAIT_USER = "awaitUser";
    public static final String AWAIT_GENERATION = "awaitGeneration";
    public static final String SNAPSHOT_CATALOG = "snapshotCatalog";
    public static final String COMPOSE = "compose";
    public static final String VALIDATE = "validate";
    public static final String REPAIR = "repair";
    public static final String FALLBACK = "fallback";
    public static final String PERSIST_RESULT = "persistResult";
    public static final String AWAIT_SELECTION = "awaitSelection";
    public static final String SELECT = "select";

    private static final int MAX_REPAIRS = 1;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final String ROUTE_CHAT = "chat";
    private static final String ROUTE_GENERATE = "generate";
    private static final String ROUTE_WAIT = "wait";
    private static final String ROUTE_SELECT = "select";
    private static final String ROUTE_OK = "ok";
    private static final String ROUTE_REPAIR = "repair";
    private static final String ROUTE_FALLBACK = "fallback";

    /** The eight working nodes, in graph order; the {@code await*} nodes have no behaviour of their own. */
    public record Nodes(ChatTurnNode chatTurn, SnapshotCatalogNode snapshotCatalog, ComposeNode compose,
                        ValidateNode validate, RepairNode repair, FallbackNode fallback,
                        PersistResultNode persistResult, SelectNode select) {
    }

    public record PlannerStateSnapshot(PlannerState state, String next) {
    }

    private final StateGraph<PlannerState> workflow;
    private final CompiledGraph<PlannerState> compiled;
    private final BaseCheckpointSaver saver;

    public PlannerGraph(Nodes nodes, BaseCheckpointSaver saver) {
        this.saver = saver;
        try {
            this.workflow = new StateGraph<>(PlannerState.SCHEMA, new PlannerStateSerializer())
                    .addNode(CHAT_TURN, timed(CHAT_TURN, nodes.chatTurn()))
                    .addNode(AWAIT_USER, park(AWAIT_USER))
                    .addNode(AWAIT_GENERATION, park(AWAIT_GENERATION))
                    .addNode(SNAPSHOT_CATALOG, timed(SNAPSHOT_CATALOG, nodes.snapshotCatalog()))
                    .addNode(COMPOSE, timed(COMPOSE, nodes.compose()))
                    .addNode(VALIDATE, timed(VALIDATE, nodes.validate()))
                    .addNode(REPAIR, timed(REPAIR, nodes.repair()))
                    .addNode(FALLBACK, timed(FALLBACK, nodes.fallback()))
                    .addNode(PERSIST_RESULT, timed(PERSIST_RESULT, nodes.persistResult()))
                    .addNode(AWAIT_SELECTION, park(AWAIT_SELECTION))
                    .addNode(SELECT, timed(SELECT, nodes.select()))
                    .addEdge(StateGraph.START, CHAT_TURN)
                    .addConditionalEdges(CHAT_TURN, AsyncEdgeAction.edge_async(PlannerGraph::afterChatTurn),
                            Map.of(ROUTE_GENERATE, AWAIT_GENERATION, ROUTE_WAIT, AWAIT_USER))
                    .addConditionalEdges(AWAIT_USER, AsyncEdgeAction.edge_async(PlannerGraph::afterUserWait),
                            Map.of(ROUTE_CHAT, CHAT_TURN, ROUTE_GENERATE, AWAIT_GENERATION))
                    .addEdge(AWAIT_GENERATION, SNAPSHOT_CATALOG)
                    .addEdge(SNAPSHOT_CATALOG, COMPOSE)
                    .addEdge(COMPOSE, VALIDATE)
                    .addConditionalEdges(VALIDATE, AsyncEdgeAction.edge_async(PlannerGraph::afterValidate),
                            Map.of(ROUTE_OK, PERSIST_RESULT, ROUTE_REPAIR, REPAIR, ROUTE_FALLBACK, FALLBACK))
                    .addEdge(REPAIR, VALIDATE)
                    .addEdge(FALLBACK, PERSIST_RESULT)
                    .addEdge(PERSIST_RESULT, AWAIT_SELECTION)
                    .addConditionalEdges(AWAIT_SELECTION, AsyncEdgeAction.edge_async(PlannerGraph::afterSelectionWait),
                            Map.of(ROUTE_SELECT, SELECT, ROUTE_CHAT, CHAT_TURN, ROUTE_GENERATE, AWAIT_GENERATION))
                    .addEdge(SELECT, AWAIT_SELECTION);
            this.compiled = workflow.compile(CompileConfig.builder()
                    .checkpointSaver(saver)
                    .interruptBefore(AWAIT_USER, AWAIT_GENERATION, AWAIT_SELECTION)
                    .releaseThread(false)
                    .build());
        } catch (GraphStateException e) {
            throw new IllegalStateException("planner graph definition is invalid", e);
        }
    }

    private static String afterChatTurn(PlannerState state) {
        return PlannerState.ACTION_GENERATE.equals(state.action()) ? ROUTE_GENERATE : ROUTE_WAIT;
    }

    private static String afterUserWait(PlannerState state) {
        return ResumeReason.GENERATE.name().equals(state.resumeReason().orElse("")) ? ROUTE_GENERATE : ROUTE_CHAT;
    }

    private static String afterSelectionWait(PlannerState state) {
        String reason = state.resumeReason().orElse("");
        if (ResumeReason.SELECT.name().equals(reason)) {
            return ROUTE_SELECT;
        }
        if (ResumeReason.GENERATE.name().equals(reason)) {
            return ROUTE_GENERATE;
        }
        return ROUTE_CHAT;
    }

    private static String afterValidate(PlannerState state) {
        if (state.violations().isEmpty() && state.result().isPresent()) {
            return ROUTE_OK;
        }
        return state.attempt() < MAX_REPAIRS ? ROUTE_REPAIR : ROUTE_FALLBACK;
    }

    /** A boundary node: it exists only so the run can be interrupted before it. */
    private static AsyncNodeActionWithConfig<PlannerState> park(String name) {
        return timed(name, state -> Map.of());
    }

    /** One INFO line per executed node - session token, node name, latency. Never message bodies or prompts. */
    private static AsyncNodeActionWithConfig<PlannerState> timed(String name, NodeAction<PlannerState> action) {
        return AsyncNodeActionWithConfig.node_async((state, config) -> {
            long startedAt = System.nanoTime();
            Map<String, Object> update = action.apply(state);
            log.info("planner node session={} node={} ms={}", config.threadId().orElse(null), name,
                    (System.nanoTime() - startedAt) / NANOS_PER_MILLI);
            return update;
        });
    }

    public StateGraph<PlannerState> workflow() {
        return workflow;
    }

    public CompiledGraph<PlannerState> compiled() {
        return compiled;
    }

    public RunnableConfig configFor(UUID token) {
        return RunnableConfig.builder().threadId(token.toString()).build();
    }

    /** First run of a thread: seeds the state and runs until the first interrupt. */
    public PlannerStateSnapshot start(UUID token, Map<String, Object> inputs) {
        drain(compiled.stream(inputs, configFor(token)));
        return snapshot(token);
    }

    /**
     * Creates the thread's first checkpoint parked at {@link #AWAIT_USER} without calling the model.
     * The checkpoint saver has nothing to patch yet - {@code updateState} on an unknown thread is
     * rejected with "Missing Checkpoint!" - so the seed runs the graph once with the
     * {@link PlannerState#ACTION_SEED} sentinel, which {@code chatTurn} returns from immediately.
     */
    public PlannerStateSnapshot seedParked(UUID token, Map<String, Object> seed) {
        Map<String, Object> inputs = new HashMap<>(seed);
        inputs.put(PlannerState.ACTION, PlannerState.ACTION_SEED);
        return start(token, inputs);
    }

    /** Resumes a parked thread and runs until the next interrupt. */
    public PlannerStateSnapshot runUntilInterrupt(UUID token) {
        drain(compiled.stream(GraphInput.resume(), configFor(token)));
        return snapshot(token);
    }

    public void update(UUID token, Map<String, Object> values) {
        try {
            compiled.updateState(configFor(token), values, null);
        } catch (Exception e) {
            throw new IllegalStateException("cannot update planner state for " + token, e);
        }
    }

    public PlannerStateSnapshot snapshot(UUID token) {
        StateSnapshot<PlannerState> snapshot = compiled.getState(configFor(token));
        return new PlannerStateSnapshot(snapshot.state(), snapshot.next());
    }

    public boolean exists(UUID token) {
        return compiled.stateOf(configFor(token)).isPresent();
    }

    public void release(UUID token) {
        try {
            saver.release(configFor(token));
        } catch (Exception e) {
            throw new IllegalStateException("cannot release planner thread " + token, e);
        }
    }

    private static void drain(Iterable<?> outputs) {
        for (Object ignored : outputs) {
            // each element is one executed node; the loop ends at the next interrupt
        }
    }
}
