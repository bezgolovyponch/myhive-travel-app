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

    /** {@code nights} is days - 1 (arrive on day 1, leave on the last); null on rows stored before it existed. */
    public record PackageResult(Tier key, String title, String tagline, String description, BigDecimal pricePerPerson,
                                BigDecimal totalPrice, String currency, int totalDurationMinutes, List<UUID> activityIds,
                                List<DayResult> days, Integer nights) {

        public PackageResult(Tier key, String title, String tagline, String description, BigDecimal pricePerPerson,
                             BigDecimal totalPrice, String currency, int totalDurationMinutes, List<UUID> activityIds,
                             List<DayResult> days) {
            this(key, title, tagline, description, pricePerPerson, totalPrice, currency, totalDurationMinutes,
                    activityIds, days, null);
        }
    }

    public record DayResult(int dayNumber, String title, String summary, List<ItemResult> items) {
    }

    /** {@code includes} is the catalog's own "what is included" text, in the plan's language; never model output. */
    public record ItemResult(Slot slot, String startHint, UUID activityId, String slug, String name, String imageUrl,
                             int durationMinutes, BigDecimal price, BigDecimal minPrice, BigDecimal lineTotal,
                             boolean groupMinApplied, String why, String includes) {

        public ItemResult(Slot slot, String startHint, UUID activityId, String slug, String name, String imageUrl,
                          int durationMinutes, BigDecimal price, BigDecimal minPrice, BigDecimal lineTotal,
                          boolean groupMinApplied, String why) {
            this(slot, startHint, activityId, slug, name, imageUrl, durationMinutes, price, minPrice, lineTotal,
                    groupMinApplied, why, null);
        }
    }
}
