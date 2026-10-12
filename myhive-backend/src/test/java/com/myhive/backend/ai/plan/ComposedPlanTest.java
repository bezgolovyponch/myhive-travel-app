package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.entity.ExtraPricing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ComposedPlanTest {

    @Test
    void extrasTotal_sumsTheChosenExtrasOfEveryItemAcrossDays() {
        BigDecimal expectedTotal = new BigDecimal("400.00");
        ComposedPlan.PackageResult pkg = pkg(List.of(
                day(1, List.of(
                        item("River Boat Cruise", List.of(
                                extra("private-show", ExtraPricing.PER_GROUP, "250.00"),
                                extra("open-bar", ExtraPricing.PER_PERSON, "100.00"))),
                        item("Club Crawl", List.of()))),
                day(2, List.of(item("Karting", List.of(extra("photographer", ExtraPricing.PER_PERSON, "50.00")))))));

        assertThat(pkg.extrasTotal()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void extrasTotal_isZeroWhenNothingWasChosen() {
        ComposedPlan.PackageResult pkg = pkg(List.of(day(1, List.of(item("Beer Bike", List.of())))));

        assertThat(pkg.extrasTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private static ComposedPlan.PackageResult pkg(List<ComposedPlan.DayResult> days) {
        return new ComposedPlan.PackageResult(Tier.BASIC, "title", "tagline", "description", new BigDecimal("40.00"),
                new BigDecimal("160.00"), ComposedPlan.CURRENCY, 180, List.of(), days);
    }

    private static ComposedPlan.DayResult day(int dayNumber, List<ComposedPlan.ItemResult> items) {
        return new ComposedPlan.DayResult(dayNumber, "Day " + dayNumber, "summary", items);
    }

    private static ComposedPlan.ItemResult item(String name, List<ComposedPlan.ItemExtra> extras) {
        return new ComposedPlan.ItemResult(Slot.EVENING, "19:00", UUID.randomUUID(), "slug", name, null, 90,
                new BigDecimal("40.00"), null, new BigDecimal("160.00"), false, "why", null, extras);
    }

    private static ComposedPlan.ItemExtra extra(String code, ExtraPricing pricing, String total) {
        return new ComposedPlan.ItemExtra(code, code, new BigDecimal("25.00"), pricing, new BigDecimal(total));
    }
}
