package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class VoteBatchRequest {

    @NotNull private UUID voterToken;
    @NotNull @NotEmpty @Valid @Size(max = 100) private List<VoteItem> votes;
    /** Activities this friend recommends adding; optional, same destination as the vote. */
    @Size(max = 50) private List<UUID> recommendedActivityIds;

    @Getter
    @Setter
    public static class VoteItem {
        @NotNull private UUID activityId;
        @NotNull private Boolean liked;
    }
}
