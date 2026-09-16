package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.plan.ComposedPlan;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;
import java.util.UUID;

/** Hands the finished plan to whoever stores it, then clears the resume reason so the thread parks cleanly. */
@RequiredArgsConstructor
public class PersistResultNode implements NodeAction<PlannerState> {

    /** Implemented by the generation service; the graph never touches the database itself. */
    public interface GenerationResultSink {
        void ready(UUID generationId, ComposedPlan plan, boolean degraded, LlmUsage usage, int attempt);
    }

    private final GenerationResultSink sink;

    @Override
    public Map<String, Object> apply(PlannerState state) {
        ComposedPlan plan = state.result()
                .orElseThrow(() -> new IllegalStateException("persistResult reached without a result"));
        LlmUsage usage = state.usage();
        state.generationId().ifPresent(id -> sink.ready(id, plan, state.degraded(), usage, state.attempt()));
        return Map.of(PlannerState.RESUME_REASON, "", PlannerState.ACTION, PlannerState.ACTION_NONE);
    }
}
