package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.llm.FakeLlmGateway;
import com.myhive.backend.ai.llm.LlmUnavailableException;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.llm.PackageTexts;
import com.myhive.backend.ai.llm.TextRefreshRequest;
import com.myhive.backend.ai.llm.TextRefreshResult;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TextRefresherTest {

    private static final UUID BEER_SPA_ID = UUID.randomUUID();
    private static final UUID KARTING_ID = UUID.randomUUID();
    private static final UUID NIGHT_CLUB_ID = UUID.randomUUID();
    private static final UUID ESCAPE_ROOM_ID = UUID.randomUUID();
    private static final UUID RIVER_CRUISE_ID = UUID.randomUUID();
    private static final String LOCALE = "de";
    private static final String DESTINATION_NAME = "Prague";
    private static final LlmUsage FAKE_USAGE = new LlmUsage("fake-chat", 5, 7, 3L);

    private final FakeLlmGateway gateway = new FakeLlmGateway();
    private final TextRefresher refresher = new TextRefresher(gateway);

    @Test
    void noAppliedEdits_doesNotCallTheModel() {
        ComposedPlan expectedPlan = basicPlan();

        TextRefresher.Refreshed refreshed = refresher.refresh(expectedPlan,
                new EditOutcome(expectedPlan, List.of(), List.of(new RejectedEdit(EditOp.ADD, "Karting", Tier.BASIC,
                        EditRejectionReason.NO_FREE_SLOT, "no room"))),
                LOCALE, DESTINATION_NAME);

        assertThat(refreshed.plan()).isSameAs(expectedPlan);
        assertThat(refreshed.refreshed()).isFalse();
        assertThat(refreshed.usage()).isEqualTo(LlmUsage.none());
        assertThat(gateway.refreshRequests).isEmpty();
    }

    @Test
    void appliesOnlyRequestedFields_andCleansAndCaps() {
        String expectedDescription = "Rebuilt description";
        String expectedDayOneSummary = "Day one now with karting";
        String expectedDayTwoSummary = "Quieter day two";
        String expectedUntouchedWhy = "Warm up the group";
        String expectedUntouchedSummary = "Day three as before";
        ComposedPlan plan = basicPlan();
        gateway.queueRefresh(new TextRefreshResult(Map.of(Tier.BASIC, new PackageTexts(
                "<b>" + expectedDescription + "</b>",
                Map.of(KARTING_ID, "k".repeat(200), BEER_SPA_ID, "this item was not edited"),
                Map.of(1, "  Day one <i>now</i> with  karting ", 2, expectedDayTwoSummary,
                        3, "this day was not edited"))), FAKE_USAGE));

        TextRefresher.Refreshed refreshed = refresher.refresh(plan, new EditOutcome(plan,
                List.of(new AppliedEdit(EditOp.ADD, "Karting", null, Tier.BASIC, 1, Slot.AFTERNOON, KARTING_ID),
                        new AppliedEdit(EditOp.REMOVE, "Beer Bike", null, Tier.BASIC, 2, Slot.EVENING, null)),
                List.of()), LOCALE, DESTINATION_NAME);

        assertThat(refreshed.refreshed()).isTrue();
        assertThat(refreshed.usage()).isEqualTo(FAKE_USAGE);
        ComposedPlan.PackageResult basic = packageOf(refreshed.plan(), Tier.BASIC);
        assertThat(basic.description()).isEqualTo(expectedDescription);
        assertThat(whyOf(basic, KARTING_ID)).hasSize(PlanValidator.WHY_MAX).startsWith("kkk");
        assertThat(whyOf(basic, BEER_SPA_ID)).isEqualTo(expectedUntouchedWhy);
        assertThat(basic.days().get(0).summary()).isEqualTo(expectedDayOneSummary);
        assertThat(basic.days().get(1).summary()).isEqualTo(expectedDayTwoSummary);
        assertThat(basic.days().get(2).summary()).isEqualTo(expectedUntouchedSummary);
        ComposedPlan.PackageResult before = packageOf(plan, Tier.BASIC);
        assertThat(basic.pricePerPerson()).isEqualByComparingTo(before.pricePerPerson());
        assertThat(basic.totalPrice()).isEqualByComparingTo(before.totalPrice());
        assertThat(basic.activityIds()).isEqualTo(before.activityIds());
        assertThat(basic.days().get(0).items()).extracting(ComposedPlan.ItemResult::slot,
                        ComposedPlan.ItemResult::activityId, ComposedPlan.ItemResult::lineTotal)
                .containsExactly(tuple(Slot.MORNING, BEER_SPA_ID, new BigDecimal("160.00")),
                        tuple(Slot.AFTERNOON, KARTING_ID, new BigDecimal("160.00")));
        assertThat(gateway.refreshRequests).singleElement().satisfies(request -> {
            assertThat(request.locale()).isEqualTo(LOCALE);
            assertThat(request.destinationName()).isEqualTo(DESTINATION_NAME);
            assertThat(request.packages()).extracting(ComposedPlan.PackageResult::key).containsExactly(Tier.BASIC);
            assertThat(request.newActivityIds().get(Tier.BASIC)).containsExactly(KARTING_ID);
            assertThat(request.touchedDays().get(Tier.BASIC)).containsExactlyInAnyOrder(1, 2);
        });
    }

    @Test
    void modelFailure_keepsTextsAndReportsNotRefreshed() {
        ComposedPlan expectedPlan = basicPlan();
        gateway.failNextRefresh(new LlmUnavailableException("model down", new RuntimeException("connection reset")));

        TextRefresher.Refreshed refreshed = refresher.refresh(expectedPlan, new EditOutcome(expectedPlan,
                List.of(new AppliedEdit(EditOp.ADD, "Karting", null, Tier.BASIC, 1, Slot.AFTERNOON, KARTING_ID)),
                List.of()), LOCALE, DESTINATION_NAME);

        assertThat(refreshed.plan()).isSameAs(expectedPlan);
        assertThat(refreshed.refreshed()).isFalse();
        assertThat(refreshed.usage()).isEqualTo(LlmUsage.none());
        assertThat(gateway.refreshRequests).hasSize(1);
    }

    @Test
    void untouchedPackages_areReturnedUnchanged() {
        String expectedBasicDescription = "Rebuilt basic";
        ComposedPlan plan = new ComposedPlan(List.of(basicPackage(), mediumPackage()), false);
        ComposedPlan.PackageResult expectedMedium = packageOf(plan, Tier.MEDIUM);
        gateway.queueRefresh(new TextRefreshResult(Map.of(
                Tier.BASIC, new PackageTexts(expectedBasicDescription, Map.of(), Map.of()),
                Tier.MEDIUM, new PackageTexts("hallucinated for an untouched package", Map.of(), Map.of())),
                FAKE_USAGE));

        TextRefresher.Refreshed refreshed = refresher.refresh(plan, new EditOutcome(plan,
                List.of(new AppliedEdit(EditOp.ADD, "Karting", null, Tier.BASIC, 1, Slot.AFTERNOON, KARTING_ID)),
                List.of()), LOCALE, DESTINATION_NAME);

        assertThat(packageOf(refreshed.plan(), Tier.MEDIUM)).isSameAs(expectedMedium);
        assertThat(packageOf(refreshed.plan(), Tier.BASIC).description()).isEqualTo(expectedBasicDescription);
        assertThat(gateway.refreshRequests).singleElement().satisfies(request ->
                assertThat(request.packages()).extracting(ComposedPlan.PackageResult::key).containsExactly(Tier.BASIC));
    }

    private static ComposedPlan basicPlan() {
        return new ComposedPlan(List.of(basicPackage()), false);
    }

    private static ComposedPlan.PackageResult basicPackage() {
        ComposedPlan.DayResult dayOne = day(1, "Day one as before",
                item(BEER_SPA_ID, "Beer Spa", Slot.MORNING, "Warm up the group"),
                item(KARTING_ID, "Karting", Slot.AFTERNOON, "Old karting why"));
        ComposedPlan.DayResult dayTwo = day(2, "Day two as before",
                item(NIGHT_CLUB_ID, "Night Club", Slot.EVENING, "Old club why"));
        ComposedPlan.DayResult dayThree = day(3, "Day three as before",
                item(ESCAPE_ROOM_ID, "Escape Room", Slot.MORNING, "Old escape why"));
        return pkg(Tier.BASIC, "Old basic description", dayOne, dayTwo, dayThree);
    }

    private static ComposedPlan.PackageResult mediumPackage() {
        return pkg(Tier.MEDIUM, "Old medium description", day(1, "Medium day one",
                item(RIVER_CRUISE_ID, "River Cruise", Slot.AFTERNOON, "Old cruise why")));
    }

    private static ComposedPlan.PackageResult pkg(Tier key, String description, ComposedPlan.DayResult... days) {
        List<UUID> activityIds = List.of(days).stream()
                .flatMap(d -> d.items().stream())
                .map(ComposedPlan.ItemResult::activityId)
                .toList();
        return new ComposedPlan.PackageResult(key, key + " night", "tagline", description, new BigDecimal("40.00"),
                new BigDecimal("160.00"), ComposedPlan.CURRENCY, 300, activityIds, List.of(days));
    }

    private static ComposedPlan.DayResult day(int dayNumber, String summary, ComposedPlan.ItemResult... items) {
        return new ComposedPlan.DayResult(dayNumber, "Day " + dayNumber, summary, List.of(items));
    }

    private static ComposedPlan.ItemResult item(UUID activityId, String name, Slot slot, String why) {
        return new ComposedPlan.ItemResult(slot, "19:00", activityId, "slug", name, "https://img/x.jpg", 90,
                new BigDecimal("40.00"), null, new BigDecimal("160.00"), false, why);
    }

    private static ComposedPlan.PackageResult packageOf(ComposedPlan plan, Tier key) {
        return plan.packages().stream().filter(p -> p.key() == key).findFirst().orElseThrow();
    }

    private static String whyOf(ComposedPlan.PackageResult pkg, UUID activityId) {
        return pkg.days().stream().flatMap(d -> d.items().stream())
                .filter(i -> i.activityId().equals(activityId)).findFirst().orElseThrow().why();
    }
}
