package com.myhive.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageActivityRefDTO {
    @NotNull
    private UUID activityId;

    @NotNull
    @PositiveOrZero
    private Integer position;

    private String slug;
    private String name;
    private BigDecimal price;
    private Integer duration;
    private String imageUrl;
}
