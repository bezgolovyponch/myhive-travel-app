package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class TripLeadSyncRequest {
    @NotNull private UUID restoreToken;
    @Min(1) @Max(99) private Integer numberOfTravelers;
    private LocalDate startDate;
    private LocalDate endDate;
    @PositiveOrZero private BigDecimal budget;
    @Size(max = 100_000) private String quizResponsesJson;
    /** null = leave the snapshot untouched; empty list = clear it. */
    @Size(max = 100) @Valid private List<SyncItem> items;

    @Getter
    @Setter
    public static class SyncItem {
        @NotNull private UUID activityId;
        @NotNull private Integer sortOrder;
    }
}
