package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.edit.PackageEditor;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.BudgetLimit;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.model.Wish;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetFitterTest {

    private static final int TRAVELERS = 4;

    private final PlanValidator validator = new PlanValidator();
    private final PlanAssembler assembler = new PlanAssembler();
    private final PackageEditor editor = new PackageEditor(validator, assembler);

    private final List<CatalogActivity> catalog = new ArrayList<>();
    private final CatalogActivity riverCruise = activity("River Cruise", "25.00", 60, "sightseeing");
    private final CatalogActivity escapeRoom = activity("Escape Room", "20.00", 75, "indoor");
    private final CatalogActivity beerSpa = activity("Beer Spa", "40.00", 90, "wellness");
    private final CatalogActivity beerBike = activity("Beer Bike", "45.00", 120, "sightseeing");
    private final CatalogActivity shootingRange = activity("Shooting Range", "60.00", 300, "adrenaline");
    private final CatalogActivity paintball = activity("Paintball", "30.00", 120, "adrenaline");

    @Test
    void packageOverTheBudget_swapsAnActivityForACheaperOneOfTheSameKind_andKeepsTheTiersInOrder() {
        // PREMIUM: 145 per person at catalog prices -> from 131. Shooting for paintball: 115 -> from 104, still
        // above MEDIUM's 99; taking anything out would fit too but drop PREMIUM under MEDIUM.
        Brief brief = brief(new BudgetLimit(new BigDecimal("110"), BudgetLimit.Basis.PER_PERSON), List.of());
        ComposedPlan plan = basePlan(brief);

        ComposedPlan fitted = BudgetFitter.fit(editor, plan, brief, catalog);

        assertThat(namesIn(fitted, Tier.PREMIUM)).contains(paintball.name()).doesNotContain(shootingRange.name());
        assertThat(BudgetFitter.fromPerPerson(packageOf(fitted, Tier.PREMIUM), brief)).isEqualByComparingTo("104");
        assertThat(namesIn(fitted, Tier.BASIC)).isEqualTo(namesIn(plan, Tier.BASIC));
        assertThat(namesIn(fitted, Tier.MEDIUM)).isEqualTo(namesIn(plan, Tier.MEDIUM));
        assertThat(fitted.packages()).noneMatch(pkg -> BudgetFitter.overBudget(pkg, brief));
    }

    @Test
    void aTightBudget_stillLeavesTheTiersRisingInPrice() {
        Brief brief = brief(new BudgetLimit(new BigDecimal("60"), BudgetLimit.Basis.PER_PERSON), List.of());

        ComposedPlan fitted = BudgetFitter.fit(editor, basePlan(brief), brief, catalog);

        List<BigDecimal> prices = fitted.packages().stream().map(pkg -> BudgetFitter.fromPerPerson(pkg, brief)).toList();
        assertThat(prices).allSatisfy(price -> assertThat(price).isLessThanOrEqualTo(new BigDecimal("60")));
        assertThat(prices.get(0)).isLessThan(prices.get(1));
        assertThat(prices.get(1)).isLessThan(prices.get(2));
    }

    @Test
    void aBudgetForTheWholeGroup_isHeldPerPerson() {
        Brief brief = brief(new BudgetLimit(new BigDecimal("440"), BudgetLimit.Basis.TOTAL), List.of());

        ComposedPlan fitted = BudgetFitter.fit(editor, basePlan(brief), brief, catalog);

        assertThat(brief.budgetPerPerson()).isEqualByComparingTo("110");
        assertThat(fitted.packages()).noneMatch(pkg -> BudgetFitter.overBudget(pkg, brief));
    }

    @Test
    void whatWasAskedForByName_isNeverWhatGoes() {
        Brief brief = brief(new BudgetLimit(new BigDecimal("110"), BudgetLimit.Basis.PER_PERSON),
                List.of(new Wish(List.of(shootingRange.name()), null)));

        ComposedPlan fitted = BudgetFitter.fit(editor, basePlan(brief), brief, catalog);

        assertThat(namesIn(fitted, Tier.PREMIUM)).contains(shootingRange.name());
        assertThat(BudgetFitter.overBudget(packageOf(fitted, Tier.PREMIUM), brief)).isFalse();
    }

    @Test
    void aBudgetNothingCanMeet_leavesEveryPackageWithAnActivity_andSaysItIsOver() {
        Brief brief = brief(new BudgetLimit(new BigDecimal("5"), BudgetLimit.Basis.PER_PERSON), List.of());

        ComposedPlan fitted = BudgetFitter.fit(editor, basePlan(brief), brief, catalog);

        assertThat(fitted.packages()).allSatisfy(pkg -> {
            assertThat(pkg.activityIds()).isNotEmpty();
            assertThat(BudgetFitter.overBudget(pkg, brief)).isTrue();
        });
    }

    @Test
    void withoutABudget_thePlanComesBackAsItWas() {
        Brief brief = brief(null, List.of());
        ComposedPlan plan = basePlan(brief);

        assertThat(BudgetFitter.fit(editor, plan, brief, catalog)).isSameAs(plan);
        assertThat(plan.packages()).noneMatch(pkg -> BudgetFitter.overBudget(pkg, brief));
    }

    private static Brief brief(BudgetLimit limit, List<Wish> wishes) {
        return new Brief(2, TRAVELERS, List.of(), "stag weekend", null, null, DayEdge.MORNING, DayEdge.EVENING, null,
                wishes, limit);
    }

    /** BASIC from 41, MEDIUM from 99, PREMIUM from 131 per person. */
    private ComposedPlan basePlan(Brief brief) {
        PlanDraft draft = new PlanDraft(List.of(
                pkg(Tier.BASIC, day(1, item(Slot.AFTERNOON, riverCruise)), day(2, item(Slot.AFTERNOON, escapeRoom))),
                pkg(Tier.MEDIUM, day(1, item(Slot.MORNING, beerSpa), item(Slot.AFTERNOON, beerBike)),
                        day(2, item(Slot.MORNING, riverCruise))),
                pkg(Tier.PREMIUM, day(1, item(Slot.MORNING, shootingRange), item(Slot.AFTERNOON, beerBike)),
                        day(2, item(Slot.MORNING, beerSpa)))));
        return assembler.assemble(draft, brief, byId(), false).plan();
    }

    private CatalogActivity activity(String name, String price, int durationMinutes, String category) {
        CatalogActivity created = new CatalogActivity(UUID.randomUUID(), name.toLowerCase(Locale.ROOT).replace(' ', '-'),
                name, "one line", durationMinutes, true, new BigDecimal(price), null, "img", List.of(category));
        catalog.add(created);
        return created;
    }

    private Map<UUID, CatalogActivity> byId() {
        Map<UUID, CatalogActivity> index = new LinkedHashMap<>();
        catalog.forEach(entry -> index.put(entry.id(), entry));
        return index;
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

    private static ComposedPlan.PackageResult packageOf(ComposedPlan plan, Tier tier) {
        return plan.packages().stream().filter(pkg -> pkg.key() == tier).findFirst().orElseThrow();
    }

    private static List<String> namesIn(ComposedPlan plan, Tier tier) {
        return packageOf(plan, tier).days().stream()
                .flatMap(day -> day.items().stream())
                .map(ComposedPlan.ItemResult::name)
                .toList();
    }
}
