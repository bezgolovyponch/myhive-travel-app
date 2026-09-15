package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.BudgetHint;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanValidatorTest {

    private final PlanValidator validator = new PlanValidator();
    private final Map<UUID, CatalogActivity> catalog = new HashMap<>();
    private final Brief brief = new Brief(2, 8, List.of("nightlife"), null, null, BudgetHint.MID,
            DayEdge.AFTERNOON, DayEdge.EVENING, null);

    private UUID activity(String name, int minutes) {
        UUID id = UUID.randomUUID();
        catalog.put(id, new CatalogActivity(id, name.toLowerCase(), name, "", minutes, true,
                new BigDecimal("40.00"), null, null, List.of("nightlife")));
        return id;
    }

    private static PlanDraft.ItemDraft item(Slot slot, UUID id) {
        return new PlanDraft.ItemDraft(slot, null, id, "fun");
    }

    private static PlanDraft.PackageDraft pkg(Tier tier, List<PlanDraft.DayDraft> days) {
        return new PlanDraft.PackageDraft(tier, "T", "tag", "desc", days);
    }

    private static PlanDraft.DayDraft day(int n, PlanDraft.ItemDraft... items) {
        return new PlanDraft.DayDraft(n, "Day", "sum", List.of(items));
    }

    /**
     * Three valid, distinct packages over two days. Each tier keeps at least one activity
     * that the other two tiers lack, so TIER_NOT_DISTINCT never fires on this fixture.
     */
    private PlanDraft validDraft() {
        UUID a = activity("A", 120);
        UUID b = activity("B", 120);
        UUID c = activity("C", 120);
        UUID d = activity("D", 60);
        UUID e = activity("E", 60);
        UUID f = activity("F", 60);
        return new PlanDraft(List.of(
                pkg(Tier.BASIC, List.of(day(1, item(Slot.EVENING, a)), day(2, item(Slot.MORNING, b)))),
                pkg(Tier.MEDIUM, List.of(day(1, item(Slot.EVENING, a)), day(2, item(Slot.MORNING, c)))),
                pkg(Tier.PREMIUM, List.of(day(1, item(Slot.AFTERNOON, d), item(Slot.EVENING, a)),
                        day(2, item(Slot.MORNING, e), item(Slot.AFTERNOON, f))))));
    }

    @Test
    void validDraft_hasNoViolations() {
        assertThat(validator.validate(validDraft(), brief, catalog)).isEmpty();
    }

    @Test
    void unknownActivity_isReported() {
        PlanDraft draft = validDraft();
        UUID ghost = UUID.randomUUID();
        List<PlanDraft.DayDraft> days = List.of(
                day(1, item(Slot.EVENING, ghost)), draft.packages().get(0).days().get(1));
        PlanDraft broken = new PlanDraft(List.of(pkg(Tier.BASIC, days), draft.packages().get(1), draft.packages().get(2)));

        assertThat(validator.validate(broken, brief, catalog))
                .extracting(Violation::code).contains(ViolationCode.UNKNOWN_ACTIVITY);
    }

    @Test
    void duplicateActivityInsidePackage_isReported() {
        UUID a = activity("A", 60);
        PlanDraft.PackageDraft p = pkg(Tier.BASIC, List.of(day(1, item(Slot.EVENING, a)), day(2, item(Slot.MORNING, a))));
        PlanDraft draft = new PlanDraft(List.of(p, validDraft().packages().get(1), validDraft().packages().get(2)));

        assertThat(validator.validate(draft, brief, catalog))
                .extracting(Violation::code).contains(ViolationCode.DUPLICATE_ACTIVITY);
    }

    @Test
    void dayOverMinutes_countsBufferBetweenItems() {
        int itemMinutes = 170;
        UUID a = activity("A", itemMinutes);
        UUID b = activity("B", itemMinutes);
        // BASIC caps at 360 minutes/day: 170 + 30 (buffer) + 170 = 370 > 360.
        PlanDraft.PackageDraft p = pkg(Tier.BASIC, List.of(
                day(1, item(Slot.AFTERNOON, a), item(Slot.EVENING, b)),
                day(2, item(Slot.MORNING, activity("C", 30)))));
        PlanDraft draft = new PlanDraft(List.of(p, validDraft().packages().get(1), validDraft().packages().get(2)));

        assertThat(validator.validate(draft, brief, catalog))
                .extracting(Violation::code).contains(ViolationCode.DAY_OVER_MINUTES);
    }

    /**
     * Every tier's minute cap is reachable, not just BASIC's: a two-item day just at
     * {@code tier.maxMinutesPerDay()} (including the buffer) is clean, and a few minutes
     * over that same cap reports DAY_OVER_MINUTES.
     */
    @ParameterizedTest
    @EnumSource(Tier.class)
    void dayOverMinutes_isReportedForEachTier(Tier tier) {
        int controlMinutesPerItem = (tier.maxMinutesPerDay() - PlanValidator.BUFFER_MINUTES) / 2;
        int overMinutesPerItem = controlMinutesPerItem + 5;

        PlanDraft.PackageDraft controlPackage = pkg(tier, List.of(
                day(1, item(Slot.AFTERNOON, activity("Control-A", controlMinutesPerItem)),
                        item(Slot.EVENING, activity("Control-B", controlMinutesPerItem))),
                day(brief.days())));
        assertThat(validator.validate(new PlanDraft(List.of(controlPackage)), brief, catalog))
                .extracting(Violation::code).doesNotContain(ViolationCode.DAY_OVER_MINUTES);

        PlanDraft.PackageDraft overPackage = pkg(tier, List.of(
                day(1, item(Slot.AFTERNOON, activity("Over-A", overMinutesPerItem)),
                        item(Slot.EVENING, activity("Over-B", overMinutesPerItem))),
                day(brief.days())));
        assertThat(validator.validate(new PlanDraft(List.of(overPackage)), brief, catalog))
                .extracting(Violation::code).contains(ViolationCode.DAY_OVER_MINUTES);
    }

    @Test
    void dayOverItems_perTier() {
        List<PlanDraft.ItemDraft> items = new ArrayList<>();
        items.add(item(Slot.MORNING, activity("A", 30)));
        items.add(item(Slot.AFTERNOON, activity("B", 30)));
        items.add(item(Slot.EVENING, activity("C", 30)));
        PlanDraft.DayDraft day2 = new PlanDraft.DayDraft(2, "d", "s", items);
        // Day 2 ends at EVENING per brief; three items in BASIC (max 2) is over the item cap.
        PlanDraft.PackageDraft p = pkg(Tier.BASIC, List.of(day(1, item(Slot.EVENING, activity("Z", 30))), day2));
        PlanDraft draft = new PlanDraft(List.of(p, validDraft().packages().get(1), validDraft().packages().get(2)));

        assertThat(validator.validate(draft, brief, catalog))
                .extracting(Violation::code).contains(ViolationCode.DAY_OVER_ITEMS);
    }

    /**
     * Every tier's item cap is reachable, not just BASIC's: a middle day (full MORNING..NIGHT
     * window) loaded with one more item than {@code tier.maxItemsPerDay()} reports DAY_OVER_ITEMS.
     * Slots are cycled from the 4-value {@link Slot} enum, so PREMIUM's 5th item reuses a slot
     * (an incidental SLOT_TAKEN alongside it is fine; this test only cares about the item cap).
     */
    @ParameterizedTest
    @EnumSource(Tier.class)
    void dayOverItems_isReportedForEachTier(Tier tier) {
        int overCapItemCount = tier.maxItemsPerDay() + 1;
        Slot[] slots = Slot.values();
        List<PlanDraft.ItemDraft> items = new ArrayList<>();
        for (int i = 0; i < overCapItemCount; i++) {
            items.add(item(slots[i % slots.length], activity("Item" + i, 10)));
        }
        PlanDraft.PackageDraft p = new PlanDraft.PackageDraft(tier, "T", "tag", "desc", List.of(
                new PlanDraft.DayDraft(1, "d", "s", List.of()),
                new PlanDraft.DayDraft(2, "d", "s", items),
                new PlanDraft.DayDraft(3, "d", "s", List.of())));
        Brief threeDayBrief = new Brief(3, 8, List.of("nightlife"), null, null, null, DayEdge.EVENING, DayEdge.MORNING, null);
        PlanDraft draft = new PlanDraft(List.of(p));

        assertThat(validator.validate(draft, threeDayBrief, catalog))
                .extracting(Violation::code).contains(ViolationCode.DAY_OVER_ITEMS);
    }

    @Test
    void slotOutsideArrivalOrDepartureWindow_isReported() {
        // Arrival AFTERNOON -> MORNING on day 1 is outside; departure EVENING -> NIGHT on day 2 is outside:
        // two items placed outside the window, so two violations are expected below.
        int outsideWindowItemCount = 2;
        PlanDraft.PackageDraft p = pkg(Tier.BASIC, List.of(
                day(1, item(Slot.MORNING, activity("A", 60))),
                day(2, item(Slot.NIGHT, activity("B", 60)))));
        PlanDraft draft = new PlanDraft(List.of(p, validDraft().packages().get(1), validDraft().packages().get(2)));

        List<Violation> violations = validator.validate(draft, brief, catalog);
        assertThat(violations).filteredOn(v -> v.code() == ViolationCode.SLOT_OUTSIDE_WINDOW)
                .hasSize(outsideWindowItemCount);
    }

    @Test
    void slotTakenTwice_isReported() {
        PlanDraft.PackageDraft p = pkg(Tier.BASIC, List.of(
                day(1, item(Slot.EVENING, activity("A", 30)), item(Slot.EVENING, activity("B", 30))),
                day(2, item(Slot.MORNING, activity("C", 30)))));
        PlanDraft draft = new PlanDraft(List.of(p, validDraft().packages().get(1), validDraft().packages().get(2)));

        assertThat(validator.validate(draft, brief, catalog))
                .extracting(Violation::code).contains(ViolationCode.SLOT_TAKEN);
    }

    @Test
    void emptyMiddleDay_isReported_butArrivalDayMayBeEmpty() {
        int expectedEmptyDayNumber = 2;
        Brief threeDays = new Brief(3, 8, List.of("nightlife"), null, null, null, DayEdge.EVENING, DayEdge.MORNING, null);
        PlanDraft.PackageDraft p = pkg(Tier.BASIC, List.of(
                new PlanDraft.DayDraft(1, "d", "s", List.of()),
                new PlanDraft.DayDraft(expectedEmptyDayNumber, "d", "s", List.of()),
                day(3, item(Slot.MORNING, activity("A", 30)))));
        PlanDraft draft = new PlanDraft(List.of(p, validDraft().packages().get(1), validDraft().packages().get(2)));

        List<Violation> violations = validator.validate(draft, threeDays, catalog);
        assertThat(violations).filteredOn(v -> v.code() == ViolationCode.EMPTY_DAY).hasSize(1);
        assertThat(violations).filteredOn(v -> v.code() == ViolationCode.EMPTY_DAY).first()
                .extracting(Violation::dayNumber).isEqualTo(expectedEmptyDayNumber);
    }

    /** Mirrors the arrival-day case above: the last (departure) day may also be empty. */
    @Test
    void departureDayMayBeEmpty_isNotReported() {
        PlanDraft.PackageDraft p = pkg(Tier.BASIC, List.of(
                day(1, item(Slot.EVENING, activity("A", 30))),
                day(brief.days())));
        PlanDraft draft = new PlanDraft(List.of(p));

        List<Violation> violations = validator.validate(draft, brief, catalog);
        assertThat(violations).filteredOn(v -> v.code() == ViolationCode.EMPTY_DAY).isEmpty();
    }

    @Test
    void wrongDayCountOrMissingTier_isReported() {
        PlanDraft draft = validDraft();
        PlanDraft twoPackages = new PlanDraft(List.of(draft.packages().get(0), draft.packages().get(1)));
        assertThat(validator.validate(twoPackages, brief, catalog))
                .extracting(Violation::code).contains(ViolationCode.MISSING_TIER);

        PlanDraft.PackageDraft oneDay = pkg(Tier.BASIC, List.of(day(1, item(Slot.EVENING, activity("A", 30)))));
        PlanDraft wrongDays = new PlanDraft(List.of(oneDay, draft.packages().get(1), draft.packages().get(2)));
        assertThat(validator.validate(wrongDays, brief, catalog))
                .extracting(Violation::code).contains(ViolationCode.WRONG_DAY_COUNT);
    }

    @Test
    void tiersMustEachHaveAUniqueActivity() {
        UUID a = activity("A", 60);
        UUID b = activity("B", 60);
        List<PlanDraft.DayDraft> same = List.of(day(1, item(Slot.EVENING, a)), day(2, item(Slot.MORNING, b)));
        PlanDraft draft = new PlanDraft(List.of(pkg(Tier.BASIC, same), pkg(Tier.MEDIUM, same), pkg(Tier.PREMIUM, same)));

        assertThat(validator.validate(draft, brief, catalog))
                .extracting(Violation::code).contains(ViolationCode.TIER_NOT_DISTINCT);
    }

    @Test
    void textTooLong_isReported() {
        PlanDraft draft = validDraft();
        PlanDraft.PackageDraft p = draft.packages().get(0);
        String tooLongTitle = "x".repeat(PlanValidator.TITLE_MAX + 1);
        PlanDraft.PackageDraft longTitle = new PlanDraft.PackageDraft(p.key(), tooLongTitle, p.tagline(), p.description(), p.days());
        PlanDraft broken = new PlanDraft(List.of(longTitle, draft.packages().get(1), draft.packages().get(2)));

        assertThat(validator.validate(broken, brief, catalog))
                .extracting(Violation::code).contains(ViolationCode.TEXT_TOO_LONG);
    }
}
