package com.myhive.backend.ai.plan;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Group-minimum floor for one plan line. Mirrors BookingService.lineTotal,
 * VoteSessionService.flooredLine and tripPricing.js lineTotal — keep all four in sync.
 */
public final class PlanPricer {

    private PlanPricer() {}

    public static BigDecimal lineTotal(BigDecimal price, BigDecimal minPrice, int travelers) {
        BigDecimal raw = price.multiply(BigDecimal.valueOf(travelers));
        if (minPrice == null || minPrice.signum() <= 0) {
            return raw.setScale(2, RoundingMode.HALF_UP);
        }
        return raw.max(minPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal perPerson(BigDecimal total, int travelers) {
        return total.divide(BigDecimal.valueOf(travelers), 2, RoundingMode.HALF_UP);
    }
}
