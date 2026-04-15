package com.myhive.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    @Size(max = 280, message = "Slug must be at most 280 characters")
    private String slug;

    @NotNull(message = "Destination ID is required")
    private UUID destinationId;

    private String destinationName;
    private String destinationSlug;

    @NotBlank(message = "Activity name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    private Integer duration;

    @Size(max = 100, message = "Category must be at most 100 characters")
    private String category;

    @Size(max = 500, message = "Image URL must be at most 500 characters")
    private String imageUrl;

    private String includes;
}
