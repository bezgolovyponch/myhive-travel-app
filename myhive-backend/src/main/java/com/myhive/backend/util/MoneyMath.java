package com.myhive.backend.util;

import com.myhive.backend.entity.PricedExtra;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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

    /** Sum of the selected extras for a group of {@code travelers}; null-safe, scale 2. */
    public static BigDecimal extrasTotal(List<? extends PricedExtra> extras, int travelers) {
        if (extras == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return extras.stream()
                .map(extra -> extra.amount(travelers))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
