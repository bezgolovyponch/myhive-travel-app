package com.myhive.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** The organiser adds an activity (usually one the group recommended) to a running vote. */
@Getter
@Setter
public class VoteSessionActivityAddRequest {
    @NotNull private UUID activityId;
}
