package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.plan.ComposedPlan;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Hands the finished plan to whoever stores it, then clears the resume reason so the thread parks cleanly. */
@Slf4j
public class PersistResultNode implements NodeAction<PlannerState> {

    /** Implemented by the generation service; the graph never touches the database itself. */
    public interface GenerationResultSink {
        void ready(UUID generationId, ComposedPlan plan, boolean degraded, LlmUsage usage, int attempt);
    }

    /** Resolves to {@code null} while no sink bean exists; never called during bean construction. */
    private final Supplier<GenerationResultSink> sinks;

    /**
     * The service implementing the sink depends on the graph, so the sink must be looked up when a
     * generation finishes rather than when this node is built - otherwise the two would form a cycle.
     */
    public PersistResultNode(ObjectProvider<GenerationResultSink> sinks) {
        this.sinks = sinks::getIfUnique;
    }

    public PersistResultNode(GenerationResultSink sink) {
        this.sinks = () -> sink;
    }

    @Override
    public Map<String, Object> apply(PlannerState state) {
        ComposedPlan plan = state.result()
                .orElseThrow(() -> new IllegalStateException("persistResult reached without a result"));
        LlmUsage usage = state.usage();
        Map<String, Object> update = new HashMap<>();
        update.put(PlannerState.RESUME_REASON, "");
        update.put(PlannerState.ACTION, PlannerState.ACTION_NONE);
        state.generationId().ifPresentOrElse(
                id -> {
                    sink().ready(id, plan, state.degraded(), usage, state.attempt());
                    // Stamped here and only here for a generation: from now on the state can say which
                    // row the packages it carries actually came from, whatever a later resume writes
                    // into GENERATION_ID.
                    update.put(PlannerState.RESULT_GENERATION_ID, id.toString());
                },
                () -> log.warn("planner result dropped: no generationId in state; result not persisted"));
        return update;
    }

    private GenerationResultSink sink() {
        GenerationResultSink resolved = sinks.get();
        if (resolved != null) {
            return resolved;
        }
        return (generationId, plan, degraded, usage, attempt) ->
                log.warn("planner result dropped generation={}: no GenerationResultSink bean", generationId);
    }
}
