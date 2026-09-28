package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PublishSkeletonNodeTest {

    private static final ComposedPlan NAMELESS = new ComposedPlan(List.of(new ComposedPlan.PackageResult(Tier.BASIC,
            null, null, null, new BigDecimal("40.00"), new BigDecimal("160.00"), ComposedPlan.CURRENCY, 90, List.of(),
            List.of(new ComposedPlan.DayResult(1, null, null, List.of())))), false);

    private static PlannerState stateWith(UUID generationId, String locale) {
        Map<String, Object> values = new HashMap<>();
        values.put(PlannerState.RESULT, JsonCodec.write(NAMELESS));
        values.put(PlannerState.LOCALE, locale);
        if (generationId != null) {
            values.put(PlannerState.GENERATION_ID, generationId.toString());
        }
        return new PlannerState(values);
    }

    @Test
    void publishesThePlanWithPlaceholderTitles_andWritesItBackToTheState() {
        UUID expectedGenerationId = UUID.randomUUID();
        AtomicReference<UUID> publishedFor = new AtomicReference<>();
        AtomicReference<ComposedPlan> published = new AtomicReference<>();
        PublishSkeletonNode node = new PublishSkeletonNode(sink((generationId, plan) -> {
            publishedFor.set(generationId);
            published.set(plan);
        }));

        Map<String, Object> update = node.apply(stateWith(expectedGenerationId, "de"));

        assertThat(publishedFor.get()).isEqualTo(expectedGenerationId);
        assertThat(published.get().packages().get(0).title()).isEqualTo("Warm-up");
        assertThat(published.get().packages().get(0).days().get(0).title()).isEqualTo("Tag 1");
        ComposedPlan inState = JsonCodec.read((String) update.get(PlannerState.RESULT), ComposedPlan.class);
        assertThat(inState).isEqualTo(published.get());
    }

    /** A failing sink costs the preview, never the plan: the state still gets its named packages. */
    @Test
    void aFailingSink_isSwallowed_andTheStateStillGetsThePlaceholders() {
        PublishSkeletonNode node = new PublishSkeletonNode(sink((generationId, plan) -> {
            throw new IllegalStateException("database down");
        }));

        Map<String, Object> update = node.apply(stateWith(UUID.randomUUID(), "en"));

        ComposedPlan inState = JsonCodec.read((String) update.get(PlannerState.RESULT), ComposedPlan.class);
        assertThat(inState.packages().get(0).title()).isEqualTo("Warm-up");
    }

    @Test
    void withoutAGenerationId_publishesNothing_butStillNamesThePackages() {
        AtomicReference<ComposedPlan> published = new AtomicReference<>();
        PublishSkeletonNode node = new PublishSkeletonNode(sink((generationId, plan) -> published.set(plan)));

        Map<String, Object> update = node.apply(stateWith(null, "en"));

        assertThat(published.get()).isNull();
        assertThat(update).containsKey(PlannerState.RESULT);
    }

    @Test
    void withoutAResult_doesNothing() {
        PublishSkeletonNode node = new PublishSkeletonNode(sink((generationId, plan) -> {
            throw new AssertionError("must not publish");
        }));

        assertThat(node.apply(new PlannerState(Map.of()))).isEmpty();
    }

    private interface SkeletonSink {
        void skeleton(UUID generationId, ComposedPlan plan);
    }

    /** Only the skeleton half of the sink matters here; storing a finished plan is another node's business. */
    private static PersistResultNode.GenerationResultSink sink(SkeletonSink onSkeleton) {
        return new PersistResultNode.GenerationResultSink() {
            @Override
            public boolean ready(UUID generationId, ComposedPlan plan, boolean degraded,
                                 LlmUsage usage, int attempt) {
                throw new AssertionError("publishSkeleton must never store a finished plan");
            }

            @Override
            public void skeleton(UUID generationId, ComposedPlan plan) {
                onSkeleton.skeleton(generationId, plan);
            }
        };
    }
}
