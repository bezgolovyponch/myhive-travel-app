package com.myhive.backend.ai.dto;

import java.util.List;

/**
 * One "+ Add ..." tag under the plan: a kind of activity (a catalog category, named in the session's
 * language) and the activities of that kind the package does not hold yet, most wanted first. A tap asks
 * "which one?" with {@code options} as the answers; the chosen one is shown as a card with Add.
 */
public record DraftGapDTO(String categorySlug, String name, List<RecommendationDTO> options) {
}
