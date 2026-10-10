package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogExtra;
import com.myhive.backend.entity.PricedExtra;
import com.myhive.backend.util.MoneyMath;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Group-minimum floor for one plan line. Mirrors BookingService.lineTotal,
 * VoteSessionService.flooredLine and tripPricing.js lineTotal — keep all four in sync.
 * A line is {@code max(price × N, minPrice) + Σ extras}: the floor covers the base only, extras are never floored.
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

    /** What the extras cost the group, by the formula every booking path shares. */
    public static BigDecimal extrasTotal(List<? extends PricedExtra> extras, int travelers) {
        return MoneyMath.extrasTotal(extras, travelers);
    }

    /**
     * The extras of {@code offered} that {@code codes} name, priced for the group, in catalog order. A code the
     * activity does not offer (any more) is skipped: an extra is only ever billed at the catalog's price.
     */
    public static List<ComposedPlan.ItemExtra> itemExtras(List<CatalogExtra> offered, List<String> codes,
            int travelers) {
        if (offered == null || codes == null || codes.isEmpty()) {
            return List.of();
        }
        Set<String> wanted = new HashSet<>(codes);
        return offered.stream()
                .filter(extra -> wanted.contains(extra.code()))
                .map(extra -> new ComposedPlan.ItemExtra(extra.code(), extra.name(), extra.price(), extra.pricing(),
                        extra.amount(travelers)))
                .toList();
    }
}
