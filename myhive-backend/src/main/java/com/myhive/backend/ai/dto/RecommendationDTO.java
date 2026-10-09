package com.myhive.backend.ai.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One activity the chat recommends for the draft, ready to add with one tap: catalog facts only, in the
 * session's language. {@code pricePerPerson} is the line for the whole group (with its group minimum)
 * divided by the head-count, in whole euros rounded up; {@code durationMinutes} is null when unknown.
 * {@code fromPricePerPerson} is the same share of the line's "from" price - what adding the activity puts
 * on the plan's own "from ... / person", so the two figures on screen add up.
 */
public record RecommendationDTO(UUID activityId, String name, String oneLine, Integer durationMinutes,
                                BigDecimal pricePerPerson, BigDecimal fromPricePerPerson, String imageUrl) {
}
