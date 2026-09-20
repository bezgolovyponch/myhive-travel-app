package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.plan.ComposedPlan;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Hands the finished plan to whoever stores it, then clears the resume reason so the thread parks cleanly. */
@Slf4j
public class PersistResultNode implements NodeAction<PlannerState> {

    /** Implemented by the generation service; the graph never touches the database itself. */
    public interface GenerationResultSink {
        /**
         * @return whether the plan was stored. {@code false} means nobody will ever show it - the row
         *         was closed out while this run was still going - and the graph has to unwind the
         *         stamps that promise packages.
         */
        boolean ready(UUID generationId, ComposedPlan plan, boolean degraded, LlmUsage usage, int attempt);
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
        Map<String, Object> update = new HashMap<>();
        update.put(PlannerState.RESUME_REASON, "");
        update.put(PlannerState.ACTION, PlannerState.ACTION_NONE);
        if (store(state, plan)) {
            // Everything that says "this chat now has packages" belongs in this branch and nowhere
            // else: a plan nobody stored must leave no trace of itself in the state.
            return update;
        }
        // snapshotCatalog stamped the brief this run was built for before it knew the plan would be
        // thrown away. Left standing it would tell the next chat turn there is nothing to rebuild.
        update.put(PlannerState.LAST_GENERATED_BRIEF, "");
        return update;
    }

    /** @return whether the plan reached a generation row; a plan with no id to store it under never does. */
    private boolean store(PlannerState state, ComposedPlan plan) {
        Optional<UUID> generationId = state.generationId();
        if (generationId.isEmpty()) {
            log.warn("planner result dropped: no generationId in state; result not persisted");
            return false;
        }
        return sink().ready(generationId.get(), plan, state.degraded(), state.usage(), state.attempt());
    }

    private GenerationResultSink sink() {
        GenerationResultSink resolved = sinks.get();
        if (resolved != null) {
            return resolved;
        }
        // Answers false because it really did drop the plan: a misconfigured context must not leave
        // the chat believing it has packages either.
        return (generationId, plan, degraded, usage, attempt) -> {
            log.warn("planner result dropped generation={}: no GenerationResultSink bean", generationId);
            return false;
        };
    }
}
