package com.myhive.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
    private Boolean seoIndexable;

    /** Raw per-locale overrides — present on the admin (no-locale) view only; localized reads fold them into the fields above. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Map<String, String>> translations;
}
