package com.myhive.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class VoteRequest {
    @NotNull private UUID voterToken;
    @NotNull private UUID activityId;
    @NotNull private Boolean liked;
}
