package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogPreset;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanDraft;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DraftSuggestionsTest {

    private static final int TRAVELERS = 10;

    private final Map<UUID, CatalogActivity> byId = new LinkedHashMap<>();
    private final CatalogActivity shooting = activity("Shooting", 90, "55.00", "guns");
    private final CatalogActivity dinner = activity("Czech Dinner", 90, "40.00", "food");
    private final CatalogActivity cabaret = activity("Cabaret Tour", 120, "10.00", "nightlife");
    private final CatalogActivity tasting = activity("Beer Tasting", 120, "30.00", "beer");
    private final CatalogActivity paintball = activity("Paintball", 120, "37.00", "extreme");
    private final CatalogActivity karting = activity("Karting", 45, "54.00", "extreme");
    private final CatalogActivity tank = activity("Army Tank", 60, "55.00", "extreme");

    /** Friday afternoon to Saturday morning: three slots on day 1, one on day 2. */
    private final Brief weekend = new Brief(2, TRAVELERS, List.of(), null, null, null, DayEdge.AFTERNOON,
            DayEdge.MORNING, null);

    @Test
    void offersWhatTheReadyMadePackagesHoldAndTheDraftLacks_ownTierFirst() {
        ComposedPlan plan = plan(
                pkg(Tier.BASIC, day(1, item(Slot.AFTERNOON, cabaret), item(Slot.EVENING, dinner)),
                        day(2, item(Slot.MORNING, shooting))),
                pkg(Tier.MEDIUM, day(1, item(Slot.AFTERNOON, shooting)), day(2)),
                pkg(Tier.PREMIUM, day(1, item(Slot.AFTERNOON, shooting), item(Slot.EVENING, karting)), day(2)));
        List<CatalogPreset> presets = List.of(
                preset(Tier.PREMIUM, tank, shooting),
                preset(Tier.BASIC, shooting, dinner, cabaret),
                preset(Tier.BASIC, paintball, dinner, tasting),
                preset(Tier.MEDIUM, shooting, karting));

        Map<Tier, List<CatalogActivity>> suggestions = DraftSuggestions.of(plan, weekend, catalog(), presets);

        // The other Basic package first, then Medium, then Premium; nothing the draft already has.
        assertThat(suggestions.get(Tier.BASIC)).extracting(CatalogActivity::name)
                .containsExactly(paintball.name(), tasting.name(), karting.name(), tank.name());
    }

    /** The exact case of a 1-night Basic draft: two on day 1, one on the last morning - the free night takes one. */
    @Test
    void aBasicDraftWithAFreeEvening_isOfferedSomething_thoughItsTierStopsAtTwoADay() {
        ComposedPlan plan = plan(
                pkg(Tier.BASIC, day(1, item(Slot.AFTERNOON, cabaret), item(Slot.EVENING, dinner)),
                        day(2, item(Slot.MORNING, shooting))),
                pkg(Tier.MEDIUM, day(1, item(Slot.AFTERNOON, shooting)), day(2)),
                pkg(Tier.PREMIUM, day(1, item(Slot.AFTERNOON, shooting), item(Slot.EVENING, karting)), day(2)));

        Map<Tier, List<CatalogActivity>> suggestions = DraftSuggestions.of(plan, weekend, catalog(),
                List.of(preset(Tier.BASIC, paintball, tasting)));

        assertThat(suggestions.get(Tier.BASIC)).isNotEmpty();
    }

    @Test
    void neverOffersWhatWouldNotGoIn() {
        // Every slot of the weekend is taken.
        ComposedPlan plan = plan(
                pkg(Tier.BASIC, day(1, item(Slot.AFTERNOON, cabaret)), day(2)),
                pkg(Tier.MEDIUM, day(1, item(Slot.AFTERNOON, shooting)), day(2)),
                pkg(Tier.PREMIUM, day(1, item(Slot.AFTERNOON, shooting), item(Slot.EVENING, dinner),
                        item(Slot.NIGHT, cabaret)), day(2, item(Slot.MORNING, karting))));

        Map<Tier, List<CatalogActivity>> suggestions = DraftSuggestions.of(plan, weekend, catalog(),
                List.of(preset(Tier.PREMIUM, tank, paintball, tasting)));

        assertThat(suggestions.get(Tier.PREMIUM)).isEmpty();
        assertThat(suggestions.get(Tier.BASIC)).isNotEmpty();
    }

    @Test
    void withoutReadyMadePackages_thereIsNothingToOffer() {
        ComposedPlan plan = plan(pkg(Tier.BASIC, day(1, item(Slot.AFTERNOON, cabaret)), day(2)));

        assertThat(DraftSuggestions.of(plan, weekend, catalog(), List.of())).isEmpty();
        assertThat(DraftSuggestions.of(null, weekend, catalog(), List.of(preset(Tier.BASIC, tank)))).isEmpty();
    }

    private CatalogActivity activity(String name, int minutes, String price, String category) {
        CatalogActivity activity = new CatalogActivity(UUID.randomUUID(), name.toLowerCase().replace(' ', '-'), name,
                "line", minutes, true, new BigDecimal(price), null, null, List.of(category));
        byId.put(activity.id(), activity);
        return activity;
    }

    private List<CatalogActivity> catalog() {
        return new ArrayList<>(byId.values());
    }

    private static CatalogPreset preset(Tier tier, CatalogActivity... activities) {
        return new CatalogPreset(UUID.randomUUID(), tier + " preset", tier,
                Arrays.stream(activities).map(CatalogActivity::id).toList(), List.of());
    }

    private ComposedPlan plan(PlanDraft.PackageDraft... packages) {
        return new PlanAssembler().assemble(new PlanDraft(List.of(packages)), weekend, byId, false).plan();
    }

    private static PlanDraft.PackageDraft pkg(Tier tier, PlanDraft.DayDraft... days) {
        return new PlanDraft.PackageDraft(tier, tier.name(), null, null, List.of(days));
    }

    private static PlanDraft.DayDraft day(int number, PlanDraft.ItemDraft... items) {
        return new PlanDraft.DayDraft(number, null, null, List.of(items));
    }

    private static PlanDraft.ItemDraft item(Slot slot, CatalogActivity activity) {
        return new PlanDraft.ItemDraft(slot, null, activity.id(), null);
    }
}
