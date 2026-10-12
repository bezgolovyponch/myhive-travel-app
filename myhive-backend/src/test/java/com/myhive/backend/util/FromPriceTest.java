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

    /** Extras are never discounted: the margin is taken from the activities, the extras go on top in full. */
    @Test
    void of_withExtras_takesTheMarginFromTheActivitiesOnly() {
        BigDecimal total = new BigDecimal("530.00");
        BigDecimal extras = new BigDecimal("160.00");

        // (530 - 160) x 0.90 + 160 = 493, against 477 when the extra was inside the margin
        assertThat(FromPrice.of(total, extras)).isEqualByComparingTo("493");
        assertThat(FromPrice.perPerson(total, extras, 10)).isEqualByComparingTo("50");
        assertThat(FromPrice.of(total, null)).isEqualByComparingTo(FromPrice.of(total));
    }
}
