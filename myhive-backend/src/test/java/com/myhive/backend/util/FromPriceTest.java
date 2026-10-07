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

    @Test
    void perPerson_isOneTravellersShareRoundedUp() {
        // 2140 -> from 1926; 1926 / 10 = 192.6 -> 193, so ten shares cover the group's price.
        assertThat(FromPrice.perPerson(new BigDecimal("2140.00"), 10)).isEqualByComparingTo("193");
        assertThat(FromPrice.perPerson(new BigDecimal("1000.00"), 9)).isEqualByComparingTo("100");
    }

    @Test
    void perPerson_needsAPriceAndAHeadCount() {
        assertThat(FromPrice.perPerson(null, 10)).isNull();
        assertThat(FromPrice.perPerson(new BigDecimal("2140.00"), null)).isNull();
        assertThat(FromPrice.perPerson(new BigDecimal("2140.00"), 0)).isNull();
    }
}
