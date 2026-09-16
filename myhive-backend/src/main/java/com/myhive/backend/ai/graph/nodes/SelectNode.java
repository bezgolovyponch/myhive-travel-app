package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.model.Tier;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;
import java.util.UUID;

/** Records which of the three packages the group picked; the thread stays parked at the selection screen. */
@RequiredArgsConstructor
public class SelectNode implements NodeAction<PlannerState> {

    /** Implemented by the generation service; the graph never touches the database itself. */
    public interface SelectionSink {
        void selected(UUID generationId, Tier key);
    }

    private final SelectionSink sink;

    @Override
    public Map<String, Object> apply(PlannerState state) {
        Tier key = state.selectedPackageKey()
                .orElseThrow(() -> new IllegalStateException("select reached without a selected package"));
        state.generationId().ifPresent(id -> sink.selected(id, key));
        return Map.of(PlannerState.RESUME_REASON, "");
    }
}
