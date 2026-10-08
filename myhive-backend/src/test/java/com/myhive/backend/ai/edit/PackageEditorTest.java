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

    /**
     * A tap on a card names the row by id: two catalog rows with one name (prod has pairs whose slugs were
     * suffixed on collision) are ambiguous by name, never by id.
     */
    @Test
    void add_carryingTheActivityId_placesThatRow_whereTheNameAloneIsAmbiguous() {
        CatalogActivity laserTag = activity("Laser Tag", "20.00", 60, null, "indoor");
        CatalogActivity laserTagTwo = activity("Laser Tag", "25.00", 60, null, "indoor");
        ComposedPlan plan = basePlan();
        EditRequest byName = add(laserTag.name(), Tier.BASIC, null, null);
        EditRequest byId = new EditRequest(EditOp.ADD, laserTagTwo.name(), null, Tier.BASIC, null, null, List.of(),
                laserTagTwo.id());

        EditOutcome named = editor.apply(plan, brief, catalog, List.of(byName));
        EditOutcome tapped = editor.apply(plan, brief, catalog, List.of(byId));

        assertThat(named.anyApplied()).isFalse();
        assertThat(named.rejected()).singleElement().satisfies(rejected ->
                assertThat(rejected.reason()).isEqualTo(EditRejectionReason.AMBIGUOUS_ACTIVITY));
        assertThat(tapped.rejected()).isEmpty();
        assertThat(tapped.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.placedActivityId()).isEqualTo(laserTagTwo.id());
            assertThat(applied.packageKey()).isEqualTo(Tier.BASIC);
        });
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

    /**
     * What the organizer adds is held to the roomiest day any tier has (4 activities, 540 minutes with
     * the buffers), not to the tier's own: a Basic day of two takes a third, a day that would run past
     * 540 minutes sends the activity on to the next, and with no day left it is refused.
     */
    @Test
    void add_prefersADayWithRoom_andIsNeverRefused() {
        String expectedAdded = riverCruise.name();
        ComposedPlan basicDayOfTwo = planOf(
                pkg(Tier.BASIC, day(1, item(Slot.MORNING, beerSpa), item(Slot.AFTERNOON, beerBike)), day(2)),
                mediumPackage(), premiumPackage());
        // 300 + 150 + one buffer is 480; a 60-minute cruise and its buffer would make 570.
        ComposedPlan dayOneOutOfMinutes = planOf(
                pkg(Tier.BASIC, day(1, item(Slot.MORNING, shootingRange), item(Slot.EVENING, nightClub)), day(2)),
                mediumPackage(), premiumPackage());
        // Day 2 ends in the evening: its three slots are taken.
        ComposedPlan noDayWithRoom = planOf(
                pkg(Tier.BASIC, day(1, item(Slot.MORNING, shootingRange), item(Slot.EVENING, nightClub)),
                        day(2, item(Slot.MORNING, beerSpa), item(Slot.AFTERNOON, escapeRoom),
                                item(Slot.EVENING, beerBike))),
                mediumPackage(), premiumPackage());

        EditOutcome thirdOnABasicDay = editor.apply(basicDayOfTwo, brief, catalog,
                List.of(add(expectedAdded, Tier.BASIC, null, null)));
        EditOutcome spilledToNextDay = editor.apply(dayOneOutOfMinutes, brief, catalog,
                List.of(add(expectedAdded, Tier.BASIC, null, null)));
        EditOutcome nothingFits = editor.apply(noDayWithRoom, brief, catalog,
                List.of(add(expectedAdded, Tier.BASIC, null, null)));

        assertThat(thirdOnABasicDay.rejected()).isEmpty();
        assertThat(thirdOnABasicDay.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.dayNumber()).isEqualTo(1);
            assertThat(applied.slot()).isEqualTo(Slot.EVENING);
        });
        assertThat(spilledToNextDay.rejected()).isEmpty();
        assertThat(spilledToNextDay.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.dayNumber()).isEqualTo(2);
            assertThat(applied.slot()).isEqualTo(Slot.MORNING);
        });
        // No day has a free slot: it goes on the lighter day all the same, sharing a slot.
        assertThat(nothingFits.rejected()).isEmpty();
        assertThat(nothingFits.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.activityName()).isEqualTo(expectedAdded);
            assertThat(applied.packageKey()).isEqualTo(Tier.BASIC);
        });
        assertThat(namesIn(nothingFits.plan(), Tier.BASIC)).contains(expectedAdded);
    }

    @Test
    void add_withExplicitDayAndSlot_usesThatCell_andStillGoesInWhenTheCellIsNotFree() {
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
        // A cell that is taken or outside the day's hours is no reason to refuse: it goes in elsewhere.
        assertThat(takenCell.rejected()).isEmpty();
        assertThat(namesIn(takenCell.plan(), Tier.MEDIUM)).contains(expectedAdded);
        assertThat(outsideWindow.rejected()).isEmpty();
        assertThat(namesIn(outsideWindow.plan(), Tier.MEDIUM)).contains(expectedAdded);
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
        // In the cruise's own cell the range would make day 1 run 600 minutes: 90 + 300 + 150 and two buffers.
        ComposedPlan plan = planOf(
                pkg(Tier.BASIC, day(1, item(Slot.MORNING, beerSpa), item(Slot.AFTERNOON, riverCruise),
                        item(Slot.EVENING, nightClub)), day(2)),
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
    void replace_whenNoDayHasRoom_stillSwaps() {
        String expectedOld = riverCruise.name();
        String expectedNew = shootingRange.name();
        // Either day would run past 540 minutes with the 300-minute range in it.
        ComposedPlan plan = planOf(
                pkg(Tier.BASIC, day(1, item(Slot.MORNING, beerSpa), item(Slot.AFTERNOON, riverCruise),
                                item(Slot.EVENING, beerBike)),
                        day(2, item(Slot.MORNING, escapeRoom), item(Slot.EVENING, nightClub))),
                mediumPackage(), premiumPackage());
        List<String> expectedNames = namesIn(plan, Tier.BASIC);

        EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(replace(expectedOld, expectedNew, Tier.BASIC)));

        assertThat(outcome.rejected()).isEmpty();
        assertThat(outcome.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.op()).isEqualTo(EditOp.REPLACE);
            assertThat(applied.packageKey()).isEqualTo(Tier.BASIC);
        });
        assertThat(namesIn(outcome.plan(), Tier.BASIC)).hasSameSizeAs(expectedNames)
                .contains(expectedNew).doesNotContain(expectedOld);
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
        // The very object that came in: a batch that changed nothing has nothing to re-price, and a
        // round-trip through the assembler is exactly where a stale snapshot could drop items.
        assertThat(outcome.plan()).isSameAs(plan);
    }

    /**
     * On a {@code REPLACE} the replacement is resolved second, so when that is the unresolvable one the
     * rejection carries the <em>replacement's</em> spelling — the activity being replaced resolved fine
     * and naming it would send the group looking in the wrong place.
     */
    @Test
    void replace_whoseReplacementIsAmbiguous_reportsTheReplacementsSpelling() {
        String expectedSpelling = "Beer";
        String expectedCandidates = beerBike.name() + ", " + beerSpa.name();
        ComposedPlan plan = basePlan();

        EditOutcome outcome = editor.apply(plan, brief, catalog,
                List.of(replace(riverCruise.name(), expectedSpelling, Tier.BASIC)));

        assertThat(outcome.anyApplied()).isFalse();
        assertThat(outcome.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.op()).isEqualTo(EditOp.REPLACE);
            assertThat(rejected.activityName()).isEqualTo(expectedSpelling);
            assertThat(rejected.packageKey()).isNull();
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.AMBIGUOUS_ACTIVITY);
            assertThat(rejected.detail()).isEqualTo(expectedCandidates);
        });
    }

    /**
     * The snapshot is brief-dependent — sorted by category overlap and cut at 80 — so undoing back to a
     * generation built from a different brief routinely lands on a plan whose items the current snapshot
     * does not list. The assembler prices every package from that snapshot and skips what it cannot find,
     * so those items are rebuilt from the plan instead: refusing would have been a permanent "try again"
     * on a chat where trying again can never work, and re-assembling without them would have deleted
     * activities from packages the batch never named.
     */
    @Test
    void aSnapshotMissingPlanActivities_rebuildsThemFromThePlan_andStillApplies() {
        String expectedAdded = nightClub.name();
        String expectedSurvivor = beerSpa.name();
        ComposedPlan plan = basePlan();
        BigDecimal expectedPremiumTotal = packageOf(plan, Tier.PREMIUM).totalPrice();
        List<CatalogActivity> staleCatalog = catalog.stream()
                .filter(entry -> !entry.id().equals(beerSpa.id()))
                .toList();

        EditOutcome outcome = editor.apply(plan, brief, staleCatalog,
                List.of(add(expectedAdded, Tier.MEDIUM, null, null)));

        assertThat(outcome.rejected()).isEmpty();
        assertThat(outcome.applied()).singleElement()
                .satisfies(applied -> assertThat(applied.packageKey()).isEqualTo(Tier.MEDIUM));
        assertThat(namesIn(outcome.plan(), Tier.MEDIUM)).contains(expectedAdded);
        // PREMIUM was never targeted: its Beer Spa line is still there and still costs what it did
        assertThat(namesIn(outcome.plan(), Tier.PREMIUM)).contains(expectedSurvivor);
        assertThat(packageOf(outcome.plan(), Tier.PREMIUM).totalPrice()).isEqualByComparingTo(expectedPremiumTotal);
    }

    /**
     * A rebuilt entry is only a stand-in for what the plan holds: it has no categories to pick a slot by
     * and the activity may have left the catalog for good, so it can be dropped or swapped out by name
     * but never placed somewhere new.
     */
    @Test
    void anActivityTheSnapshotNoLongerLists_canStillBeRemoved_butNotAddedElsewhere() {
        String expectedGone = beerSpa.name();
        ComposedPlan plan = basePlan();
        List<CatalogActivity> staleCatalog = catalog.stream()
                .filter(entry -> !entry.id().equals(beerSpa.id()))
                .toList();

        EditOutcome removed = editor.apply(plan, brief, staleCatalog, List.of(remove(expectedGone, Tier.MEDIUM)));
        EditOutcome addedBack = editor.apply(plan, brief, staleCatalog,
                List.of(add(expectedGone, Tier.BASIC, null, null)));

        assertThat(removed.rejected()).isEmpty();
        assertThat(removed.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.op()).isEqualTo(EditOp.REMOVE);
            assertThat(applied.activityName()).isEqualTo(expectedGone);
        });
        assertThat(namesIn(removed.plan(), Tier.MEDIUM)).doesNotContain(expectedGone);
        assertThat(addedBack.anyApplied()).isFalse();
        assertThat(addedBack.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.op()).isEqualTo(EditOp.ADD);
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.UNKNOWN_ACTIVITY);
            assertThat(rejected.detail()).contains("no longer in the catalog");
        });
        assertThat(namesIn(addedBack.plan(), Tier.BASIC)).doesNotContain(expectedGone);
    }

    /**
     * The one case that still costs the batch: an item the snapshot does not list and the plan cannot
     * describe well enough to price. Re-assembling would drop it from packages nobody touched.
     */
    @Test
    void aPlanItemThatCannotBeRebuilt_refusesTheWholeBatch_andLeavesThePlanUntouched() {
        ComposedPlan plan = withoutPriceOn(basePlan(), beerSpa.id());
        List<CatalogActivity> staleCatalog = catalog.stream()
                .filter(entry -> !entry.id().equals(beerSpa.id()))
                .toList();

        EditOutcome outcome = editor.apply(plan, brief, staleCatalog,
                List.of(add(nightClub.name(), Tier.MEDIUM, null, null)));

        assertThat(outcome.anyApplied()).isFalse();
        assertThat(outcome.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.op()).isEqualTo(EditOp.ADD);
            assertThat(rejected.activityName()).isEqualTo(nightClub.name());
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.INTERNAL);
            assertThat(rejected.detail()).contains("catalog snapshot");
        });
        assertThat(outcome.plan()).isSameAs(plan);
    }

    /**
     * Degraded and fallback plans routinely ship with violations of their own (an empty middle day is the
     * common one), so rejecting on any violation present after the op left those packages permanently
     * un-editable — behind a {@code WOULD_BREAK_SCHEDULE} blaming the edit for a rule the plan already
     * broke. Only what the op introduces counts.
     */
    @Test
    void aPreExistingViolation_doesNotBlockAnEdit_butANewOneStillDoes() {
        String expectedAdded = escapeRoom.name();
        String expectedKept = beerBike.name();
        Brief fourDayBrief = new Brief(4, TRAVELERS, List.of(), "stag weekend", null, null,
                DayEdge.MORNING, DayEdge.EVENING, null);
        ComposedPlan degraded = planOf(fourDayBrief, pkg(Tier.BASIC, day(1, item(Slot.MORNING, beerSpa)), day(2),
                day(3, item(Slot.MORNING, beerBike)), day(4, item(Slot.MORNING, riverCruise))));

        EditOutcome added = editor.apply(degraded, fourDayBrief, catalog,
                List.of(add(expectedAdded, Tier.BASIC, 1, Slot.AFTERNOON)));
        EditOutcome emptiedAnotherDay = editor.apply(degraded, fourDayBrief, catalog,
                List.of(remove(expectedKept, Tier.BASIC)));

        assertThat(added.rejected()).isEmpty();
        assertThat(added.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.dayNumber()).isEqualTo(1);
            assertThat(applied.slot()).isEqualTo(Slot.AFTERNOON);
        });
        assertThat(namesIn(added.plan(), Tier.BASIC)).contains(expectedAdded);
        assertThat(emptiedAnotherDay.anyApplied()).isFalse();
        assertThat(emptiedAnotherDay.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.WOULD_BREAK_SCHEDULE);
            assertThat(rejected.detail()).startsWith(ViolationCode.EMPTY_DAY.name()).contains("day 3");
        });
        assertThat(namesIn(emptiedAnotherDay.plan(), Tier.BASIC)).contains(expectedKept);
    }

    @Test
    void add_toEveryPackage_landsInAFullOneToo() {
        String expectedAdded = nightClub.name();
        // Day 1 is 450 minutes long already and day 2 has its three slots taken: no room for a club night.
        ComposedPlan plan = planOf(
                pkg(Tier.BASIC, day(1, item(Slot.MORNING, shootingRange), item(Slot.AFTERNOON, beerBike)),
                        day(2, item(Slot.MORNING, beerSpa), item(Slot.AFTERNOON, escapeRoom),
                                item(Slot.EVENING, riverCruise))),
                mediumPackage(), premiumPackage());

        EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(add(expectedAdded, null, null, null)));

        assertThat(outcome.applied()).extracting(AppliedEdit::packageKey)
                .containsExactly(Tier.BASIC, Tier.MEDIUM, Tier.PREMIUM);
        assertThat(outcome.rejected()).isEmpty();
        assertThat(namesIn(outcome.plan(), Tier.BASIC)).contains(expectedAdded);
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

    /**
     * {@code validatePackage} returns on a wrong day count before it looks at a single day, so neither
     * the before- nor the after-list says anything about the day rules. Diffing them would wave every op
     * through unvalidated; the malformed package refuses them instead.
     */
    @Test
    void aPackageWithTheWrongDayCount_refusesTheEditRatherThanSkippingValidation() {
        String expectedAdded = escapeRoom.name();
        Brief threeDayBrief = new Brief(3, TRAVELERS, List.of(), "stag weekend", null, null,
                DayEdge.MORNING, DayEdge.EVENING, null);
        // two days of itinerary against a three-day brief: WRONG_DAY_COUNT before and after any edit
        ComposedPlan plan = planOf(threeDayBrief, pkg(Tier.BASIC, day(1, item(Slot.MORNING, beerSpa)),
                day(2, item(Slot.MORNING, riverCruise))));

        EditOutcome outcome = editor.apply(plan, threeDayBrief, catalog,
                List.of(add(expectedAdded, Tier.BASIC, null, null)));

        assertThat(outcome.anyApplied()).isFalse();
        assertThat(outcome.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.packageKey()).isEqualTo(Tier.BASIC);
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.WOULD_BREAK_SCHEDULE);
            assertThat(rejected.detail()).startsWith(ViolationCode.WRONG_DAY_COUNT.name());
        });
        assertThat(namesIn(outcome.plan(), Tier.BASIC)).doesNotContain(expectedAdded);
    }

    /** Strips the price off one activity's lines, leaving an item the editor cannot rebuild a catalog row from. */
    private static ComposedPlan withoutPriceOn(ComposedPlan plan, UUID activityId) {
        List<ComposedPlan.PackageResult> packages = new ArrayList<>();
        for (ComposedPlan.PackageResult p : plan.packages()) {
            List<ComposedPlan.DayResult> days = new ArrayList<>();
            for (ComposedPlan.DayResult dayResult : p.days()) {
                List<ComposedPlan.ItemResult> items = new ArrayList<>();
                for (ComposedPlan.ItemResult itemResult : dayResult.items()) {
                    items.add(activityId.equals(itemResult.activityId()) ? withoutPrice(itemResult) : itemResult);
                }
                days.add(new ComposedPlan.DayResult(dayResult.dayNumber(), dayResult.title(), dayResult.summary(),
                        items));
            }
            packages.add(new ComposedPlan.PackageResult(p.key(), p.title(), p.tagline(), p.description(),
                    p.pricePerPerson(), p.totalPrice(), p.currency(), p.totalDurationMinutes(), p.activityIds(), days));
        }
        return new ComposedPlan(packages, plan.degraded());
    }

    private static ComposedPlan.ItemResult withoutPrice(ComposedPlan.ItemResult item) {
        return new ComposedPlan.ItemResult(item.slot(), item.startHint(), item.activityId(), item.slug(), item.name(),
                item.imageUrl(), item.durationMinutes(), null, item.minPrice(), item.lineTotal(),
                item.groupMinApplied(), item.why());
    }

    private CatalogActivity activity(String name, String price, int durationMinutes, String minPrice, String category) {
        CatalogActivity created = new CatalogActivity(UUID.randomUUID(), name.toLowerCase(Locale.ROOT).replace(' ', '-'),
                name, "one line", durationMinutes, true, new BigDecimal(price),
                minPrice == null ? null : new BigDecimal(minPrice), "img", List.of(category));
        catalog.add(created);
        return created;
    }

    /**
     * The model names what it deems closest to a request the catalog lacks; only the names that really
     * resolve are offered back, in the catalog's spelling, so that "add <one of them>" resolves exactly.
     * An ambiguous one ("Beer") is no offer either: it would only answer with "which one?".
     */
    @Test
    void unknownName_offersTheAlternativesThatResolve_inCatalogSpelling() {
        String expectedUnknown = "strip shows";
        List<String> expectedAlternatives = List.of(nightClub.name(), beerBike.name());
        EditRequest add = new EditRequest(EditOp.ADD, expectedUnknown, null, null, null, null,
                List.of("night club", "Lap Dance Bar", "beer bike", "Beer"));

        EditOutcome outcome = editor.apply(basePlan(), brief, catalog, List.of(add));

        assertThat(outcome.anyApplied()).isFalse();
        assertThat(outcome.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.UNKNOWN_ACTIVITY);
            assertThat(rejected.activityName()).isEqualTo(expectedUnknown);
            assertThat(rejected.alternatives()).isEqualTo(expectedAlternatives);
        });
    }

    /** A name that resolves needs no alternatives, whatever the model attached to it. */
    @Test
    void knownName_ignoresAlternatives() {
        EditRequest remove = new EditRequest(EditOp.REMOVE, beerBike.name(), null, null, null, null,
                List.of(nightClub.name()));

        EditOutcome outcome = editor.apply(basePlan(), brief, catalog, List.of(remove));

        assertThat(outcome.rejected()).isEmpty();
        assertThat(outcome.applied()).isNotEmpty();
    }

    @Test
    void move_putsTheActivityOnTheOtherDay_evenWhenThatLeavesItsOwnDayFree() {
        String expectedMoved = riverCruise.name();
        // Medium: day 1 Beer Spa + Beer Bike, day 2 River Cruise alone.
        ComposedPlan plan = basePlan();

        EditOutcome outcome = editor.apply(plan, brief, catalog,
                List.of(new EditRequest(EditOp.MOVE, expectedMoved, null, Tier.MEDIUM, 1, null)));

        assertThat(outcome.rejected()).isEmpty();
        assertThat(outcome.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.op()).isEqualTo(EditOp.MOVE);
            assertThat(applied.dayNumber()).isEqualTo(1);
        });
        ComposedPlan.PackageResult medium = outcome.plan().packages().stream()
                .filter(pkg -> pkg.key() == Tier.MEDIUM).findFirst().orElseThrow();
        assertThat(medium.days().get(0).items()).extracting(ComposedPlan.ItemResult::name).contains(expectedMoved);
        assertThat(medium.days().get(1).items()).isEmpty();
        // Nothing was added or lost on the way.
        assertThat(namesIn(outcome.plan(), Tier.MEDIUM)).hasSameSizeAs(namesIn(plan, Tier.MEDIUM));
    }

    @Test
    void move_toADayTheTripDoesNotHave_isRefused_andSoIsMovingWhatIsNotThere() {
        ComposedPlan plan = basePlan();

        EditOutcome noSuchDay = editor.apply(plan, brief, catalog,
                List.of(new EditRequest(EditOp.MOVE, riverCruise.name(), null, Tier.MEDIUM, 9, null)));
        EditOutcome notInThePackage = editor.apply(plan, brief, catalog,
                List.of(new EditRequest(EditOp.MOVE, shootingRange.name(), null, Tier.MEDIUM, 1, null)));

        assertThat(noSuchDay.anyApplied()).isFalse();
        assertThat(noSuchDay.rejected()).singleElement()
                .extracting(RejectedEdit::reason).isEqualTo(EditRejectionReason.WOULD_BREAK_SCHEDULE);
        assertThat(notInThePackage.anyApplied()).isFalse();
        assertThat(namesIn(noSuchDay.plan(), Tier.MEDIUM)).isEqualTo(namesIn(plan, Tier.MEDIUM));
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
