package com.myhive.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The "from €X" a plan shows before the organiser asks the group: the activities less a fixed margin, plus
 * the chosen extras at their full price, in whole euros. Extras are never discounted (owner's rule,
 * 2026-10-12), so the margin is taken from the group total without them. The margin lives only here and is
 * illustrative: it is not announced anywhere, and a booking is charged its own total.
 */
public final class FromPrice {

    private static final BigDecimal FACTOR = new BigDecimal("0.90");

    private FromPrice() {
    }

    /** {@code 2140.00 -> 1926}; null or a non-positive total has no "from" price (null). */
    public static BigDecimal of(BigDecimal groupTotal) {
        return of(groupTotal, BigDecimal.ZERO);
    }

    /**
     * {@code extras} is the part of {@code groupTotal} that is chosen extras: {@code (2140 - 160) x 0.90 + 160}.
     * Null extras count as none; extras larger than the total (never the case for a priced plan) are capped at it.
     */
    public static BigDecimal of(BigDecimal groupTotal, BigDecimal extras) {
        if (groupTotal == null || groupTotal.signum() <= 0) {
            return null;
        }
        BigDecimal full = extras == null || extras.signum() <= 0 ? BigDecimal.ZERO : extras.min(groupTotal);
        return groupTotal.subtract(full).multiply(FACTOR).add(full).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * One traveller's share of {@link #of}, rounded up to a whole euro so the shares never add up to
     * less than the group's "from" price; null when there is no "from" price or no head-count.
     */
    public static BigDecimal perPerson(BigDecimal groupTotal, Integer travelers) {
        return perPerson(groupTotal, BigDecimal.ZERO, travelers);
    }

    public static BigDecimal perPerson(BigDecimal groupTotal, BigDecimal extras, Integer travelers) {
        BigDecimal from = of(groupTotal, extras);
        if (from == null || travelers == null || travelers <= 0) {
            return null;
        }
        return from.divide(BigDecimal.valueOf(travelers), 0, RoundingMode.CEILING);
    }
}
