package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogPreset;
import com.myhive.backend.ai.model.Tier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PresetThemesTest {

    private static final CatalogActivity SHOOTING = activity("Shooting");
    private static final CatalogActivity STEAK = activity("Steak & Show");
    private static final CatalogActivity KARTING = activity("Karting");
    private static final CatalogActivity QUADS = activity("Quads");
    private static final CatalogActivity BEER_SPA = activity("Beer Spa");
    /** Most popular first, as the snapshot delivers it. */
    private static final List<CatalogActivity> CATALOG = List.of(SHOOTING, BEER_SPA, STEAK, KARTING, QUADS);

    private static final List<CatalogPreset> PRESETS = List.of(
            preset("Classic Stag: Essential", Tier.BASIC, SHOOTING),
            preset("Classic Stag: Legend", Tier.PREMIUM, SHOOTING, STEAK, KARTING),
            preset("Adrenaline: Starter", Tier.BASIC, KARTING),
            preset("Adrenaline: All Out", Tier.PREMIUM, KARTING, QUADS),
            preset("Beer & Food: Taster", Tier.BASIC, BEER_SPA));

    @Test
    void themes_areTheFamiliesThePlanWasNotBuiltFrom_mostPopularFirst() {
        ComposedPlan plan = plan(SHOOTING, STEAK);

        // Beer Spa sits above Karting and Quads in the catalog, so its family is offered first.
        assertThat(PresetThemes.of(plan, CATALOG, PRESETS)).containsExactly("Beer & Food", "Adrenaline");
    }

    @Test
    void themes_followThePlan_whenItIsRebuiltFromAnotherFamily() {
        assertThat(PresetThemes.of(plan(KARTING, QUADS), CATALOG, PRESETS))
                .containsExactly("Beer & Food", "Classic Stag");
    }

    @Test
    void themes_withoutAPlanOrPresets_areEmpty() {
        assertThat(PresetThemes.of(null, CATALOG, PRESETS)).isEmpty();
        assertThat(PresetThemes.of(plan(SHOOTING), CATALOG, List.of())).isEmpty();
    }

    @Test
    void family_isTheNameBeforeTheLevel() {
        assertThat(PresetThemes.family("Beer & Food: Brewmaster")).isEqualTo("Beer & Food");
        assertThat(PresetThemes.family("Party Weekend")).isEqualTo("Party Weekend");
    }

    private static ComposedPlan plan(CatalogActivity... activities) {
        List<UUID> ids = Arrays.stream(activities).map(CatalogActivity::id).toList();
        return new ComposedPlan(List.of(new ComposedPlan.PackageResult(Tier.MEDIUM, "t", "t", "d",
                new BigDecimal("40.00"), new BigDecimal("160.00"), ComposedPlan.CURRENCY, 90, ids, List.of())), false);
    }

    private static CatalogPreset preset(String name, Tier tier, CatalogActivity... activities) {
        return new CatalogPreset(UUID.randomUUID(), name, tier,
                Arrays.stream(activities).map(CatalogActivity::id).toList(), List.of());
    }

    private static CatalogActivity activity(String name) {
        return new CatalogActivity(UUID.randomUUID(), name.toLowerCase(), name, null, 90, true,
                new BigDecimal("40.00"), null, null, List.of());
    }
}
