package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DraftGapsTest {

    private static final CatalogActivity AK47 = activity("AK-47", "guns-and-bullets");
    private static final CatalogActivity STEAK = activity("Steak & Show", "food-and-drink", "strip");
    private static final CatalogActivity KARTING = activity("Karting", "extreme");
    private static final CatalogActivity SNIPER = activity("Sniper", "guns-and-bullets", "extreme");
    private static final CatalogActivity RIBS = activity("Ribs", "food-and-drink");
    /** Most wanted first, as the snapshot delivers it. */
    private static final List<CatalogActivity> CATALOG = List.of(AK47, STEAK, KARTING, SNIPER, RIBS);

    @Test
    void kinds_comeInTheCatalogsOrder_eachWithWhatThePackageLacks() {
        List<DraftGaps.Kind> kinds = DraftGaps.of(plan(Tier.MEDIUM, STEAK), CATALOG).get(Tier.MEDIUM);

        assertThat(kinds).extracting(DraftGaps.Kind::categorySlug)
                .containsExactly("guns-and-bullets", "extreme", "food-and-drink");
        assertThat(names(kinds.get(0))).containsExactly("AK-47", "Sniper");
        assertThat(names(kinds.get(1))).containsExactly("Karting", "Sniper");
        // Steak is in the package: its own kinds only offer what is left, and "strip" has nothing left.
        assertThat(names(kinds.get(2))).containsExactly("Ribs");
    }

    @Test
    void anActivityFiledUnderTwoKinds_leadsOnlyTheFirst() {
        List<DraftGaps.Kind> kinds = DraftGaps.of(plan(Tier.BASIC, AK47, KARTING), List.of(SNIPER, RIBS))
                .get(Tier.BASIC);

        // Sniper opens "guns-and-bullets"; "extreme" would open on it again and has nothing else.
        assertThat(kinds).extracting(DraftGaps.Kind::categorySlug).containsExactly("guns-and-bullets", "food-and-drink");
    }

    @Test
    void kinds_areCappedAtSix_andTheirOptionsAtFour() {
        List<CatalogActivity> catalog = new ArrayList<>();
        for (int kind = 0; kind < 8; kind++) {
            for (int n = 0; n < 6; n++) {
                catalog.add(activity("a" + kind + n, "kind-" + kind));
            }
        }

        List<DraftGaps.Kind> kinds = DraftGaps.of(plan(Tier.BASIC, AK47), catalog).get(Tier.BASIC);

        assertThat(kinds).hasSize(DraftGaps.MAX_GAPS);
        assertThat(kinds).allSatisfy(kind -> assertThat(kind.options()).hasSize(DraftGaps.MAX_OPTIONS));
    }

    @Test
    void withoutAPlanOrACatalog_thereIsNothingToOffer() {
        assertThat(DraftGaps.of(null, CATALOG)).isEmpty();
        assertThat(DraftGaps.of(plan(Tier.BASIC, AK47), List.of())).isEmpty();
    }

    private static List<String> names(DraftGaps.Kind kind) {
        return kind.options().stream().map(CatalogActivity::name).toList();
    }

    private static ComposedPlan plan(Tier tier, CatalogActivity... activities) {
        List<ComposedPlan.ItemResult> items = Arrays.stream(activities)
                .map(a -> new ComposedPlan.ItemResult(Slot.AFTERNOON, null, a.id(), a.slug(), a.name(), null, 90,
                        new BigDecimal("40.00"), null, new BigDecimal("160.00"), false, "why"))
                .toList();
        return new ComposedPlan(List.of(new ComposedPlan.PackageResult(tier, "t", "t", "d", new BigDecimal("40.00"),
                new BigDecimal("160.00"), ComposedPlan.CURRENCY, 90, List.of(),
                List.of(new ComposedPlan.DayResult(1, "Day 1", "s", items)))), false);
    }

    private static CatalogActivity activity(String name, String... categories) {
        return new CatalogActivity(UUID.randomUUID(), name.toLowerCase(), name, null, 90, true,
                new BigDecimal("40.00"), null, null, List.of(categories));
    }
}
