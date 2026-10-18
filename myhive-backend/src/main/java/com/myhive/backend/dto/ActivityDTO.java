package com.myhive.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDTO {
    private UUID id;
    @Size(max = 280, message = "Slug must be at most 280 characters")
    private String slug;

    /** Hand-written German URL slug. On admin write: null = unchanged, blank = cleared. */
    @Size(max = 280, message = "German slug must be at most 280 characters")
    private String slugDe;

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

    @PositiveOrZero(message = "Minimum price must not be negative")
    private BigDecimal minPrice;

    private Integer duration;

    private Integer featuredWeight;

    private Boolean featured;

    private List<CategoryDTO> categories = new ArrayList<>();

    private List<UUID> categoryIds = new ArrayList<>();

    @Size(max = 500, message = "Image URL must be at most 500 characters")
    private String imageUrl;

    private String includes;

    private Boolean seoIndexable;

    /** The SEO hub this activity belongs to. On admin write: null = unchanged. */
    private UUID primaryCategoryId;

    /** Read-only, localized; ignored on write. */
    private PrimaryCategoryDTO primaryCategory;

    private LocalDateTime updatedAt;

    /** Raw per-locale overrides — present on the admin (no-locale) view only; localized reads fold them into the fields above. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Map<String, String>> translations;

    /** Optional add-ons. On admin write: null = unchanged, [] = clear. Reads always return a list. */
    private List<ActivityExtraDTO> extras;
}
