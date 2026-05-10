package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class VoteBatchRequest {

    @NotNull private UUID voterToken;
    @NotNull @NotEmpty @Valid private List<VoteItem> votes;

    @Getter
    @Setter
    public static class VoteItem {
        @NotNull private UUID activityId;
        @NotNull private Boolean liked;
    }
}
