package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.AttemptDiagnostic;
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


    /** VIOLATIONS is overwritten per attempt; the log keeps both, with the failed call's error beside its draft. */
    @Test
    void rejectedDrafts_accumulateInTheAttemptLog_withTheCallErrorOfAFailedAttempt() {
        String expectedErrorCode = "LLM_TIMEOUT";
        PlannerState first = stateWith(new PlanDraft(List.of()), 0);
        Map<String, Object> firstUpdate = node.apply(withValue(first, PlannerState.LAST_ERROR, expectedErrorCode));
        Map<String, Object> carried = new HashMap<>(first.data());
        carried.putAll(firstUpdate);
        carried.put(PlannerState.ATTEMPT, 1);
        carried.put(PlannerState.LAST_ERROR, "");

        Map<String, Object> secondUpdate = node.apply(new PlannerState(carried));

        List<AttemptDiagnostic> log = new PlannerState(Map.of(PlannerState.ATTEMPT_LOG,
                secondUpdate.get(PlannerState.ATTEMPT_LOG))).attemptLog();
        assertThat(log).extracting(AttemptDiagnostic::attempt).containsExactly(0, 1);
        assertThat(log.get(0).errorCode()).isEqualTo(expectedErrorCode);
        assertThat(log.get(1).errorCode()).isNull();
        assertThat(log.get(1).violations()).anyMatch(v -> v.startsWith("MISSING_TIER BASIC"));
    }

    private static PlannerState withValue(PlannerState state, String key, Object value) {
        Map<String, Object> values = new HashMap<>(state.data());
        values.put(key, value);
        return new PlannerState(values);
    }
}
