package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.edit.PackageEditor;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanTextWriter;
import com.myhive.backend.ai.plan.WishApplier;
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
    /** Makes the organizer's wishes true in every package before anyone sees them; null in a bare test node. */
    private final PackageEditor editor;

    /** The same unresolved provider as {@link PersistResultNode}, for the same constructor-cycle reason. */
    public PublishSkeletonNode(ObjectProvider<PersistResultNode.GenerationResultSink> sinks, PackageEditor editor) {
        this.sinks = sinks::getIfUnique;
        this.editor = editor;
    }

    public PublishSkeletonNode(PersistResultNode.GenerationResultSink sink) {
        this(sink, null);
    }

    public PublishSkeletonNode(PersistResultNode.GenerationResultSink sink, PackageEditor editor) {
        this.sinks = () -> sink;
        this.editor = editor;
    }

    @Override
    public Map<String, Object> apply(PlannerState state) {
        Optional<ComposedPlan> result = state.result();
        if (result.isEmpty()) {
            return Map.of();
        }
        // What was asked for by name is in every package, on the day that was named, before the copy is
        // written for it and before the first poll shows it.
        ComposedPlan wished = WishApplier.apply(editor, result.get(), state.brief(), state.catalog());
        ComposedPlan plan = PlanTextWriter.placeholders(wished, state.locale());
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
