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
}
