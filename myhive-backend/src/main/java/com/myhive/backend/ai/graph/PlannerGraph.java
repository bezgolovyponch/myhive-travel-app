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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The whole conversation as one graph; one checkpoint thread per session token. The three
 * {@code await*} nodes are the HTTP boundaries: the run parks before them and a service resumes it
 * after writing a {@link ResumeReason} into the state.
 *
 * <pre>
 *   START            -> chatTurn
 *   chatTurn         -> awaitGeneration  (action = GENERATE)  | awaitUser       (otherwise)
 *   awaitUser        -> select           (resume = SELECT)    | awaitGeneration (resume = GENERATE)
 *                                                             | chatTurn        (otherwise)
 *   awaitGeneration  -> snapshotCatalog  (resume = GENERATE)  | select          (resume = SELECT)
 *                                                             | chatTurn        (otherwise)
 *   snapshotCatalog  -> compose -> validate
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

    /**
     * The only three nodes a thread may be waiting at between two requests. Anything else means a run
     * died in the middle of the graph - see {@link #ensureParked}. The single place to extend when a
     * new park point is added.
     */
    private static final Set<String> PARK_NODES = Set.of(AWAIT_USER, AWAIT_GENERATION, AWAIT_SELECTION);

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
                    .addConditionalEdges(AWAIT_USER, AsyncEdgeAction.edge_async(PlannerGraph::afterWait),
                            Map.of(ROUTE_SELECT, SELECT, ROUTE_CHAT, CHAT_TURN, ROUTE_GENERATE, AWAIT_GENERATION))
                    .addConditionalEdges(AWAIT_GENERATION, AsyncEdgeAction.edge_async(PlannerGraph::afterWait),
                            Map.of(ROUTE_SELECT, SELECT, ROUTE_CHAT, CHAT_TURN, ROUTE_GENERATE, SNAPSHOT_CATALOG))
                    .addEdge(SNAPSHOT_CATALOG, COMPOSE)
                    .addEdge(COMPOSE, VALIDATE)
                    .addConditionalEdges(VALIDATE, AsyncEdgeAction.edge_async(PlannerGraph::afterValidate),
                            Map.of(ROUTE_OK, PERSIST_RESULT, ROUTE_REPAIR, REPAIR, ROUTE_FALLBACK, FALLBACK))
                    .addEdge(REPAIR, VALIDATE)
                    .addEdge(FALLBACK, PERSIST_RESULT)
                    .addEdge(PERSIST_RESULT, AWAIT_SELECTION)
                    .addConditionalEdges(AWAIT_SELECTION, AsyncEdgeAction.edge_async(PlannerGraph::afterWait),
                            Map.of(ROUTE_SELECT, SELECT, ROUTE_CHAT, CHAT_TURN, ROUTE_GENERATE, AWAIT_GENERATION))
                    .addEdge(SELECT, AWAIT_SELECTION);
            this.compiled = workflow.compile(compileConfig(saver));
        } catch (GraphStateException e) {
            throw new IllegalStateException("planner graph definition is invalid", e);
        }
    }

    /**
     * Where a run parks, and on which saver. Shared with the dev Studio instance, which compiles the
     * same {@link #workflow()} on its own saver - a Studio run that did not park at the {@code await*}
     * nodes would race straight through the whole conversation and look nothing like production.
     */
    public static CompileConfig compileConfig(BaseCheckpointSaver saver) {
        return CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptBefore(AWAIT_USER, AWAIT_GENERATION, AWAIT_SELECTION)
                .releaseThread(false)
                .build();
    }

    private static String afterChatTurn(PlannerState state) {
        return PlannerState.ACTION_GENERATE.equals(state.action()) ? ROUTE_GENERATE : ROUTE_WAIT;
    }

    /**
     * All three park points route the same way, on the reason the resuming service wrote. The packages
     * stay on the page while the group keeps chatting, so a selection can arrive from {@link #AWAIT_USER}
     * just as well as from {@link #AWAIT_SELECTION}; routing it to chat instead would re-invoke the model
     * and lose the pick.
     *
     * <p>{@link #AWAIT_GENERATION} is routed too, rather than falling through to the generation branch:
     * an unconditional edge there means <em>any</em> resume of a thread parked at that point builds a
     * plan. A user message arriving after a failed or rejected generation would then run a full
     * generation on the HTTP thread instead of being answered, and a selection arriving while a job is
     * queued would run a second generation concurrently on the same checkpoint thread. Only
     * {@link ResumeReason#GENERATE} reaches {@link #SNAPSHOT_CATALOG} now, and only the generation job
     * writes it.
     */
    private static String afterWait(PlannerState state) {
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
        update(token, values, null);
    }

    /**
     * Guarantees the thread is waiting at one of the {@code await*} nodes, so that whoever resumes it
     * next starts a turn rather than continuing somebody else's half-finished run.
     *
     * <p>langgraph4j writes a checkpoint after every node and {@code GraphInput.resume()} continues
     * from the checkpoint's {@code next}. A generation that dies inside the generation branch - a node
     * that throws, a restart, a job thread that is lost and swept after
     * {@code STALE_AFTER_MINUTES} - therefore leaves {@code next} pointing at a generation-branch
     * node, and the next resume walks straight back into it. Two faces, one cause: the resume runs the
     * ~40-60 s planner model on an HTTP request thread and {@code persistResult} stores its plan for
     * the row the sweep already failed, while the message that triggered the resume is never answered;
     * and if the node fails deterministically, every later message answers 502 instead. The
     * {@link #afterWait} guard cannot help with either - it only runs when a thread <em>leaves</em> an
     * {@code await*} node.
     *
     * <p>Re-parking is only safe while no generation is in flight for this session, because a running
     * job is the one thing that may legitimately be mid-branch. Every caller establishes that first:
     * the three request paths refuse the request with GENERATION_IN_PROGRESS while a QUEUED or RUNNING
     * row exists, and the job itself owns the run it is about to start. A slow job cannot lose that
     * protection either - the sweep skips RUNNING rows this process is still running, so its row keeps
     * holding requests off for as long as it lives. What remains is the single-JVM assumption behind
     * that set: during a deploy overlap two instances serve the same database, and the new one can
     * sweep a row the old one is still running. The blast radius is one generation, the same as before
     * this guard existed.
     *
     * @param lastDeliveredBrief the brief of the newest generation that actually delivered packages,
     *        or {@code null} when there is none; only asked for when a dead run is found
     * @return the node the thread was found at, when it had to be re-parked
     */
    public Optional<String> ensureParked(UUID token, Supplier<String> lastDeliveredBrief) {
        // One state load rather than exists() plus snapshot(): this runs on every resume path.
        String next = compiled.stateOf(configFor(token)).map(StateSnapshot::next).orElse(null);
        // Unknown thread, or a checkpoint with nowhere to go: neither is a run to rescue.
        if (next == null || PARK_NODES.contains(next)) {
            return Optional.empty();
        }
        log.warn("planner thread session={} was left at node={} by a run that never finished; re-parking at {}",
                token, next, AWAIT_USER);
        // asNode replays the router *of* the node named, not the node itself: chatTurn's router sends
        // ACTION=NONE to awaitUser, which is exactly where a finished turn parks. Naming AWAIT_USER
        // here would run its own router instead and land the thread on chatTurn - not a park point.
        update(token, clearedRunState(lastDeliveredBrief.get()), CHAT_TURN);
        return Optional.of(next);
    }

    /**
     * Everything one generation attempt scribbles on the state, reset to what a fresh run expects.
     * The single place to extend when the branch learns a new key.
     *
     * <p>What deliberately survives: MESSAGES, BRIEF and CATALOG (the conversation), RESULT and
     * GENERATION_ID (the packages a previous generation delivered, which the selection screen still
     * offers) and SELECTED_PACKAGE_KEY. Every resume stamps its own GENERATION_ID before it runs, so
     * a stale one is never read.
     */
    private static Map<String, Object> clearedRunState(String lastDeliveredBrief) {
        Map<String, Object> values = new HashMap<>();
        // Load-bearing for the re-park itself: chatTurn's router reads ACTION to pick the park point.
        values.put(PlannerState.ACTION, PlannerState.ACTION_NONE);
        // A reason the dead run left behind would steer the next resume down its branch.
        values.put(PlannerState.RESUME_REASON, "");
        // The repair budget, the findings, the draft and the accounting all belong to the dead attempt.
        values.put(PlannerState.ATTEMPT, 0);
        values.put(PlannerState.VIOLATIONS, "");
        values.put(PlannerState.DRAFT, "");
        values.put(PlannerState.DEGRADED, false);
        values.put(PlannerState.USAGE, "");
        values.put(PlannerState.LAST_ERROR, "");
        // snapshotCatalog stamps this one early, so a run that dies later leaves the chat believing
        // packages exist for the current brief: it would never regenerate on its own again. Restoring
        // the brief of the last generation that really delivered is the middle ground - small talk
        // still costs nothing, a changed brief still rebuilds. Blank when the chat has no packages
        // at all, which makes the next complete brief generate.
        values.put(PlannerState.LAST_GENERATED_BRIEF, lastDeliveredBrief == null ? "" : lastDeliveredBrief);
        return values;
    }

    private void update(UUID token, Map<String, Object> values, String asNode) {
        try {
            compiled.updateState(configFor(token), values, asNode);
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
