package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.FakeLlmGateway;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.llm.PackageTexts;
import com.myhive.backend.ai.llm.PlanTextsResult;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanTextWriter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WriteTextsNodeTest {

    private static final ComposedPlan PLAN = new ComposedPlan(List.of(new ComposedPlan.PackageResult(Tier.BASIC,
            "Warm-up", null, null, new BigDecimal("40.00"), new BigDecimal("160.00"), ComposedPlan.CURRENCY, 90,
            List.of(), List.of(new ComposedPlan.DayResult(1, "Day 1", null, List.of())))), false);

    private final FakeLlmGateway llm = new FakeLlmGateway();

    private static PlannerState state(LlmUsage plannerUsage) {
        Map<String, Object> values = new HashMap<>();
        values.put(PlannerState.RESULT, JsonCodec.write(PLAN));
        values.put(PlannerState.LOCALE, "en");
        values.put(PlannerState.DESTINATION_NAME, "Prague");
        values.put(PlannerState.BRIEF, JsonCodec.write(new Brief(1, 4, List.of(), null, null, null,
                DayEdge.AFTERNOON, DayEdge.EVENING, null)));
        values.put(PlannerState.USAGE, JsonCodec.write(plannerUsage));
        return new PlannerState(values);
    }

    @Test
    void writesTheCopyIntoTheResult_andAddsTheCallToTheUsage() {
        String expectedTitle = "Beer, Bikes and Bad Decisions";
        int expectedPromptTokens = 100 + 7;
        int expectedCompletionTokens = 200 + 9;
        long expectedLatency = 1500L + 4L;
        llm.queueTexts(new PlanTextsResult(Map.of(Tier.BASIC, new PackageTexts(expectedTitle, null, null,
                Map.of(), Map.of(), Map.of())), new LlmUsage("fake-chat", 7, 9, 4L)));
        WriteTextsNode node = new WriteTextsNode(new PlanTextWriter(llm));

        Map<String, Object> update = node.apply(state(new LlmUsage("planner", 100, 200, 1500L)));

        ComposedPlan written = JsonCodec.read((String) update.get(PlannerState.RESULT), ComposedPlan.class);
        assertThat(written.packages().get(0).title()).isEqualTo(expectedTitle);
        LlmUsage usage = JsonCodec.read((String) update.get(PlannerState.USAGE), LlmUsage.class);
        assertThat(usage.model()).isEqualTo("planner");
        assertThat(usage.promptTokens()).isEqualTo(expectedPromptTokens);
        assertThat(usage.completionTokens()).isEqualTo(expectedCompletionTokens);
        assertThat(usage.latencyMs()).isEqualTo(expectedLatency);
    }

    @Test
    void aFailedCall_keepsTheResultAsItWas_andTheUsageAsItWas() {
        LlmUsage expectedUsage = new LlmUsage("planner", 100, 200, 1500L);
        llm.failNextTexts(new IllegalStateException("model down"));
        WriteTextsNode node = new WriteTextsNode(new PlanTextWriter(llm));

        Map<String, Object> update = node.apply(state(expectedUsage));

        ComposedPlan written = JsonCodec.read((String) update.get(PlannerState.RESULT), ComposedPlan.class);
        assertThat(written).isEqualTo(PLAN);
        assertThat(JsonCodec.read((String) update.get(PlannerState.USAGE), LlmUsage.class)).isEqualTo(expectedUsage);
    }

    /** langgraph4j checkpoints after a node returns: a node that throws re-enters on every later turn. */
    @Test
    void aWriterBug_isSwallowed_andLeavesTheStateAlone() {
        PlanTextWriter broken = mock(PlanTextWriter.class);
        when(broken.write(any(), any(), any(), any(), any())).thenThrow(new NullPointerException("bug"));
        WriteTextsNode node = new WriteTextsNode(broken);

        assertThat(node.apply(state(LlmUsage.none()))).isEmpty();
    }

    @Test
    void withoutAResult_doesNothing() {
        WriteTextsNode node = new WriteTextsNode(new PlanTextWriter(llm));

        assertThat(node.apply(new PlannerState(Map.of()))).isEmpty();
        assertThat(llm.textsRequests).isEmpty();
    }
}
