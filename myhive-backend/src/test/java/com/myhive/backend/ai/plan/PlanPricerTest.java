package com.myhive.backend.ai.plan;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PlanPricerTest {

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
}
