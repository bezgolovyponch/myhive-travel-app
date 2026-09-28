package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PackagesViewTest {

    @Test
    void render_listsEachPackageDayAndSlotInOrder() {
        String expectedBasicLine = "BASIC: day 1 [EVENING Beer Bike, NIGHT Club Crawl]; day 2 [MORNING Karting]";
        String expectedPremiumLine = "PREMIUM: day 1 [AFTERNOON Shooting Range]";
        ComposedPlan plan = new ComposedPlan(List.of(
                pkg(Tier.BASIC, List.of(
                        day(1, List.of(item("Beer Bike", Slot.EVENING), item("Club Crawl", Slot.NIGHT))),
                        day(2, List.of(item("Karting", Slot.MORNING))))),
                pkg(Tier.PREMIUM, List.of(day(1, List.of(item("Shooting Range", Slot.AFTERNOON)))))), false);

        String view = PackagesView.render(plan);

        assertThat(view).isEqualTo(expectedBasicLine + "\n" + expectedPremiumLine);
    }

    @Test
    void render_withoutPackages_isEmpty() {
        assertThat(PackagesView.render(new ComposedPlan(List.of(), false))).isEmpty();
    }

    @Test
    void catalogNames_areSortedAndDistinct() {
        List<String> expectedNames = List.of("Beer Bike", "Karting");
        List<CatalogActivity> catalog = List.of(catalogActivity("Karting"), catalogActivity("Beer Bike"),
                catalogActivity("Karting"));

        assertThat(PackagesView.catalogNames(catalog)).containsExactlyElementsOf(expectedNames);
    }

    private static ComposedPlan.PackageResult pkg(Tier key, List<ComposedPlan.DayResult> days) {
        return new ComposedPlan.PackageResult(key, "title", "tagline", "description", new BigDecimal("40.00"),
                new BigDecimal("160.00"), ComposedPlan.CURRENCY, 180, List.of(), days);
    }

    private static ComposedPlan.DayResult day(int dayNumber, List<ComposedPlan.ItemResult> items) {
        return new ComposedPlan.DayResult(dayNumber, "Day " + dayNumber, "summary", items);
    }

    private static ComposedPlan.ItemResult item(String name, Slot slot) {
        return new ComposedPlan.ItemResult(slot, "19:00", UUID.randomUUID(), "slug", name, null, 90,
                new BigDecimal("40.00"), null, new BigDecimal("160.00"), false, "why");
    }

    private static CatalogActivity catalogActivity(String name) {
        return new CatalogActivity(UUID.randomUUID(), "slug", name, "one line", 90, true, new BigDecimal("40.00"),
                null, null, List.of("nightlife"));
    }
}
