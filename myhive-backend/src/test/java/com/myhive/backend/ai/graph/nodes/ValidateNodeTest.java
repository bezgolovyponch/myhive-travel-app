package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.AttemptDiagnostic;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.plan.PlanTrimmer;
import com.myhive.backend.ai.plan.PlanValidator;
import com.myhive.backend.ai.plan.Violation;
import com.myhive.backend.ai.plan.ViolationCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ValidateNodeTest {

    private final PlanValidator validator = new PlanValidator();
    private final PlanAssembler assembler = new PlanAssembler();
    private final ValidateNode node = new ValidateNode(validator, assembler, new PlanTrimmer(validator, assembler));

    private final List<CatalogActivity> catalog = new ArrayList<>();
    private final CatalogActivity tasting = activity("Beer Tasting", 120, "30.00");
    private final CatalogActivity karting = activity("Karting", 120, "50.00");
    private final CatalogActivity tank = activity("Army Tank", 240, "120.00");
    private final CatalogActivity shooting = activity("Shooting Range", 180, "90.00");
    private final CatalogActivity cabaret = activity("Cabaret Night", 180, "60.00");

    /** One day, in the morning and out in the evening: MORNING, AFTERNOON and EVENING are open. */
    private final Brief dayTrip = new Brief(1, 8, List.of(), null, null, null, DayEdge.MORNING, DayEdge.EVENING, null);

    private static PlannerState stateWith(PlanDraft draft, int attempt) {
        return stateWith(draft, attempt, new Brief(2, 8, List.of(), null, null, null, null, null, null), List.of());
    }

    private static PlannerState stateWith(PlanDraft draft, int attempt, Brief brief, List<CatalogActivity> catalog) {
        Map<String, Object> values = new HashMap<>();
        values.put(PlannerState.BRIEF, JsonCodec.write(brief));
        values.put(PlannerState.CATALOG, JsonCodec.write(catalog));
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

        List<AttemptDiagnostic> log = attemptLogOf(secondUpdate);
        assertThat(log).extracting(AttemptDiagnostic::attempt).containsExactly(0, 1);
        assertThat(log.get(0).errorCode()).isEqualTo(expectedErrorCode);
        assertThat(log.get(1).errorCode()).isNull();
        assertThat(log.get(1).violations()).anyMatch(v -> v.startsWith("MISSING_TIER BASIC"));
        assertThat(log).allSatisfy(attempt -> assertThat(attempt.fixes()).isEmpty());
    }

    /** What every live run did: PREMIUM over its minutes. One activity goes and the plan is still the model's. */
    @Test
    void aDayOverItsCap_isCorrectedHere_andTheDraftBecomesTheResult(CapturedOutput output) {
        PlanDraft draft = draftWith(pkg(Tier.MEDIUM, item(Slot.MORNING, karting)));

        Map<String, Object> update = node.apply(stateWith(draft, 0, dayTrip, catalog));

        PlannerState after = new PlannerState(update);
        ComposedPlan plan = after.result().orElseThrow();
        assertThat(plan.degraded()).isFalse();
        assertThat(namesOf(plan, Tier.PREMIUM)).containsExactly(tank.name(), cabaret.name());
        assertThat(namesOf(plan, Tier.MEDIUM)).containsExactly(karting.name());
        assertThat(after.violations()).isEmpty();
        assertThat(validator.validate(after.draft().orElseThrow(), dayTrip, byId())).isEmpty();
        AttemptDiagnostic attempt = attemptLogOf(update).get(0);
        assertThat(attempt.violations()).singleElement().asString().startsWith("DAY_OVER_MINUTES PREMIUM d1");
        assertThat(attempt.fixes()).singleElement().asString()
                .startsWith("dropped " + shooting.name() + " from PREMIUM day 1");
        String logged = output.getOut() + output.getErr();
        assertThat(logged).contains("planner draft corrected attempt=0 violations=DAY_OVER_MINUTES/PREMIUM/d1"
                + " fixes=[dropped " + shooting.name() + " from PREMIUM day 1");
        assertThat(logged).doesNotContain("planner draft rejected");
    }

    /** MEDIUM offers nothing BASIC lacks, which no trim can mend: the repair gets the draft as the model wrote it. */
    @Test
    void aDraftThatStillFailsOnceCorrected_isRejectedAsWritten_withEveryViolation(CapturedOutput output) {
        PlanDraft draft = draftWith(pkg(Tier.MEDIUM, item(Slot.MORNING, tasting)));

        Map<String, Object> update = node.apply(stateWith(draft, 0, dayTrip, catalog));

        assertThat(update).doesNotContainKeys(PlannerState.RESULT, PlannerState.DRAFT);
        assertThat(output.getOut() + output.getErr())
                .contains("planner draft corrected but still failing attempt=0 violations=TIER_NOT_DISTINCT/");
        assertThat(new PlannerState(update).violations()).extracting(Violation::code)
                .contains(ViolationCode.DAY_OVER_MINUTES, ViolationCode.TIER_NOT_DISTINCT);
        assertThat(attemptLogOf(update)).singleElement()
                .satisfies(attempt -> assertThat(attempt.fixes()).isEmpty());
    }

    /** A node must never throw: a correction that blows up leaves the draft rejected, as if there were none. */
    @Test
    void aCorrectionThatFails_leavesTheDraftRejected() {
        PlanTrimmer failing = mock(PlanTrimmer.class);
        when(failing.trim(any(), any(), any())).thenThrow(new IllegalStateException("boom"));
        ValidateNode guarded = new ValidateNode(validator, assembler, failing);
        PlanDraft draft = draftWith(pkg(Tier.MEDIUM, item(Slot.MORNING, karting)));

        Map<String, Object> update = guarded.apply(stateWith(draft, 0, dayTrip, catalog));

        assertThat(update).doesNotContainKey(PlannerState.RESULT);
        assertThat(new PlannerState(update).violations()).extracting(Violation::code)
                .containsExactly(ViolationCode.DAY_OVER_MINUTES);
    }

    /** BASIC and PREMIUM are fixed; PREMIUM runs 660 minutes against a cap of 540. */
    private PlanDraft draftWith(PlanDraft.PackageDraft medium) {
        return new PlanDraft(List.of(pkg(Tier.BASIC, item(Slot.MORNING, tasting)), medium,
                pkg(Tier.PREMIUM, item(Slot.MORNING, tank), item(Slot.AFTERNOON, shooting),
                        item(Slot.EVENING, cabaret))));
    }

    private CatalogActivity activity(String name, int minutes, String price) {
        CatalogActivity activity = new CatalogActivity(UUID.randomUUID(), name.toLowerCase().replace(' ', '-'), name,
                "line", minutes, true, new BigDecimal(price), null, null, List.of());
        catalog.add(activity);
        return activity;
    }

    private Map<UUID, CatalogActivity> byId() {
        Map<UUID, CatalogActivity> byId = new HashMap<>();
        catalog.forEach(activity -> byId.put(activity.id(), activity));
        return byId;
    }

    private static PlanDraft.ItemDraft item(Slot slot, CatalogActivity activity) {
        return new PlanDraft.ItemDraft(slot, null, activity.id(), null);
    }

    private static PlanDraft.PackageDraft pkg(Tier tier, PlanDraft.ItemDraft... items) {
        return new PlanDraft.PackageDraft(tier, null, null, null,
                List.of(new PlanDraft.DayDraft(1, null, null, List.of(items))));
    }

    private static List<String> namesOf(ComposedPlan plan, Tier tier) {
        return plan.packages().stream()
                .filter(p -> p.key() == tier)
                .flatMap(p -> p.days().stream())
                .flatMap(day -> day.items().stream())
                .map(ComposedPlan.ItemResult::name)
                .toList();
    }

    private static List<AttemptDiagnostic> attemptLogOf(Map<String, Object> update) {
        return new PlannerState(Map.of(PlannerState.ATTEMPT_LOG, update.get(PlannerState.ATTEMPT_LOG))).attemptLog();
    }

    private static PlannerState withValue(PlannerState state, String key, Object value) {
        Map<String, Object> values = new HashMap<>(state.data());
        values.put(key, value);
        return new PlannerState(values);
    }
}
