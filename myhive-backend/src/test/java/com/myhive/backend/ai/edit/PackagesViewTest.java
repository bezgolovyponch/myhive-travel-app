package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogExtra;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.entity.ExtraPricing;
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
    void render_withAWorkingPackage_showsTheTripDraftAlone() {
        ComposedPlan plan = new ComposedPlan(List.of(
                pkg(Tier.BASIC, List.of(day(1, List.of(item("Beer Bike", Slot.EVENING))))),
                pkg(Tier.MEDIUM, List.of(day(1, List.of(item("Karting", Slot.AFTERNOON))))),
                pkg(Tier.PREMIUM, List.of(day(1, List.of(item("Shooting Range", Slot.AFTERNOON)))))), false);

        assertThat(PackagesView.render(plan, Tier.MEDIUM)).isEqualTo("MEDIUM: day 1 [AFTERNOON Karting]");
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

    @Test
    void render_namesTheChosenExtrasOfAnItem() {
        ComposedPlan.ItemResult cruise = new ComposedPlan.ItemResult(Slot.EVENING, "19:00", UUID.randomUUID(), "slug",
                "River Boat Cruise", null, 90, new BigDecimal("40.00"), null, new BigDecimal("435.00"), false, "why",
                null, List.of(
                        new ComposedPlan.ItemExtra("private-show", "Private show", new BigDecimal("250.00"),
                                ExtraPricing.PER_GROUP, new BigDecimal("250.00")),
                        new ComposedPlan.ItemExtra("open-bar", "Open bar", new BigDecimal("25.00"),
                                ExtraPricing.PER_PERSON, new BigDecimal("100.00"))));
        ComposedPlan plan = new ComposedPlan(List.of(pkg(Tier.BASIC,
                List.of(day(1, List.of(item("Karting", Slot.AFTERNOON), cruise))))), false);

        assertThat(PackagesView.render(plan)).isEqualTo(
                "BASIC: day 1 [AFTERNOON Karting, EVENING River Boat Cruise (+ Private show, Open bar)]");
    }

    @Test
    void extrasCatalog_listsTheExtrasPerActivity_orNone() {
        String expectedLine = "River Boat Cruise -> Private show, Open bar; Tiki Boat -> Photographer";
        CatalogActivity cruise = withExtras("River Boat Cruise",
                new CatalogExtra("private-show", "Private show", new BigDecimal("250.00"), ExtraPricing.PER_GROUP),
                new CatalogExtra("open-bar", "Open bar", new BigDecimal("25.00"), ExtraPricing.PER_PERSON));
        CatalogActivity tiki = withExtras("Tiki Boat",
                new CatalogExtra("photographer", "Photographer", new BigDecimal("12.50"), ExtraPricing.PER_PERSON));

        assertThat(PackagesView.extrasCatalog(List.of(cruise, catalogActivity("Karting"), tiki)))
                .isEqualTo(expectedLine);
        assertThat(PackagesView.extrasCatalog(List.of(catalogActivity("Karting")))).isEqualTo("none");
        assertThat(PackagesView.extrasCatalog(null)).isEqualTo("none");
    }

    private static CatalogActivity withExtras(String name, CatalogExtra... extras) {
        return new CatalogActivity(UUID.randomUUID(), "slug", name, "one line", 90, true, new BigDecimal("40.00"),
                null, null, List.of("boat"), null, List.of(extras));
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
