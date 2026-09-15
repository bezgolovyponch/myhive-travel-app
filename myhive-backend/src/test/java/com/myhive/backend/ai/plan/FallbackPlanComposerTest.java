package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
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
}
