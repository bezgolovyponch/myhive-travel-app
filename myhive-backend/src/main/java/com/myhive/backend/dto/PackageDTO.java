package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageDTO {
    private UUID id;

    @Size(max = 280)
    private String slug;

    @NotNull(message = "Destination ID is required")
    private UUID destinationId;
    private String destinationName;
    private String destinationSlug;

    @NotBlank(message = "Package name is required")
    @Size(max = 255)
    private String name;

    private String description;

    @Size(max = 500)
    private String imageUrl;

    private String includes;

    private Integer duration;

    @NotNull(message = "Discount percent is required")
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private BigDecimal discountPct;

    @Valid
    private List<PackageActivityRefDTO> activities = new ArrayList<>();

    private List<CategoryDTO> categories = new ArrayList<>();
    private List<UUID> categoryIds = new ArrayList<>();

    private BigDecimal originalPrice;
    private BigDecimal discountedPrice;
    private BigDecimal savings;
}
