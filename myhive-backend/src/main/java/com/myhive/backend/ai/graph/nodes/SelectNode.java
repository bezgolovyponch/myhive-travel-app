package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.model.Tier;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Records which of the three packages the group picked; the thread stays parked at the selection screen. */
@Slf4j
public class SelectNode implements NodeAction<PlannerState> {

    /** Implemented by the generation service; the graph never touches the database itself. */
    public interface SelectionSink {
        void selected(UUID generationId, Tier key);
    }

    /** Resolves to {@code null} while no sink bean exists; never called during bean construction. */
    private final Supplier<SelectionSink> sinks;

    /**
     * The service implementing the sink depends on the graph, so the sink must be looked up when a
     * selection arrives rather than when this node is built - otherwise the two would form a cycle.
     */
    public SelectNode(ObjectProvider<SelectionSink> sinks) {
        this.sinks = sinks::getIfUnique;
    }

    public SelectNode(SelectionSink sink) {
        this.sinks = () -> sink;
    }

    @Override
    public Map<String, Object> apply(PlannerState state) {
        Tier key = state.selectedPackageKey()
                .orElseThrow(() -> new IllegalStateException("select reached without a selected package"));
        state.generationId().ifPresentOrElse(
                id -> sink().selected(id, key),
                () -> log.warn("planner selection dropped: no generationId in state; selection not persisted"));
        return Map.of(PlannerState.RESUME_REASON, "");
    }

    private SelectionSink sink() {
        SelectionSink resolved = sinks.get();
        if (resolved != null) {
            return resolved;
        }
        return (generationId, key) ->
                log.warn("planner selection dropped generation={}: no SelectionSink bean", generationId);
    }
}
