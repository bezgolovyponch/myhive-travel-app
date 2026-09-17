package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.plan.PlanValidator;
import com.myhive.backend.ai.plan.ViolationCode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;

class PackageEditorTest {

    private static final int TRAVELERS = 4;

    private final PlanValidator validator = new PlanValidator();
    private final PlanAssembler assembler = new PlanAssembler();
    private final PackageEditor editor = new PackageEditor(validator, assembler);
    private final Brief brief = new Brief(2, TRAVELERS, List.of(), "stag weekend", null, null,
            DayEdge.MORNING, DayEdge.EVENING, null);

    private final List<CatalogActivity> catalog = new ArrayList<>();
    private final CatalogActivity beerSpa = activity("Beer Spa", "40.00", 90, null, "wellness");
    private final CatalogActivity beerBike = activity("Beer Bike", "45.00", 120, null, "sightseeing");
    private final CatalogActivity riverCruise = activity("River Cruise", "25.00", 60, null, "sightseeing");
    private final CatalogActivity nightClub = activity("Night Club", "35.00", 150, "500.00", "nightlife");
    private final CatalogActivity shootingRange = activity("Shooting Range", "60.00", 300, null, "adrenaline");
    private final CatalogActivity escapeRoom = activity("Escape Room", "20.00", 75, null, "indoor");

    @Test
    void remove_dropsTheActivityFromEveryPackageThatHasIt_andRepricesThem() {
        String expectedRemoved = beerBike.name();
        BigDecimal expectedMediumTotal = new BigDecimal("260.00");
        ComposedPlan plan = basePlan();

        EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(remove(expectedRemoved, null)));

