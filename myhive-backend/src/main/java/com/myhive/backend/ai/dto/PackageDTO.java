package com.myhive.backend.ai.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One of the three offers a generation produced. {@code key} is the stable tier the frontend keys its
 * badges and analytics on; every price is in euros, and {@code lineTotal} already applies the group
 * minimum, so the UI never recomputes it. {@code fromPrice} is the "from €X" shown on the package: the
 * group total less the fixed margin in {@link com.myhive.backend.util.FromPrice}, in whole euros.
 * {@code nights} is days - 1, null for packages stored before it existed; an item's {@code includes} is
 * the catalog's "what is included" text, null when it has none.
 */
public record PackageDTO(String key, String title, String tagline, String description, BigDecimal pricePerPerson,
                         BigDecimal totalPrice, BigDecimal fromPrice, String currency, int totalDurationMinutes,
                         List<UUID> activityIds, List<DayDTO> days, Integer nights) {

    public record DayDTO(int dayNumber, String title, String summary, List<ItemDTO> items) {
    }

    public record ItemDTO(String slot, String startHint, UUID activityId, String slug, String name, String imageUrl,
                          int durationMinutes, BigDecimal price, BigDecimal minPrice, BigDecimal lineTotal,
                          boolean groupMinApplied, String why, String includes) {
    }
}
