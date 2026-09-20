package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** The validated, priced result that is stored in ai_generations.result and served to the frontend. */
public record ComposedPlan(List<PackageResult> packages, boolean degraded) {

    public static final String CURRENCY = "EUR";

    public String currencyOf(PackageResult pkg) {
        return pkg.currency();
    }

    public record PackageResult(Tier key, String title, String tagline, String description, BigDecimal pricePerPerson,
                                BigDecimal totalPrice, String currency, int totalDurationMinutes, List<UUID> activityIds,
                                List<DayResult> days) {
    }

    public record DayResult(int dayNumber, String title, String summary, List<ItemResult> items) {
    }

    public record ItemResult(Slot slot, String startHint, UUID activityId, String slug, String name, String imageUrl,
                             int durationMinutes, BigDecimal price, BigDecimal minPrice, BigDecimal lineTotal,
                             boolean groupMinApplied, String why) {
    }
}
