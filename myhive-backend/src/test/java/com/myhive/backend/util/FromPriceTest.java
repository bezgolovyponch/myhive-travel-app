package com.myhive.backend.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FromPriceTest {

    @Test
    void of_takesTheMarginOffAndRoundsToWholeEuros() {
        assertThat(FromPrice.of(new BigDecimal("2140.00"))).isEqualByComparingTo("1926");
        assertThat(FromPrice.of(new BigDecimal("1005.55"))).isEqualByComparingTo("905");
    }

    @Test
    void of_hasNoPriceForAnEmptyPlan() {
        assertThat(FromPrice.of(null)).isNull();
        assertThat(FromPrice.of(BigDecimal.ZERO)).isNull();
    }
}
