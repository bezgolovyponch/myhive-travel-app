package com.myhive.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DestinationDTO {
    private UUID id;
    @Size(max = 280, message = "Slug must be at most 280 characters")
    private String slug;
    @NotBlank(message = "Name is required")
    private String name;
    private String description;
    private String country;
    private String city;
    private String imageUrl;
    private BigDecimal rating;
    private Integer activityCount;
    private List<CategoryDTO> assignedCategories;
}
