package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * The plan to price: one {@link Item} per line, where lines that name the same package are that package
 * and get its catalog discount. {@code activityIds} is the shape before packages were priced (every line
 * standalone); it is read when {@code items} is absent so a browser on the old bundle keeps its price
 * during a deploy, and goes with the next release.
 */
@Getter
@Setter
public class PriceQuoteRequest {
    @Valid @Size(max = 50) private List<@NotNull Item> items;
    @Deprecated @Size(max = 50) private List<@NotNull UUID> activityIds;
    @NotNull @Min(1) @Max(50) private Integer travelers;

    @Getter
    @Setter
    public static class Item {
        @NotNull private UUID activityId;
        /** The package this line was added as part of; null for a standalone activity. */
        private UUID packageId;
    }
}
