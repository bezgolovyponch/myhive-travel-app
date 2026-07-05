package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VoteSessionResponse {
    private UUID shareToken;
    private String destinationName;
    private String destinationSlug;
    private String status;
    private Instant expiresAt;
    private long participantCount;
    private int numberOfTravelers;
    private UUID managerToken;
    private String voteMode;
}
