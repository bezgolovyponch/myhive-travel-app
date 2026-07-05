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
    private List<TallyRow> rows;

    @Getter
    @AllArgsConstructor
    public static class TallyRow {
        private UUID activityId;
        private String name;
        private BigDecimal price;
        private long likeCount;
    }
}
