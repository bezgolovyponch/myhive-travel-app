package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VoteActivityResponse {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal minPrice;
    private Integer duration;
    private String imageUrl;
    private String slug;
    private String destinationSlug;
}
