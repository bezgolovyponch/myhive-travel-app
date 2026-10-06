package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogPreset;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Tier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackPlanComposerTest {

    private final FallbackPlanComposer composer = new FallbackPlanComposer();
    private final PlanValidator validator = new PlanValidator();
    private final PlanAssembler assembler = new PlanAssembler();

    private static List<CatalogActivity> catalog(int size) {
        List<CatalogActivity> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(new CatalogActivity(UUID.randomUUID(), "a" + i, "Activity " + i, "line", 60 + (i % 4) * 30, true,
                    new BigDecimal(20 + i * 7), i % 5 == 0 ? new BigDecimal("400.00") : null, null,
                    List.of(i % 2 == 0 ? "nightlife" : "driving")));
        }
        return list;
    }

    private static CatalogActivity activity(String slug, int price, String minPrice, int durationMinutes,
            String... categories) {
        return new CatalogActivity(UUID.randomUUID(), slug, slug, "line", durationMinutes, true,
                new BigDecimal(price), minPrice == null ? null : new BigDecimal(minPrice), null, List.of(categories));
    }

    @Test
    void compose_producesThreeValidAscendingTiers() {
        List<CatalogActivity> catalog = catalog(20);
        Brief brief = new Brief(3, 8, List.of("driving"), null, null, null, DayEdge.EVENING, DayEdge.MORNING, null);
        Map<UUID, CatalogActivity> byId = catalog.stream().collect(Collectors.toMap(CatalogActivity::id, Function.identity()));

        PlanDraft draft = composer.compose(brief, catalog, "en");

        assertThat(validator.validate(draft, brief, byId)).isEmpty();
        PlanAssembler.AssemblyResult result = assembler.assemble(draft, brief, byId, true);
        assertThat(result.violations()).isEmpty();
        assertThat(result.plan().packages()).extracting(ComposedPlan.PackageResult::key)
                .containsExactly(Tier.BASIC, Tier.MEDIUM, Tier.PREMIUM);
    }

    @Test
    void compose_prefersBriefCategories() {
        String expectedCategory = "driving";
        List<CatalogActivity> catalog = catalog(20);
        Brief brief = new Brief(2, 6, List.of(expectedCategory), null, null, null, DayEdge.AFTERNOON, DayEdge.EVENING, null);
        Map<UUID, CatalogActivity> byId = catalog.stream().collect(Collectors.toMap(CatalogActivity::id, Function.identity()));

        PlanDraft draft = composer.compose(brief, catalog, "en");

        long driving = draft.packages().get(0).days().stream().flatMap(d -> d.items().stream())
                .filter(i -> byId.get(i.activityId()).categorySlugs().contains(expectedCategory)).count();
        long total = draft.packages().get(0).days().stream().mapToLong(d -> d.items().size()).sum();
        assertThat(driving).isEqualTo(total);
    }

    @Test
    void compose_withTinyCatalog_stillReturnsThreePackages() {
        int expectedDayCount = 1;
        List<CatalogActivity> catalog = catalog(3);
        Brief brief = new Brief(expectedDayCount, 4, List.of(), "chill", null, null, DayEdge.MORNING, DayEdge.EVENING, null);

        PlanDraft draft = composer.compose(brief, catalog, "en");

        assertThat(draft.packages()).hasSize(3);
        assertThat(draft.packages()).allMatch(p -> p.days().size() == expectedDayCount);
    }

    /**
     * Final-review finding: a 1-day brief whose edges the agent never learned defaults to arrival
     * AFTERNOON / departure MORNING. That window used to come out empty, so the composer could place
     * nothing at all and three empty EUR 0 packages were persisted READY.
     */
    @Test
    void compose_oneDayBriefWithoutEdges_fillsEveryPackage() {
        int expectedDayCount = 1;
        List<CatalogActivity> catalog = catalog(20);
        Brief brief = new Brief(expectedDayCount, 6, List.of("driving"), null, null, null, null, null, null);
        Map<UUID, CatalogActivity> byId = catalog.stream().collect(Collectors.toMap(CatalogActivity::id, Function.identity()));

        PlanDraft draft = composer.compose(brief, catalog, "en");

        assertThat(validator.validate(draft, brief, byId)).isEmpty();
        PlanAssembler.AssemblyResult result = assembler.assemble(draft, brief, byId, true);
        assertThat(result.violations()).isEmpty();
        assertThat(draft.packages()).hasSize(Tier.values().length)
                .allSatisfy(p -> assertThat(p.days()).hasSize(expectedDayCount)
                        .allSatisfy(d -> assertThat(d.items()).as("tier %s day %d", p.key(), d.dayNumber()).isNotEmpty()));
        assertThat(result.plan().packages())
                .allSatisfy(p -> assertThat(p.totalPrice()).isGreaterThan(BigDecimal.ZERO));
    }

    // Finding 1: ranking by category match before price let signature prices invert, causing TIER_ORDER
    // even on a catalog with >= 6 distinct prices.
    @Test
    void compose_sixDistinctPricesOnlyOneCategoryMatch_keepsPerPersonPriceAscending() {
        List<CatalogActivity> catalog = List.of(
                activity("a0", 10, null, 60, "party"),
                activity("a1", 20, null, 60),
                activity("a2", 30, null, 60),
                activity("a3", 40, null, 60),
                activity("a4", 50, null, 60),
                activity("a5", 60, null, 60));
        Brief brief = new Brief(2, 2, List.of("party"), null, null, null, DayEdge.MORNING, DayEdge.EVENING, null);
        Map<UUID, CatalogActivity> byId = catalog.stream().collect(Collectors.toMap(CatalogActivity::id, Function.identity()));

        PlanDraft draft = composer.compose(brief, catalog, "en");

        assertThat(validator.validate(draft, brief, byId)).isEmpty();
        PlanAssembler.AssemblyResult result = assembler.assemble(draft, brief, byId, true);
        assertThat(result.violations()).isEmpty();
        assertThat(result.plan().packages()).extracting(ComposedPlan.PackageResult::key)
                .containsExactly(Tier.BASIC, Tier.MEDIUM, Tier.PREMIUM);
    }

    // Finding 1b: signature/fill ordering used raw price, but the assembler bills lines via
    // PlanPricer.lineTotal(price, minPrice, groupSize) - a cheap activity with a large group minimum is
    // actually the most expensive line and must be ranked (and reserved) accordingly.
    @Test
    void compose_cheapActivityWithLargeGroupMinimum_keepsPerPersonPriceAscending() {
        List<CatalogActivity> catalog = List.of(
                activity("b0", 5, "1000.00", 60),
                activity("b1", 15, null, 60),
                activity("b2", 25, null, 60),
                activity("b3", 35, null, 60),
                activity("b4", 45, null, 60),
                activity("b5", 55, null, 60),
                activity("b6", 65, null, 60),
                activity("b7", 75, null, 60));
        Brief brief = new Brief(1, 2, List.of(), null, null, null, DayEdge.MORNING, DayEdge.EVENING, null);
        Map<UUID, CatalogActivity> byId = catalog.stream().collect(Collectors.toMap(CatalogActivity::id, Function.identity()));

        PlanDraft draft = composer.compose(brief, catalog, "en");

        assertThat(validator.validate(draft, brief, byId)).isEmpty();
        PlanAssembler.AssemblyResult result = assembler.assemble(draft, brief, byId, true);
        assertThat(result.violations()).isEmpty();
        assertThat(result.plan().packages()).extracting(ComposedPlan.PackageResult::key)
                .containsExactly(Tier.BASIC, Tier.MEDIUM, Tier.PREMIUM);
    }

    // Finding 2: a signature whose duration didn't fit its tier's minute cap was silently dropped while
    // filling, so BASIC lost its exclusive activity and became a subset of the other tiers.
    @Test
    void compose_oneLongActivityAmongShortOnes_keepsEveryTierDistinct() {
        List<CatalogActivity> catalog = List.of(
                activity("c0", 200, null, 480),
                activity("c1", 10, null, 60),
                activity("c2", 20, null, 60),
                activity("c3", 30, null, 60),
                activity("c4", 40, null, 60),
                activity("c5", 50, null, 60),
                activity("c6", 60, null, 60),
                activity("c7", 70, null, 60));
        Brief brief = new Brief(1, 2, List.of(), null, null, null, DayEdge.MORNING, DayEdge.EVENING, null);
        Map<UUID, CatalogActivity> byId = catalog.stream().collect(Collectors.toMap(CatalogActivity::id, Function.identity()));

        PlanDraft draft = composer.compose(brief, catalog, "en");

        assertThat(validator.validate(draft, brief, byId)).isEmpty();
        PlanAssembler.AssemblyResult result = assembler.assemble(draft, brief, byId, true);
        assertThat(result.violations()).isEmpty();
    }

    // Finding 3: the greedy cursor never rewound, so one over-long candidate drained the remaining pool and
    // emptied every later day.
    @Test
    void compose_allActivitiesSameLongDuration_hasNoEmptyDay() {
        int expectedDayCount = 3;
        List<CatalogActivity> catalog = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            catalog.add(activity("d" + i, 10 * (i + 1), null, 240));
        }
        Brief brief = new Brief(expectedDayCount, 2, List.of(), null, null, null, DayEdge.MORNING, DayEdge.EVENING, null);
        Map<UUID, CatalogActivity> byId = catalog.stream().collect(Collectors.toMap(CatalogActivity::id, Function.identity()));

        PlanDraft draft = composer.compose(brief, catalog, "en");

        assertThat(validator.validate(draft, brief, byId)).isEmpty();
        PlanAssembler.AssemblyResult result = assembler.assemble(draft, brief, byId, true);
        assertThat(result.violations()).isEmpty();
        assertThat(draft.packages()).allSatisfy(p -> assertThat(p.days()).hasSize(expectedDayCount)
                .allSatisfy(d -> assertThat(d.items()).as("tier %s day %d", p.key(), d.dayNumber()).isNotEmpty()));
    }

    // Finding 3 (thin catalog variant): fill() used to front-load each day to the tier cap, so a thin
    // multi-day catalog could leave a middle day empty even though one-per-day would have covered it.
    @Test
    void compose_sevenDaysTenShortActivities_neverLeavesAMiddleDayEmpty() {
        int expectedDayCount = 7;
        List<CatalogActivity> catalog = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            catalog.add(activity("e" + i, 10 * (i + 1), null, 60));
        }
        Brief brief = new Brief(expectedDayCount, 2, List.of(), null, null, null, DayEdge.MORNING, DayEdge.EVENING, null);
        Map<UUID, CatalogActivity> byId = catalog.stream().collect(Collectors.toMap(CatalogActivity::id, Function.identity()));

        PlanDraft draft = composer.compose(brief, catalog, "en");

        assertThat(validator.validate(draft, brief, byId)).isEmpty();
        PlanAssembler.AssemblyResult result = assembler.assemble(draft, brief, byId, true);
        assertThat(result.violations()).isEmpty();
        assertThat(draft.packages()).allSatisfy(p -> assertThat(p.days().stream()
                        .filter(d -> d.dayNumber() > 1 && d.dayNumber() < expectedDayCount)
                        .toList())
                .as("middle days of %s", p.key())
                .allSatisfy(d -> assertThat(d.items()).isNotEmpty()));
    }

    private static CatalogPreset preset(Tier tier, List<String> categories, CatalogActivity... activities) {
        return new CatalogPreset(UUID.randomUUID(), tier + " preset", tier,
                java.util.Arrays.stream(activities).map(CatalogActivity::id).toList(), categories);
    }

    @Test
    void compose_withPresets_givesEachTierExactlyItsPackage() {
        CatalogActivity tasting = activity("tasting", 30, null, 120, "czech-beer");
        CatalogActivity dinner = activity("dinner", 40, null, 90, "food-and-drink");
        CatalogActivity shooting = activity("shooting", 55, null, 90, "guns-and-bullets");
        CatalogActivity karting = activity("karting", 54, null, 45, "extreme");
        CatalogActivity tank = activity("tank", 180, null, 60, "extreme");
        List<CatalogActivity> catalog = List.of(tasting, dinner, shooting, karting, tank);
        List<UUID> expectedBasic = List.of(tasting.id(), dinner.id());
        List<UUID> expectedMedium = List.of(shooting.id(), dinner.id(), tasting.id());
        List<UUID> expectedPremium = List.of(shooting.id(), karting.id(), dinner.id(), tasting.id());
        List<CatalogPreset> presets = List.of(
                preset(Tier.BASIC, List.of(), tasting, dinner),
                preset(Tier.MEDIUM, List.of(), shooting, dinner, tasting),
                preset(Tier.PREMIUM, List.of(), shooting, karting, dinner, tasting));
        Brief brief = new Brief(3, 10, List.of(), null, null, null, DayEdge.AFTERNOON, DayEdge.MORNING, null);

        PlanDraft draft = composer.compose(brief, catalog, presets, "en");

        assertThat(PlanValidator.activityIds(draft.packages().get(0))).containsExactlyInAnyOrderElementsOf(expectedBasic);
        assertThat(PlanValidator.activityIds(draft.packages().get(1))).containsExactlyInAnyOrderElementsOf(expectedMedium);
        assertThat(PlanValidator.activityIds(draft.packages().get(2))).containsExactlyInAnyOrderElementsOf(expectedPremium);
    }

    @Test
    void compose_withSeveralPresetsForATier_takesTheOneMatchingTheBrief() {
        String wantedCategory = "extreme";
        CatalogActivity tasting = activity("tasting", 30, null, 120, "czech-beer");
        CatalogActivity paintball = activity("paintball", 37, null, 120, wantedCategory);
        List<CatalogActivity> catalog = List.of(tasting, paintball);
        List<CatalogPreset> presets = List.of(
                preset(Tier.BASIC, List.of("czech-beer"), tasting),
                preset(Tier.BASIC, List.of(wantedCategory), paintball));
        Brief brief = new Brief(2, 10, List.of(wantedCategory), null, null, null, DayEdge.AFTERNOON, DayEdge.MORNING, null);

        PlanDraft draft = composer.compose(brief, catalog, presets, "en");

        assertThat(PlanValidator.activityIds(draft.packages().get(0))).containsExactly(paintball.id());
    }
}
