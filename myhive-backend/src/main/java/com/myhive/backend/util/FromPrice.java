package com.myhive.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The "from €X" a package shows before the organiser asks the group: the group total less a fixed
 * margin, in whole euros. The margin lives only here and is never shown or sent as a percentage.
 */
public final class FromPrice {

    private static final BigDecimal FACTOR = new BigDecimal("0.90");

    private FromPrice() {
    }

    /** {@code 2140.00 -> 1926}; null or a non-positive total has no "from" price (null). */
    public static BigDecimal of(BigDecimal groupTotal) {
        if (groupTotal == null || groupTotal.signum() <= 0) {
            return null;
        }
        return groupTotal.multiply(FACTOR).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * One traveller's share of {@link #of}, rounded up to a whole euro so the shares never add up to
     * less than the group's "from" price; null when there is no "from" price or no head-count.
     */
    public static BigDecimal perPerson(BigDecimal groupTotal, Integer travelers) {
        BigDecimal from = of(groupTotal);
        if (from == null || travelers == null || travelers <= 0) {
            return null;
        }
        return from.divide(BigDecimal.valueOf(travelers), 0, RoundingMode.CEILING);
    }
}
