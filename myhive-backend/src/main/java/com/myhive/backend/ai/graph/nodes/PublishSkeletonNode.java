package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanTextWriter;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Puts the validated, priced packages on the generation row before their copy exists, so a poller can
 * show them while {@link WriteTextsNode} is still talking to the model. The plan in the state gets its
 * placeholder titles here too: whatever the texts call does next, nothing downstream ships nameless.
 *
 * <p>Best effort on purpose. The final store is {@link PersistResultNode}'s; a sink that is missing or
 * fails only costs the early preview, and a node that threw here would re-enter on every later turn.
 */
@Slf4j
public class PublishSkeletonNode implements NodeAction<PlannerState> {

    private final Supplier<PersistResultNode.GenerationResultSink> sinks;

    /** The same unresolved provider as {@link PersistResultNode}, for the same constructor-cycle reason. */
    public PublishSkeletonNode(ObjectProvider<PersistResultNode.GenerationResultSink> sinks) {
        this.sinks = sinks::getIfUnique;
    }

    public PublishSkeletonNode(PersistResultNode.GenerationResultSink sink) {
        this.sinks = () -> sink;
    }

    @Override
    public Map<String, Object> apply(PlannerState state) {
        Optional<ComposedPlan> result = state.result();
        if (result.isEmpty()) {
            return Map.of();
        }
        ComposedPlan plan = PlanTextWriter.placeholders(result.get(), state.locale());
        Optional<UUID> generationId = state.generationId();
        PersistResultNode.GenerationResultSink sink = sinks.get();
        if (generationId.isPresent() && sink != null) {
            try {
                sink.skeleton(generationId.get(), plan);
            } catch (RuntimeException e) {
                log.warn("planner skeleton not published generation={} error={}", generationId.get(),
                        e.getClass().getName());
            }
        }
        return Map.of(PlannerState.RESULT, JsonCodec.write(plan));
    }
}
