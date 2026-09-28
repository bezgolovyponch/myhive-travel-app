package com.myhive.backend.ai.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One of the three offers a generation produced. {@code key} is the stable tier the frontend keys its
 * badges and analytics on; every price is in euros, and {@code lineTotal} already applies the group
 * minimum, so the UI never recomputes it.
 */
public record PackageDTO(String key, String title, String tagline, String description, BigDecimal pricePerPerson,
                         BigDecimal totalPrice, String currency, int totalDurationMinutes, List<UUID> activityIds,
                         List<DayDTO> days) {

    public record DayDTO(int dayNumber, String title, String summary, List<ItemDTO> items) {
    }

    public record ItemDTO(String slot, String startHint, UUID activityId, String slug, String name, String imageUrl,
                          int durationMinutes, BigDecimal price, BigDecimal minPrice, BigDecimal lineTotal,
                          boolean groupMinApplied, String why) {
    }
}
