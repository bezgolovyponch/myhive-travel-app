package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDTO {
    private UUID id;
    private UUID destinationId;
    private String destinationName;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer duration;
    private String category;
    private String imageUrl;
}