        assertThat(outcome.anyApplied()).isTrue();
        assertThat(outcome.rejected()).isEmpty();
        assertThat(outcome.applied()).extracting(AppliedEdit::packageKey).containsExactly(Tier.MEDIUM, Tier.PREMIUM);
        assertThat(outcome.applied()).allSatisfy(applied -> {
            assertThat(applied.op()).isEqualTo(EditOp.REMOVE);
            assertThat(applied.activityName()).isEqualTo(expectedRemoved);
            assertThat(applied.replacementName()).isNull();
            assertThat(applied.placedActivityId()).isNull();
            assertThat(applied.dayNumber()).isEqualTo(1);
            assertThat(applied.slot()).isEqualTo(Slot.AFTERNOON);
        });
        assertThat(namesIn(outcome.plan(), Tier.MEDIUM)).doesNotContain(expectedRemoved);
        assertThat(namesIn(outcome.plan(), Tier.PREMIUM)).doesNotContain(expectedRemoved);
        assertThat(namesIn(outcome.plan(), Tier.BASIC)).isEqualTo(namesIn(plan, Tier.BASIC));
        assertThat(packageOf(outcome.plan(), Tier.MEDIUM).totalPrice()).isEqualByComparingTo(expectedMediumTotal);
        for (Tier tier : Tier.values()) {
            ComposedPlan.PackageResult result = packageOf(outcome.plan(), tier);
            assertThat(result.totalPrice()).isEqualByComparingTo(sumOfLineTotals(result));
        }
    }

    @Test
    void remove_lastItemOfAPackage_isWouldEmptyPackage() {
        String expectedRemaining = riverCruise.name();
        ComposedPlan plan = planOf(pkg(Tier.BASIC, day(1, item(Slot.AFTERNOON, riverCruise)), day(2)),
                mediumPackage(), premiumPackage());

        EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(remove(expectedRemaining, Tier.BASIC)));

        assertThat(outcome.anyApplied()).isFalse();
        assertThat(outcome.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.op()).isEqualTo(EditOp.REMOVE);
            assertThat(rejected.activityName()).isEqualTo(expectedRemaining);
            assertThat(rejected.packageKey()).isEqualTo(Tier.BASIC);
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.WOULD_EMPTY_PACKAGE);
        });
        assertThat(namesIn(outcome.plan(), Tier.BASIC)).containsExactly(expectedRemaining);
    }

    @Test
    void remove_absentActivity_isNotInPackage() {
        String expectedMissing = nightClub.name();
        ComposedPlan plan = basePlan();

        EditOutcome scoped = editor.apply(plan, brief, catalog, List.of(remove(expectedMissing, Tier.BASIC)));
        EditOutcome unscoped = editor.apply(plan, brief, catalog, List.of(remove(expectedMissing, null)));

        assertThat(scoped.anyApplied()).isFalse();
        assertThat(scoped.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.activityName()).isEqualTo(expectedMissing);
            assertThat(rejected.packageKey()).isEqualTo(Tier.BASIC);
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.NOT_IN_PACKAGE);
        });
        assertThat(unscoped.anyApplied()).isFalse();
        assertThat(unscoped.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.activityName()).isEqualTo(expectedMissing);
            assertThat(rejected.packageKey()).isNull();
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.NOT_IN_PACKAGE);
        });
    }

    @Test
    void anEditScopedToATierThePlanDoesNotHave_isNotInPackage() {
        String expectedAdded = nightClub.name();
        ComposedPlan plan = planOf(basicPackage(), mediumPackage());

        EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(add(expectedAdded, Tier.PREMIUM, null, null)));

        assertThat(outcome.anyApplied()).isFalse();
        assertThat(outcome.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.packageKey()).isEqualTo(Tier.PREMIUM);
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.NOT_IN_PACKAGE);
        });
        assertThat(outcome.plan().packages()).hasSize(2);
    }

    @Test
    void replace_rejectsAnAbsentOldActivity_orAReplacementAlreadyInThePackage() {
        String expectedAbsent = nightClub.name();
        String expectedPresent = beerSpa.name();
        ComposedPlan plan = basePlan();

        EditOutcome absentOld = editor.apply(plan, brief, catalog,
                List.of(replace(expectedAbsent, escapeRoom.name(), Tier.MEDIUM)));
        EditOutcome replacementPresent = editor.apply(plan, brief, catalog,
                List.of(replace(beerBike.name(), expectedPresent, Tier.MEDIUM)));

        assertThat(absentOld.anyApplied()).isFalse();
        assertThat(absentOld.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.activityName()).isEqualTo(expectedAbsent);
            assertThat(rejected.packageKey()).isEqualTo(Tier.MEDIUM);
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.NOT_IN_PACKAGE);
        });
        assertThat(replacementPresent.anyApplied()).isFalse();
        assertThat(replacementPresent.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.ALREADY_IN_PACKAGE);
            assertThat(rejected.detail()).contains(expectedPresent);
        });
        assertThat(namesIn(replacementPresent.plan(), Tier.MEDIUM)).isEqualTo(namesIn(plan, Tier.MEDIUM));
    }

    @Test
    void add_withoutScope_goesIntoEveryPackageAtTheFirstFittingCell_nightlifePrefersEvening() {
        String expectedAdded = nightClub.name();
        ComposedPlan plan = basePlan();

        EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(add(expectedAdded, null, null, null)));

        assertThat(outcome.rejected()).isEmpty();
        assertThat(outcome.applied()).hasSize(3);
        assertThat(outcome.applied()).allSatisfy(applied -> {
            assertThat(applied.op()).isEqualTo(EditOp.ADD);
            assertThat(applied.activityName()).isEqualTo(expectedAdded);
            assertThat(applied.replacementName()).isNull();
            assertThat(applied.placedActivityId()).isEqualTo(nightClub.id());
            assertThat(applied.slot()).isEqualTo(Slot.EVENING);
        });
        assertThat(outcome.applied()).extracting(AppliedEdit::packageKey, AppliedEdit::dayNumber)
                .containsExactly(tuple(Tier.BASIC, 1), tuple(Tier.MEDIUM, 1), tuple(Tier.PREMIUM, 2));
        for (Tier tier : Tier.values()) {
            assertThat(namesIn(outcome.plan(), tier)).contains(expectedAdded);
        }
        assertThat(slotsIn(outcome.plan(), Tier.BASIC, 1)).containsExactly(Slot.AFTERNOON, Slot.EVENING);
    }

    @Test
    void add_daytimeActivity_prefersMorning() {
        String expectedAdded = beerBike.name();
        ComposedPlan plan = basePlan();

        EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(add(expectedAdded, Tier.BASIC, null, null)));

        assertThat(outcome.rejected()).isEmpty();
        assertThat(outcome.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.packageKey()).isEqualTo(Tier.BASIC);
            assertThat(applied.dayNumber()).isEqualTo(1);
            assertThat(applied.slot()).isEqualTo(Slot.MORNING);
        });
        assertThat(namesIn(outcome.plan(), Tier.BASIC)).contains(expectedAdded);
        assertThat(slotsIn(outcome.plan(), Tier.BASIC, 1)).containsExactly(Slot.MORNING, Slot.AFTERNOON);
    }

    @Test
    void add_respectsTierItemAndMinuteCapsIncludingBuffer() {
        String expectedAdded = riverCruise.name();
        ComposedPlan dayOneAtItemCap = planOf(
                pkg(Tier.BASIC, day(1, item(Slot.MORNING, beerSpa), item(Slot.AFTERNOON, beerBike)), day(2)),
                mediumPackage(), premiumPackage());
        ComposedPlan noDayWithRoom = planOf(fullBasicPackage(), mediumPackage(), premiumPackage());

        EditOutcome spilledToNextDay = editor.apply(dayOneAtItemCap, brief, catalog,
                List.of(add(expectedAdded, Tier.BASIC, null, null)));
        EditOutcome nothingFits = editor.apply(noDayWithRoom, brief, catalog,
                List.of(add(expectedAdded, Tier.BASIC, null, null)));

        assertThat(spilledToNextDay.rejected()).isEmpty();
        assertThat(spilledToNextDay.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.dayNumber()).isEqualTo(2);
            assertThat(applied.slot()).isEqualTo(Slot.MORNING);
        });
        assertThat(nothingFits.anyApplied()).isFalse();
        assertThat(nothingFits.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.activityName()).isEqualTo(expectedAdded);
            assertThat(rejected.packageKey()).isEqualTo(Tier.BASIC);
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.NO_FREE_SLOT);
        });
        assertThat(namesIn(nothingFits.plan(), Tier.BASIC)).doesNotContain(expectedAdded);
    }

    @Test
    void add_withExplicitDayAndSlot_usesExactlyThatCell_orRejectsNoFreeSlot() {
        String expectedAdded = escapeRoom.name();
        ComposedPlan plan = basePlan();

        EditOutcome placed = editor.apply(plan, brief, catalog,
                List.of(add(expectedAdded, Tier.MEDIUM, 2, Slot.AFTERNOON)));
        EditOutcome takenCell = editor.apply(plan, brief, catalog,
                List.of(add(expectedAdded, Tier.MEDIUM, 1, Slot.MORNING)));
        EditOutcome outsideWindow = editor.apply(plan, brief, catalog,
                List.of(add(expectedAdded, Tier.MEDIUM, 2, Slot.NIGHT)));

        assertThat(placed.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.dayNumber()).isEqualTo(2);
            assertThat(applied.slot()).isEqualTo(Slot.AFTERNOON);
            assertThat(applied.placedActivityId()).isEqualTo(escapeRoom.id());
        });
        assertThat(takenCell.anyApplied()).isFalse();
        assertThat(takenCell.rejected()).singleElement()
                .extracting(RejectedEdit::reason).isEqualTo(EditRejectionReason.NO_FREE_SLOT);
        assertThat(outsideWindow.anyApplied()).isFalse();
        assertThat(outsideWindow.rejected()).singleElement()
                .extracting(RejectedEdit::reason).isEqualTo(EditRejectionReason.NO_FREE_SLOT);
    }

    @Test
    void add_alreadyPresent_isAlreadyInPackage() {
        String expectedPresent = beerBike.name();
        ComposedPlan plan = basePlan();

        EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(add(expectedPresent, Tier.MEDIUM, null, null)));

        assertThat(outcome.anyApplied()).isFalse();
        assertThat(outcome.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.op()).isEqualTo(EditOp.ADD);
            assertThat(rejected.activityName()).isEqualTo(expectedPresent);
            assertThat(rejected.packageKey()).isEqualTo(Tier.MEDIUM);
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.ALREADY_IN_PACKAGE);
        });
    }

    @Test
    void replace_putsTheReplacementIntoTheFreedCell() {
        String expectedOld = beerBike.name();
        String expectedNew = escapeRoom.name();
        ComposedPlan plan = basePlan();

        EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(replace(expectedOld, expectedNew, Tier.MEDIUM)));

        assertThat(outcome.rejected()).isEmpty();
        assertThat(outcome.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.op()).isEqualTo(EditOp.REPLACE);
            assertThat(applied.activityName()).isEqualTo(expectedOld);
            assertThat(applied.replacementName()).isEqualTo(expectedNew);
            assertThat(applied.packageKey()).isEqualTo(Tier.MEDIUM);
            assertThat(applied.dayNumber()).isEqualTo(1);
            assertThat(applied.slot()).isEqualTo(Slot.AFTERNOON);
            assertThat(applied.placedActivityId()).isEqualTo(escapeRoom.id());
        });
        assertThat(namesIn(outcome.plan(), Tier.MEDIUM)).contains(expectedNew).doesNotContain(expectedOld);
        assertThat(slotsIn(outcome.plan(), Tier.MEDIUM, 1)).containsExactly(Slot.MORNING, Slot.AFTERNOON);
    }

    @Test
    void replace_whenTheFreedCellCannotHoldIt_fallsBackToPlacement() {
        String expectedOld = riverCruise.name();
        String expectedNew = shootingRange.name();
        ComposedPlan plan = planOf(
                pkg(Tier.BASIC, day(1, item(Slot.MORNING, beerSpa), item(Slot.AFTERNOON, riverCruise)), day(2)),
                mediumPackage(), premiumPackage());

        EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(replace(expectedOld, expectedNew, Tier.BASIC)));

        assertThat(outcome.rejected()).isEmpty();
        assertThat(outcome.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.dayNumber()).isEqualTo(2);
            assertThat(applied.slot()).isEqualTo(Slot.MORNING);
            assertThat(applied.placedActivityId()).isEqualTo(shootingRange.id());
        });
        assertThat(namesIn(outcome.plan(), Tier.BASIC)).contains(expectedNew).doesNotContain(expectedOld);
    }

    @Test
    void replace_whenNothingFits_leavesThePackageUnchanged() {
        String expectedOld = riverCruise.name();
        String expectedNew = shootingRange.name();
        ComposedPlan plan = planOf(
                pkg(Tier.BASIC, day(1, item(Slot.MORNING, beerSpa), item(Slot.AFTERNOON, riverCruise)),
                        day(2, item(Slot.MORNING, escapeRoom), item(Slot.EVENING, nightClub))),
                mediumPackage(), premiumPackage());
        List<String> expectedNames = namesIn(plan, Tier.BASIC);

        EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(replace(expectedOld, expectedNew, Tier.BASIC)));

        assertThat(outcome.anyApplied()).isFalse();
        assertThat(outcome.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.op()).isEqualTo(EditOp.REPLACE);
            assertThat(rejected.packageKey()).isEqualTo(Tier.BASIC);
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.NO_FREE_SLOT);
        });
        assertThat(namesIn(outcome.plan(), Tier.BASIC)).isEqualTo(expectedNames);
        assertThat(namesIn(outcome.plan(), Tier.BASIC)).doesNotContain(expectedNew);
    }

    @Test
    void unknownAndAmbiguousNames_areRejectedBeforeTargeting() {
        String expectedUnknown = "Bungee Jumping";
        String expectedCandidates = beerBike.name() + ", " + beerSpa.name();
        ComposedPlan plan = basePlan();

        EditOutcome outcome = editor.apply(plan, brief, catalog,
                List.of(add(expectedUnknown, null, null, null), add("Beer", null, null, null)));

        assertThat(outcome.anyApplied()).isFalse();
        assertThat(outcome.rejected()).extracting(RejectedEdit::reason)
                .containsExactly(EditRejectionReason.UNKNOWN_ACTIVITY, EditRejectionReason.AMBIGUOUS_ACTIVITY);
        assertThat(outcome.rejected()).extracting(RejectedEdit::packageKey).containsOnlyNulls();
        assertThat(outcome.rejected().get(0).activityName()).isEqualTo(expectedUnknown);
        assertThat(outcome.rejected().get(0).detail()).isEqualTo(expectedUnknown);
        assertThat(outcome.rejected().get(1).detail()).isEqualTo(expectedCandidates);
        assertThat(outcome.plan()).isEqualTo(plan);
    }

    @Test
    void partialRejection_onePackageCannotTakeIt_theOthersStillApply() {
        String expectedAdded = nightClub.name();
        ComposedPlan plan = planOf(fullBasicPackage(), mediumPackage(), premiumPackage());

        EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(add(expectedAdded, null, null, null)));

        assertThat(outcome.applied()).extracting(AppliedEdit::packageKey).containsExactly(Tier.MEDIUM, Tier.PREMIUM);
        assertThat(outcome.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.packageKey()).isEqualTo(Tier.BASIC);
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.NO_FREE_SLOT);
        });
        assertThat(namesIn(outcome.plan(), Tier.BASIC)).doesNotContain(expectedAdded);
        assertThat(namesIn(outcome.plan(), Tier.MEDIUM)).contains(expectedAdded);
        assertThat(namesIn(outcome.plan(), Tier.PREMIUM)).contains(expectedAdded);
    }

    @Test
    void editsApplyInOrder_againstTheRunningResult() {
        String expectedTouched = escapeRoom.name();
        ComposedPlan plan = basePlan();

        EditOutcome outcome = editor.apply(plan, brief, catalog,
                List.of(add(expectedTouched, Tier.MEDIUM, null, null), remove(expectedTouched, Tier.MEDIUM)));

        assertThat(outcome.rejected()).isEmpty();
        assertThat(outcome.applied()).extracting(AppliedEdit::op).containsExactly(EditOp.ADD, EditOp.REMOVE);
        assertThat(outcome.applied()).extracting(AppliedEdit::slot).containsExactly(Slot.EVENING, Slot.EVENING);
        assertThat(namesIn(outcome.plan(), Tier.MEDIUM)).doesNotContain(expectedTouched);
        assertThat(namesIn(outcome.plan(), Tier.MEDIUM)).isEqualTo(namesIn(plan, Tier.MEDIUM));
    }

    @Test
    void groupMinimumFloor_isAppliedToAnAddedItem() {
        String expectedAdded = nightClub.name();
        BigDecimal expectedLineTotal = new BigDecimal("500.00");
        ComposedPlan plan = basePlan();

        EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(add(expectedAdded, Tier.BASIC, null, null)));

        assertThat(outcome.rejected()).isEmpty();
        ComposedPlan.ItemResult added = itemsIn(outcome.plan(), Tier.BASIC).stream()
                .filter(itemResult -> expectedAdded.equals(itemResult.name())).findFirst().orElseThrow();
        assertThat(added.lineTotal()).isEqualByComparingTo(expectedLineTotal);
        assertThat(added.groupMinApplied()).isTrue();
        assertThat(added.why()).isEmpty();
        assertThat(added.startHint()).isNull();
    }

    @Test
    void tierOrderAndDistinctness_areNotEnforced() {
        String expectedAdded = nightClub.name();
        ComposedPlan plan = basePlan();

        EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(add(expectedAdded, Tier.BASIC, null, null)));

        assertThat(outcome.rejected()).isEmpty();
        assertThat(outcome.applied()).singleElement().extracting(AppliedEdit::packageKey).isEqualTo(Tier.BASIC);
        assertThat(packageOf(outcome.plan(), Tier.BASIC).totalPrice())
                .isGreaterThan(packageOf(outcome.plan(), Tier.PREMIUM).totalPrice());
    }

    @Test
    void removingAMiddleDaysOnlyActivity_isWouldBreakSchedule() {
        String expectedKept = beerBike.name();
        Brief threeDayBrief = new Brief(3, TRAVELERS, List.of(), "stag weekend", null, null,
                DayEdge.MORNING, DayEdge.EVENING, null);
        ComposedPlan plan = planOf(threeDayBrief, pkg(Tier.BASIC, day(1, item(Slot.MORNING, beerSpa)),
                day(2, item(Slot.MORNING, beerBike)), day(3, item(Slot.MORNING, riverCruise))));

        EditOutcome outcome = editor.apply(plan, threeDayBrief, catalog, List.of(remove(expectedKept, Tier.BASIC)));

        assertThat(outcome.anyApplied()).isFalse();
        assertThat(outcome.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.packageKey()).isEqualTo(Tier.BASIC);
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.WOULD_BREAK_SCHEDULE);
            assertThat(rejected.detail()).startsWith(ViolationCode.EMPTY_DAY.name());
        });
        assertThat(namesIn(outcome.plan(), Tier.BASIC)).contains(expectedKept);
    }

    @Test
    void runtimeFailureInOneEdit_isInternal_andLaterEditsStillApply() {
        String expectedFailed = beerBike.name();
        String expectedApplied = escapeRoom.name();
        String expectedDetail = IllegalStateException.class.getSimpleName();
        PlanValidator throwingOnce = Mockito.spy(new PlanValidator());
        Mockito.doThrow(new IllegalStateException("boom")).doCallRealMethod()
                .when(throwingOnce).validatePackage(any(), any(), any());
        PackageEditor editorWithFailingValidator = new PackageEditor(throwingOnce, assembler);
        ComposedPlan plan = basePlan();

        EditOutcome outcome = editorWithFailingValidator.apply(plan, brief, catalog,
                List.of(add(expectedFailed, Tier.BASIC, null, null), add(expectedApplied, Tier.MEDIUM, null, null)));

        assertThat(outcome.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.activityName()).isEqualTo(expectedFailed);
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.INTERNAL);
            assertThat(rejected.detail()).isEqualTo(expectedDetail);
        });
        assertThat(outcome.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.activityName()).isEqualTo(expectedApplied);
            assertThat(applied.packageKey()).isEqualTo(Tier.MEDIUM);
        });
        assertThat(namesIn(outcome.plan(), Tier.BASIC)).doesNotContain(expectedFailed);
        assertThat(namesIn(outcome.plan(), Tier.MEDIUM)).contains(expectedApplied);
    }

    private CatalogActivity activity(String name, String price, int durationMinutes, String minPrice, String category) {
        CatalogActivity created = new CatalogActivity(UUID.randomUUID(), name.toLowerCase(Locale.ROOT).replace(' ', '-'),
                name, "one line", durationMinutes, true, new BigDecimal(price),
                minPrice == null ? null : new BigDecimal(minPrice), "img", List.of(category));
        catalog.add(created);
        return created;
    }

    private static EditRequest add(String activity, Tier packageKey, Integer dayNumber, Slot slot) {
        return new EditRequest(EditOp.ADD, activity, null, packageKey, dayNumber, slot);
    }

    private static EditRequest remove(String activity, Tier packageKey) {
        return new EditRequest(EditOp.REMOVE, activity, null, packageKey, null, null);
    }

    private static EditRequest replace(String activity, String replacement, Tier packageKey) {
        return new EditRequest(EditOp.REPLACE, activity, replacement, packageKey, null, null);
    }

    private static PlanDraft.ItemDraft item(Slot slot, CatalogActivity activity) {
        return new PlanDraft.ItemDraft(slot, "10:00", activity.id(), "why");
    }

    private static PlanDraft.DayDraft day(int dayNumber, PlanDraft.ItemDraft... items) {
        return new PlanDraft.DayDraft(dayNumber, "Day " + dayNumber, "summary", List.of(items));
    }

    private static PlanDraft.PackageDraft pkg(Tier tier, PlanDraft.DayDraft... days) {
        return new PlanDraft.PackageDraft(tier, tier.name() + " title", "tagline", "description", List.of(days));
    }

    private PlanDraft.PackageDraft basicPackage() {
        return pkg(Tier.BASIC, day(1, item(Slot.AFTERNOON, riverCruise)), day(2, item(Slot.AFTERNOON, escapeRoom)));
    }

    /** Day 1 blocked by the minute cap (300 + 150 + 30 > 360), day 2 blocked by the two-item cap. */
    private PlanDraft.PackageDraft fullBasicPackage() {
        return pkg(Tier.BASIC, day(1, item(Slot.MORNING, shootingRange)),
                day(2, item(Slot.MORNING, beerSpa), item(Slot.AFTERNOON, escapeRoom)));
    }

    private PlanDraft.PackageDraft mediumPackage() {
        return pkg(Tier.MEDIUM, day(1, item(Slot.MORNING, beerSpa), item(Slot.AFTERNOON, beerBike)),
                day(2, item(Slot.MORNING, riverCruise)));
    }

    private PlanDraft.PackageDraft premiumPackage() {
        return pkg(Tier.PREMIUM, day(1, item(Slot.MORNING, shootingRange), item(Slot.AFTERNOON, beerBike)),
                day(2, item(Slot.MORNING, beerSpa)));
    }

    private ComposedPlan basePlan() {
        return planOf(basicPackage(), mediumPackage(), premiumPackage());
    }

    private ComposedPlan planOf(PlanDraft.PackageDraft... packages) {
        return planOf(brief, packages);
    }

    private ComposedPlan planOf(Brief planBrief, PlanDraft.PackageDraft... packages) {
        return assembler.assemble(new PlanDraft(List.of(packages)), planBrief, byId(), false).plan();
    }

    private Map<UUID, CatalogActivity> byId() {
        Map<UUID, CatalogActivity> index = new LinkedHashMap<>();
        for (CatalogActivity entry : catalog) {
            index.put(entry.id(), entry);
        }
        return index;
    }

    private static ComposedPlan.PackageResult packageOf(ComposedPlan plan, Tier tier) {
        return plan.packages().stream().filter(result -> result.key() == tier).findFirst().orElseThrow();
    }

    private static List<ComposedPlan.ItemResult> itemsIn(ComposedPlan plan, Tier tier) {
        return packageOf(plan, tier).days().stream().flatMap(dayResult -> dayResult.items().stream()).toList();
    }

    private static List<String> namesIn(ComposedPlan plan, Tier tier) {
        return itemsIn(plan, tier).stream().map(ComposedPlan.ItemResult::name).toList();
    }

    private static List<Slot> slotsIn(ComposedPlan plan, Tier tier, int dayNumber) {
        return packageOf(plan, tier).days().stream().filter(dayResult -> dayResult.dayNumber() == dayNumber)
                .flatMap(dayResult -> dayResult.items().stream()).map(ComposedPlan.ItemResult::slot).toList();
    }

    private static BigDecimal sumOfLineTotals(ComposedPlan.PackageResult result) {
        return result.days().stream().flatMap(dayResult -> dayResult.items().stream())
                .map(ComposedPlan.ItemResult::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
