package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultActivityDTO {

    private UUID activityId;
    private String name;          // snapshot
    private BigDecimal price;     // snapshot, per-person
    // Live catalog value (like slug/imageUrl below): the floor that will apply at booking
    // time comes from the catalog anyway, so the result mirrors it rather than snapshotting.
    private BigDecimal minPrice;
    private long likeCount;
    private long skipCount;
    // Live presentation fields (slug, imageUrl, etc.) — safe to read live because the
    // snapshot only protects the deal (name + price) the group voted on.
    private String slug;
    private String destinationSlug;
    private String imageUrl;
    private Integer duration;
    private String description;
    private String includes;
}
