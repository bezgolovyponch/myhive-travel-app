package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VoteSessionResponse {
    private UUID shareToken;
    private String destinationName;
    private String destinationSlug;
    private String status;
    private LocalDateTime expiresAt;
    private long participantCount;
}
