package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VotePoolActivityDTO {

    private UUID activityId;
    private String name;
    private BigDecimal price;
    private BigDecimal minPrice;
    private String imageUrl;
    private String slug;
    private String destinationSlug;
    private List<String> categories;
    private String description;
    private Integer duration;
}
