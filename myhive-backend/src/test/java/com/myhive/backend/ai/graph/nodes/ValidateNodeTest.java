package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.plan.PlanValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ValidateNodeTest {

    private final ValidateNode node = new ValidateNode(new PlanValidator(), new PlanAssembler());

    private static PlannerState stateWith(PlanDraft draft, int attempt) {
        Map<String, Object> values = new HashMap<>();
        values.put(PlannerState.BRIEF, JsonCodec.write(new Brief(2, 8, List.of(), null, null, null, null, null, null)));
        values.put(PlannerState.CATALOG, "[]");
        values.put(PlannerState.DRAFT, JsonCodec.write(draft));
        values.put(PlannerState.ATTEMPT, attempt);
        return new PlannerState(values);
    }

    @Test
    void rejectedDraft_logsViolationCodesWithTierAndDay_butNoModelText(CapturedOutput output) {
        String modelWrittenTitle = "Legendary Lads Weekend";
        int expectedAttempt = 1;
        // Only one of the three tiers, with no days at all: MISSING_TIER x2 + WRONG_DAY_COUNT for BASIC.
        PlanDraft draft = new PlanDraft(List.of(
                new PlanDraft.PackageDraft(Tier.BASIC, modelWrittenTitle, "tag", "desc", List.of())));

        Map<String, Object> update = node.apply(stateWith(draft, expectedAttempt));

        assertThat(update).doesNotContainKey(PlannerState.RESULT);
        String logged = output.getOut() + output.getErr();
        assertThat(logged).contains("planner draft rejected attempt=" + expectedAttempt);
        assertThat(logged).contains("MISSING_TIER/MEDIUM", "MISSING_TIER/PREMIUM", "WRONG_DAY_COUNT/BASIC");
        assertThat(logged).doesNotContain(modelWrittenTitle);
    }
}
