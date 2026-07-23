package com.myhive.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TripLeadRestoreResponse(
        UUID leadId,
        String email,
        UUID destinationId,
        String destinationSlug,
        String destinationName,
        Integer numberOfTravelers,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal budget,
        String quizResponsesJson,
        List<RestoreItem> items) {

    public record RestoreItem(
            UUID activityId,
            String name,
            BigDecimal price,
            BigDecimal minPrice,
            String imageUrl,
            Integer duration,
            String slug,
            String destinationSlug,
            String description,
            String includes) {
    }
}
