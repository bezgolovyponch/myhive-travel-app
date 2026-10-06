package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogPreset;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DraftGapsTest {

    private static final CatalogActivity SHOOTING = activity("Shooting", "guns-and-bullets", "extreme");
    private static final CatalogActivity STEAK = activity("Steak & Show", "food-and-drink", "stag-hot-babies-and-pranks");
    private static final CatalogActivity BEER_SPA = activity("Beer Spa", "czech-beer");

    /** The draft is set against the presets of its own tier: what they are built around and it lacks. */
    @Test
    void gaps_areThePresetThemesOfTheSameTierTheDraftLacks_mostCommonFirst() {
        List<CatalogPreset> presets = List.of(
                preset(Tier.MEDIUM, "nightlife", "stag-hot-babies-and-pranks", "food-and-drink"),
                preset(Tier.MEDIUM, "extreme", "guns-and-bullets"),
                preset(Tier.MEDIUM, "czech-beer", "food-and-drink", "nightlife"),
                // Another tier's themes never count for this draft.
                preset(Tier.PREMIUM, "luxury"));
        ComposedPlan plan = plan(Tier.MEDIUM, SHOOTING, STEAK);

        Map<Tier, List<String>> gaps = DraftGaps.of(plan, List.of(SHOOTING, STEAK, BEER_SPA), presets);

        assertThat(gaps.get(Tier.MEDIUM)).containsExactly("nightlife", "czech-beer");
    }

    @Test
    void gaps_withoutPresets_areEmpty() {
        assertThat(DraftGaps.of(plan(Tier.BASIC, SHOOTING), List.of(SHOOTING), List.of())).isEmpty();
    }

    @Test
    void gaps_areCappedAtThree() {
        List<CatalogPreset> presets = List.of(preset(Tier.BASIC, "a", "b", "c", "d", "e"));

        assertThat(DraftGaps.of(plan(Tier.BASIC, SHOOTING), List.of(SHOOTING), presets).get(Tier.BASIC))
                .hasSize(DraftGaps.MAX_GAPS);
    }

    private static ComposedPlan plan(Tier tier, CatalogActivity... activities) {
        List<ComposedPlan.ItemResult> items = java.util.Arrays.stream(activities)
                .map(a -> new ComposedPlan.ItemResult(Slot.AFTERNOON, null, a.id(), a.slug(), a.name(), null, 90,
                        new BigDecimal("40.00"), null, new BigDecimal("160.00"), false, "why"))
                .toList();
        return new ComposedPlan(List.of(new ComposedPlan.PackageResult(tier, "t", "t", "d", new BigDecimal("40.00"),
                new BigDecimal("160.00"), ComposedPlan.CURRENCY, 90, List.of(),
                List.of(new ComposedPlan.DayResult(1, "Day 1", "s", items)))), false);
    }

    private static CatalogPreset preset(Tier tier, String... categories) {
        return new CatalogPreset(UUID.randomUUID(), "preset", tier, List.of(UUID.randomUUID()), List.of(categories));
    }

    private static CatalogActivity activity(String name, String... categories) {
        return new CatalogActivity(UUID.randomUUID(), name.toLowerCase(), name, null, 90, true,
                new BigDecimal("40.00"), null, null, List.of(categories));
    }
}
