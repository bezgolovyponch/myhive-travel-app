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
    void basicTooCheapForTheBudget_swapsUpToADearerActivityOfTheSameKind_andTheTiersStillRise() {
        // BASIC from 41 against a budget of 60: the River Cruise (25) for the Beer Bike (45), both sightseeing,
        // makes it 65 per person at catalog prices -> from 59.
        Brief brief = brief(new BudgetLimit(new BigDecimal("60"), BudgetLimit.Basis.PER_PERSON), List.of());

        ComposedPlan fitted = BudgetFitter.fit(editor, basePlan(brief), brief, catalog);

        assertThat(namesIn(fitted, Tier.BASIC)).containsExactlyInAnyOrder(beerBike.name(), escapeRoom.name());
        assertThat(priceOf(fitted, Tier.BASIC, brief)).isEqualByComparingTo("59");
        assertThat(priceOf(fitted, Tier.BASIC, brief).multiply(BudgetFitter.STEP_UP))
                .isLessThanOrEqualTo(priceOf(fitted, Tier.MEDIUM, brief));
        assertThat(priceOf(fitted, Tier.MEDIUM, brief)).isLessThan(priceOf(fitted, Tier.PREMIUM, brief));
    }

    @Test
    void basicTooDearForTheBudget_givesUpWhatLandsItNearest() {
        // BASIC from 41 against 25: without the Escape Room it is from 23, without the cruise from 18.
        Brief brief = brief(new BudgetLimit(new BigDecimal("25"), BudgetLimit.Basis.PER_PERSON), List.of());

        ComposedPlan fitted = BudgetFitter.fit(editor, basePlan(brief), brief, catalog);

        assertThat(namesIn(fitted, Tier.BASIC)).containsExactly(riverCruise.name());
        assertThat(priceOf(fitted, Tier.BASIC, brief)).isEqualByComparingTo("23");
    }

    @Test
    void whatWasAskedForByName_isNeverWhatGoes() {
        Brief brief = brief(new BudgetLimit(new BigDecimal("25"), BudgetLimit.Basis.PER_PERSON),
                List.of(new Wish(List.of(escapeRoom.name()), null)));

        ComposedPlan fitted = BudgetFitter.fit(editor, basePlan(brief), brief, catalog);

        assertThat(namesIn(fitted, Tier.BASIC)).containsExactly(escapeRoom.name());
    }

    @Test
    void aBudgetForTheWholeGroup_isAimedAtPerPerson() {
        Brief brief = brief(new BudgetLimit(new BigDecimal("240"), BudgetLimit.Basis.TOTAL), List.of());

        ComposedPlan fitted = BudgetFitter.fit(editor, basePlan(brief), brief, catalog);

        assertThat(brief.budgetPerPerson()).isEqualByComparingTo("60");
        assertThat(priceOf(fitted, Tier.BASIC, brief)).isEqualByComparingTo("59");
    }

    @Test
    void basicAlreadyAboutTheBudget_isLeftAsItIs() {
        Brief brief = brief(new BudgetLimit(new BigDecimal("44"), BudgetLimit.Basis.PER_PERSON), List.of());
        ComposedPlan plan = basePlan(brief);

        assertThat(namesIn(BudgetFitter.fit(editor, plan, brief, catalog), Tier.BASIC))
                .isEqualTo(namesIn(plan, Tier.BASIC));
    }

    @Test
    void withoutABudget_thePlanComesBackAsItWas() {
        Brief brief = brief(null, List.of());
        ComposedPlan plan = basePlan(brief);

        assertThat(BudgetFitter.fit(editor, plan, brief, catalog)).isSameAs(plan);
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

    private static BigDecimal priceOf(ComposedPlan plan, Tier tier, Brief brief) {
        return BudgetFitter.fromPerPerson(packageOf(plan, tier), brief);
    }

    private static List<String> namesIn(ComposedPlan plan, Tier tier) {
        return packageOf(plan, tier).days().stream()
                .flatMap(day -> day.items().stream())
                .map(ComposedPlan.ItemResult::name)
                .toList();
    }
}
