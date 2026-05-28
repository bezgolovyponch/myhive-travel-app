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
