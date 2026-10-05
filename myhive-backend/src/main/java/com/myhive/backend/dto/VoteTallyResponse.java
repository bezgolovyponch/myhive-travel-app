package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VoteTallyResponse {
    private String status;
    private Instant expiresAt;
    private long participantCount;
    /** Group size the organiser set: "{participantCount} of {numberOfTravelers} voted". */
    private int numberOfTravelers;
    private List<TallyRow> rows;
    /** Activities friends recommended that are not on the ballot, most recommended first. */
    private List<RecommendationRow> recommendations;

    @Getter
    @AllArgsConstructor
    public static class TallyRow {
        private UUID activityId;
        private String name;
        private BigDecimal price;
        private long likeCount;
        /** "No" votes. */
        private long skipCount;
        /** True while the organiser has dropped it; friends who vote now no longer see it. */
        private boolean excluded;
    }

    @Getter
    @AllArgsConstructor
    public static class RecommendationRow {
        private UUID activityId;
        private String name;
        private String slug;
        private String imageUrl;
        private BigDecimal price;
        private BigDecimal minPrice;
        private Integer duration;
        private long recommendationCount;
    }
}
