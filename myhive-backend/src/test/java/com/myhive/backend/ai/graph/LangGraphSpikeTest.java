package com.myhive.backend.ai.graph;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Throwaway-shaped but kept: proves the langgraph4j features the planner relies on.
 *
 * <p>Confirmed working langgraph4j 1.8.13 API surface on Java 25 / Spring Boot 4 (every symbol
 * below compiled and passed as written in the task-1 brief; nothing needed adapting to a
 * different real-API name):
 * <ul>
 *   <li>{@code org.bsc.langgraph4j.StateGraph<S extends AgentState>} — built with
 *       {@code new StateGraph<>(Map<String, Channel<?>> schema, StateSerializer<S> serializer)},
 *       then {@code .addNode(String, AsyncNodeAction<S>)}, {@code .addEdge(String, String)},
 *       {@code .addConditionalEdges(String, AsyncEdgeAction<S>, Map<String, String>)}.
 *       {@code StateGraph.START} / {@code StateGraph.END} are the sentinel node ids.</li>
 *   <li>{@code org.bsc.langgraph4j.action.AsyncNodeAction.node_async(NodeAction<S>)} and
 *       {@code org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async(EdgeAction<S>)} wrap plain
 *       lambdas (a node returns a partial state {@code Map<String, Object>}; an edge action
 *       returns the {@code String} routing key looked up in the conditional-edges map).</li>
 *   <li>{@code org.bsc.langgraph4j.state.Channels.appender(Supplier<Collection<?>>)} defines an
 *       append-only state channel (used here for the {@code "messages"} key).</li>
 *   <li>{@code org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer<S>} is
 *       subclassed with a no-arg constructor that calls {@code super(stateFactory)}, where
 *       {@code stateFactory} is a {@code Function<Map<String, Object>, S>} — here the state's own
 *       {@code Map}-constructor reference, {@code SpikeState::new}.</li>
 *   <li>{@code org.bsc.langgraph4j.checkpoint.MemorySaver} is a ready-made in-memory
 *       {@code BaseCheckpointSaver} — no Postgres needed for this spike.</li>
 *   <li>{@code org.bsc.langgraph4j.CompileConfig.builder().checkpointSaver(saver)
 *       .interruptBefore(String nodeId).releaseThread(boolean).build()} compiles the graph;
 *       {@code workflow.compile(compileConfig)} returns the runnable graph.</li>
 *   <li>{@code org.bsc.langgraph4j.RunnableConfig.builder().threadId(String).build()} scopes a
 *       run/checkpoint to one conversation thread.</li>
 *   <li>{@code graph.stream(Map<String, Object> initialInput, RunnableConfig)} starts (or
 *       restarts) a run; iterating it to exhaustion drains up to the next
 *       {@code interruptBefore} node. To resume a parked run, pass
 *       {@code org.bsc.langgraph4j.GraphInput.resume()} (a static factory, no arguments) as the
 *       input instead of a state map — {@code graph.stream(GraphInput.resume(), config)}.</li>
 *   <li>{@code graph.updateState(RunnableConfig, Map<String, Object> partialState, String
 *       asNode)} patches checkpointed state before a resume; {@code asNode} may be {@code null}
 *       to let the graph's own node reducers apply the patch.</li>
 *   <li>{@code graph.getState(RunnableConfig)} returns a snapshot exposing
 *       {@code .next()} (the id of the node the run is currently parked before, as a
 *       {@code String}) and {@code .state()} (the typed {@code S} state).</li>
 * </ul>
 */
class LangGraphSpikeTest {

    static class SpikeState extends AgentState {
        static final Map<String, Channel<?>> SCHEMA = Map.of(
                "messages", Channels.appender(ArrayList::new));

        SpikeState(Map<String, Object> initData) {
            super(initData);
        }

        List<String> messages() {
            return this.<List<String>>value("messages").orElse(List.of());
        }

        String reason() {
            return this.<String>value("reason").orElse("CHAT");
        }
    }

    static class SpikeSerializer extends JacksonStateSerializer<SpikeState> {
        SpikeSerializer() {
            super(SpikeState::new);
        }
    }

    @Test
    void interruptUpdateStateAndResume_followConditionalEdges() throws Exception {
        StateGraph<SpikeState> workflow = new StateGraph<>(SpikeState.SCHEMA, new SpikeSerializer())
                .addNode("turn", node_async(state -> Map.of("messages", "assistant:" + state.messages().size())))
                .addNode("await", node_async(state -> Map.of()))
                .addNode("generate", node_async(state -> Map.of("messages", "generated")))
                .addEdge(START, "turn")
                .addEdge("turn", "await")
                .addConditionalEdges("await", edge_async(state -> state.reason()),
                        Map.of("CHAT", "turn", "GENERATE", "generate"))
                .addEdge("generate", END);

        MemorySaver saver = new MemorySaver();
        CompileConfig compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptBefore("await")
                .releaseThread(false)
                .build();
        var graph = workflow.compile(compileConfig);
        RunnableConfig config = RunnableConfig.builder().threadId("spike-1").build();

        for (var ignored : graph.stream(Map.of("messages", "user:hi"), config)) {
            // drain until the interrupt
        }
        assertThat(graph.getState(config).next()).isEqualTo("await");
        assertThat(graph.getState(config).state().messages()).containsExactly("user:hi", "assistant:1");

        graph.updateState(config, Map.of("messages", List.of("user:more"), "reason", "CHAT"), null);
        for (var ignored : graph.stream(GraphInput.resume(), config)) {
            // second turn, parks again
        }
        assertThat(graph.getState(config).next()).isEqualTo("await");
        assertThat(graph.getState(config).state().messages()).endsWith("user:more", "assistant:3");

        graph.updateState(config, Map.of("reason", "GENERATE"), null);
        for (var ignored : graph.stream(GraphInput.resume(), config)) {
            // runs generate -> END
        }
        assertThat(graph.getState(config).state().messages()).endsWith("generated");
    }
}
