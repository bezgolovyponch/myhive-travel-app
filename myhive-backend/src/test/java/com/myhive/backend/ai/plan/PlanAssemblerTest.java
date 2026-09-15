package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanAssemblerTest {

    private final PlanAssembler assembler = new PlanAssembler();
    private final Map<UUID, CatalogActivity> catalog = new HashMap<>();
    private final Brief brief = new Brief(1, 4, List.of(), "x", null, null, DayEdge.MORNING, DayEdge.EVENING, null);

    private UUID activity(String name, String price, String minPrice) {
        UUID id = UUID.randomUUID();
        catalog.put(id, new CatalogActivity(id, name.toLowerCase(), name, "line", 90, true,
                new BigDecimal(price), minPrice == null ? null : new BigDecimal(minPrice), "img", List.of()));
        return id;
    }

    private static PlanDraft.PackageDraft pkg(Tier tier, UUID... ids) {
        Slot[] slots = Slot.values();
        List<PlanDraft.ItemDraft> items = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            items.add(new PlanDraft.ItemDraft(slots[i], "10:00", ids[i], "why"));
        }
        return new PlanDraft.PackageDraft(tier, tier.name(), "tag", "desc",
                List.of(new PlanDraft.DayDraft(1, "Day 1", "sum", items)));
    }

    @Test
    void assemble_pricesFromCatalog_andOrdersTiers() {
        String expectedCheapPrice = "10.00";
        String expectedFloor = "500.00";
        UUID cheap = activity("Cheap", expectedCheapPrice, null);
        UUID mid = activity("Mid", "40.00", null);
        UUID floor = activity("Floor", "20.00", expectedFloor);
        PlanDraft draft = new PlanDraft(List.of(pkg(Tier.BASIC, cheap), pkg(Tier.MEDIUM, mid), pkg(Tier.PREMIUM, mid, floor)));

        PlanAssembler.AssemblyResult result = assembler.assemble(draft, brief, catalog, false);

        assertThat(result.violations()).isEmpty();
        List<ComposedPlan.PackageResult> packages = result.plan().packages();
        assertThat(packages).extracting(ComposedPlan.PackageResult::key).containsExactly(Tier.BASIC, Tier.MEDIUM, Tier.PREMIUM);
        assertThat(packages.get(0).totalPrice()).isEqualByComparingTo("40.00");
        assertThat(packages.get(0).pricePerPerson()).isEqualByComparingTo(expectedCheapPrice);
        ComposedPlan.ItemResult floored = packages.get(2).days().get(0).items().get(1);
        assertThat(floored.lineTotal()).isEqualByComparingTo(expectedFloor);
        assertThat(floored.groupMinApplied()).isTrue();
        assertThat(packages.get(2).totalPrice()).isEqualByComparingTo("660.00");
        assertThat(packages.get(2).totalDurationMinutes()).isEqualTo(180);
        assertThat(packages.get(2).activityIds()).containsExactly(mid, floor);
        assertThat(result.plan().currencyOf(packages.get(0))).isEqualTo("EUR");
    }

    @Test
    void assemble_reportsTierOrderWhenNotStrictlyAscending() {
        UUID a = activity("A", "30.00", null);
        UUID b = activity("B", "30.00", null);
        PlanDraft draft = new PlanDraft(List.of(pkg(Tier.BASIC, a), pkg(Tier.MEDIUM, b), pkg(Tier.PREMIUM, a, b)));

        PlanAssembler.AssemblyResult result = assembler.assemble(draft, brief, catalog, false);

        assertThat(result.violations()).extracting(Violation::code).containsExactly(ViolationCode.TIER_ORDER);
    }

    @Test
    void assemble_stripsHtmlFromModelText() {
        String expectedTitle = "Bold night";
        String expectedWhy = "ok";
        boolean expectedDegraded = true;
        UUID a = activity("A", "30.00", null);
        PlanDraft.PackageDraft p = new PlanDraft.PackageDraft(Tier.BASIC, "<b>Bold</b> night", "t", "d",
                List.of(new PlanDraft.DayDraft(1, "x", "y", List.of(new PlanDraft.ItemDraft(Slot.MORNING, null, a, "<script>x</script>ok")))));
        PlanDraft draft = new PlanDraft(List.of(p, pkg(Tier.MEDIUM, a), pkg(Tier.PREMIUM, a)));

        ComposedPlan plan = assembler.assemble(draft, brief, catalog, expectedDegraded).plan();

        assertThat(plan.packages().get(0).title()).isEqualTo(expectedTitle);
        assertThat(plan.packages().get(0).days().get(0).items().get(0).why()).isEqualTo(expectedWhy);
        assertThat(plan.degraded()).isEqualTo(expectedDegraded);
    }
}
