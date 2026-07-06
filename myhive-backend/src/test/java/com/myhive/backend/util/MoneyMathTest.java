package com.myhive.backend.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyMathTest {

    @Test
    void applyDiscountPct_typicalDiscount_returnsDiscountedAmountAtScale2() {
        BigDecimal expected = new BigDecimal("87.50");

        BigDecimal result = MoneyMath.applyDiscountPct(new BigDecimal("100.00"), new BigDecimal("12.50"));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void applyDiscountPct_resultNeedsRounding_roundsHalfUp() {
        // 33.33 * 0.90 = 29.997 -> 30.00
        BigDecimal expected = new BigDecimal("30.00");

        BigDecimal result = MoneyMath.applyDiscountPct(new BigDecimal("33.33"), new BigDecimal("10"));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void applyDiscountPct_halfCentBoundary_roundsUp() {
        // 10.05 * 0.50 = 5.025 -> 5.03
        BigDecimal expected = new BigDecimal("5.03");

        BigDecimal result = MoneyMath.applyDiscountPct(new BigDecimal("10.05"), new BigDecimal("50"));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void applyDiscountPct_nullPct_returnsAmountAtScale2() {
        BigDecimal expected = new BigDecimal("42.00");

        BigDecimal result = MoneyMath.applyDiscountPct(new BigDecimal("42"), null);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void applyDiscountPct_zeroPct_returnsAmountAtScale2() {
        BigDecimal expected = new BigDecimal("42.00");

        BigDecimal result = MoneyMath.applyDiscountPct(new BigDecimal("42"), BigDecimal.ZERO);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void applyDiscountPct_fullDiscount_returnsZeroAtScale2() {
        BigDecimal expected = new BigDecimal("0.00");

        BigDecimal result = MoneyMath.applyDiscountPct(new BigDecimal("99.99"), new BigDecimal("100"));

        assertThat(result).isEqualTo(expected);
    }
}
