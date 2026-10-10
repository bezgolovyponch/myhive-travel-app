package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogExtra;
import com.myhive.backend.entity.ExtraPricing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanPricerTest {

    private static final CatalogExtra PRIVATE_SHOW =
            new CatalogExtra("private-show", "Private show", new BigDecimal("250.00"), ExtraPricing.PER_GROUP);
    private static final CatalogExtra OPEN_BAR =
            new CatalogExtra("open-bar", "Open bar", new BigDecimal("25.00"), ExtraPricing.PER_PERSON);

    @Test
    void lineTotal_floorsToGroupMinimum() {
        BigDecimal expectedFloor = new BigDecimal("300.00");
        assertThat(PlanPricer.lineTotal(new BigDecimal("30.00"), expectedFloor, 8)).isEqualByComparingTo(expectedFloor);
        assertThat(PlanPricer.lineTotal(new BigDecimal("50.00"), expectedFloor, 8)).isEqualByComparingTo("400.00");
        assertThat(PlanPricer.lineTotal(new BigDecimal("50.00"), null, 3)).isEqualByComparingTo("150.00");
        assertThat(PlanPricer.lineTotal(new BigDecimal("50.00"), BigDecimal.ZERO, 3)).isEqualByComparingTo("150.00");
    }

    @Test
    void perPerson_roundsHalfUpToCents() {
        assertThat(PlanPricer.perPerson(new BigDecimal("100.00"), 3)).isEqualByComparingTo("33.33");
        assertThat(PlanPricer.perPerson(new BigDecimal("100.01"), 6)).isEqualByComparingTo("16.67");
    }

    @Test
    void itemExtras_pricesPerPersonTimesTravelers_inCatalogOrder_andSkipsUnknownCodes() {
        int travelers = 6;

        List<ComposedPlan.ItemExtra> extras = PlanPricer.itemExtras(List.of(PRIVATE_SHOW, OPEN_BAR),
                List.of("open-bar", "stripper", "private-show"), travelers);

        assertThat(extras).containsExactly(
                new ComposedPlan.ItemExtra("private-show", "Private show", new BigDecimal("250.00"),
                        ExtraPricing.PER_GROUP, new BigDecimal("250.00")),
                new ComposedPlan.ItemExtra("open-bar", "Open bar", new BigDecimal("25.00"),
                        ExtraPricing.PER_PERSON, new BigDecimal("150.00")));
    }

    @Test
    void itemExtras_withNoCodesOrNoOffer_isEmpty() {
        assertThat(PlanPricer.itemExtras(List.of(PRIVATE_SHOW), List.of(), 4)).isEmpty();
        assertThat(PlanPricer.itemExtras(List.of(), List.of("private-show"), 4)).isEmpty();
    }
}
