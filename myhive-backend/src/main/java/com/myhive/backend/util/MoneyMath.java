package com.myhive.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money arithmetic shared by booking totals, package pricing and confirmation emails.
 * All call sites must use the same formula so displayed and stored amounts never drift.
 */
public final class MoneyMath {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private MoneyMath() {
    }

    /**
     * Applies a percent discount to an amount and rounds to cents (HALF_UP).
     * A {@code null} discount is treated as no discount.
     */
    public static BigDecimal applyDiscountPct(BigDecimal amount, BigDecimal discountPct) {
        BigDecimal pct = discountPct == null ? BigDecimal.ZERO : discountPct;
        return amount.multiply(HUNDRED.subtract(pct))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
