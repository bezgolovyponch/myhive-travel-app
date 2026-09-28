package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.llm.FakeLlmGateway;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.llm.PackageTexts;
import com.myhive.backend.ai.llm.PlanTextsResult;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTextWriterTest {

    private static final String LOCALE = "en";
    private static final String DESTINATION = "Prague";

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final PlanTextWriter writer = new PlanTextWriter(llm);
    private final UUID activityId = UUID.randomUUID();
    private final Brief brief = new Brief(1, 4, List.of("nightlife"), null, null, null, DayEdge.AFTERNOON,
            DayEdge.EVENING, null);

    @Test
    void placeholders_nameNamelessPackagesAndDays_andLeaveExistingTitlesAlone() {
        String expectedKeptTitle = "Already named";
        ComposedPlan plan = new ComposedPlan(List.of(
                pkg(Tier.BASIC, null, null),
                pkg(Tier.PREMIUM, expectedKeptTitle, "Landing day")), false);

        ComposedPlan named = PlanTextWriter.placeholders(plan, "de");

        assertThat(named.packages().get(0).title()).isEqualTo("Warm-up");
        assertThat(named.packages().get(0).days().get(0).title()).isEqualTo("Tag 1");
        assertThat(named.packages().get(0).description()).isNull();
        assertThat(named.packages().get(1).title()).isEqualTo(expectedKeptTitle);
        assertThat(named.packages().get(1).days().get(0).title()).isEqualTo("Landing day");
    }

    @Test
    void write_mergesTheModelsCopy_ontoThePlaceholders_andKeepsPricesUntouched() {
        String expectedTitle = "Beer, Bikes and Bad Decisions";
        String expectedTagline = "Two loud nights";
        String expectedDescription = "You wanted beer.";
        String expectedDayTitle = "Landing day";
        String expectedSummary = "Easy start";
        String expectedWhy = "Because you asked for beer";
        LlmUsage expectedUsage = new LlmUsage("fake-chat", 7, 9, 4L);
        ComposedPlan plan = new ComposedPlan(List.of(pkg(Tier.BASIC, null, null), pkg(Tier.MEDIUM, null, null)), false);
        llm.queueTexts(new PlanTextsResult(Map.of(Tier.BASIC, new PackageTexts(expectedTitle, expectedTagline,
                expectedDescription, Map.of(1, expectedDayTitle), Map.of(activityId, expectedWhy),
                Map.of(1, expectedSummary))), expectedUsage));

        PlanTextWriter.Written written = writer.write(plan, brief, LOCALE, DESTINATION, List.of());

        assertThat(written.written()).isTrue();
        assertThat(written.usage()).isEqualTo(expectedUsage);
        ComposedPlan.PackageResult basic = written.plan().packages().get(0);
        assertThat(basic.title()).isEqualTo(expectedTitle);
        assertThat(basic.tagline()).isEqualTo(expectedTagline);
        assertThat(basic.description()).isEqualTo(expectedDescription);
        assertThat(basic.days().get(0).title()).isEqualTo(expectedDayTitle);
        assertThat(basic.days().get(0).summary()).isEqualTo(expectedSummary);
        assertThat(basic.days().get(0).items().get(0).why()).isEqualTo(expectedWhy);
        assertThat(basic.pricePerPerson()).isEqualByComparingTo(plan.packages().get(0).pricePerPerson());
        assertThat(basic.activityIds()).isEqualTo(plan.packages().get(0).activityIds());
        // The model skipped MEDIUM: the stock name stays, nothing goes nameless.
        assertThat(written.plan().packages().get(1).title()).isEqualTo("Main Event");
        // One call per package, each carrying that package alone, already named with its placeholder.
        assertThat(llm.textsRequests).hasSize(2);
        assertThat(llm.textsRequests.get(0).destinationName()).isEqualTo(DESTINATION);
        assertThat(llm.textsRequests.get(0).plan().packages()).singleElement().satisfies(sent -> {
            assertThat(sent.key()).isEqualTo(Tier.BASIC);
            assertThat(sent.title()).isEqualTo("Warm-up");
        });
        assertThat(llm.textsRequests.get(1).plan().packages()).singleElement()
                .satisfies(sent -> assertThat(sent.key()).isEqualTo(Tier.MEDIUM));
    }

    @Test
    void write_cleansAndCapsWhatTheModelWrote() {
        int expectedTitleLength = PlanValidator.TITLE_MAX;
        ComposedPlan plan = new ComposedPlan(List.of(pkg(Tier.BASIC, null, null)), false);
        llm.queueTexts(new PlanTextsResult(Map.of(Tier.BASIC, new PackageTexts("T".repeat(200),
                "<script>alert(1)</script>", "  ", Map.of(), Map.of(), Map.of())), LlmUsage.none()));

        PlanTextWriter.Written written = writer.write(plan, brief, LOCALE, DESTINATION, List.of());

        ComposedPlan.PackageResult basic = written.plan().packages().get(0);
        assertThat(basic.title()).hasSize(expectedTitleLength);
        assertThat(basic.tagline()).isNull();
        assertThat(basic.description()).isNull();
    }

    @Test
    void write_whenTheModelFails_shipsThePlaceholders() {
        ComposedPlan plan = new ComposedPlan(List.of(pkg(Tier.PREMIUM, null, null)), false);
        llm.failNextTexts(new IllegalStateException("model down"));

        PlanTextWriter.Written written = writer.write(plan, brief, LOCALE, DESTINATION, List.of());

        assertThat(written.written()).isFalse();
        assertThat(written.usage()).isEqualTo(LlmUsage.none());
        assertThat(written.plan().packages().get(0).title()).isEqualTo("Full Send");
        assertThat(written.plan().packages().get(0).days().get(0).title()).isEqualTo("Day 1");
    }

    @Test
    void write_whenTheAnswerNamesNoPackage_isNotWritten_butStillCounted() {
        LlmUsage expectedUsage = new LlmUsage("fake-chat", 3, 1, 2L);
        ComposedPlan plan = new ComposedPlan(List.of(pkg(Tier.BASIC, null, null)), false);
        llm.queueTexts(new PlanTextsResult(Map.of(), expectedUsage));

        PlanTextWriter.Written written = writer.write(plan, brief, LOCALE, DESTINATION, List.of());

        assertThat(written.written()).isFalse();
        assertThat(written.usage()).isEqualTo(expectedUsage);
        assertThat(written.plan().packages().get(0).title()).isEqualTo("Warm-up");
    }

    private ComposedPlan.PackageResult pkg(Tier tier, String title, String dayTitle) {
        ComposedPlan.ItemResult item = new ComposedPlan.ItemResult(Slot.EVENING, null, activityId, "beer-bike",
                "Beer Bike", null, 120, new BigDecimal("35.00"), null, new BigDecimal("140.00"), false, null);
        ComposedPlan.DayResult day = new ComposedPlan.DayResult(1, dayTitle, null, List.of(item));
        return new ComposedPlan.PackageResult(tier, title, null, null, new BigDecimal("35.00"),
                new BigDecimal("140.00"), ComposedPlan.CURRENCY, 120, List.of(activityId), List.of(day));
    }
}
