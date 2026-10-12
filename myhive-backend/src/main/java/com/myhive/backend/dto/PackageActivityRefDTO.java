package com.myhive.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
    /**
     * The optional extras the activity offers, localised like its name; read-only (ignored on a write).
     * A package's activity takes extras like any other: the cart needs to know which ones there are.
     */
    private List<ActivityExtraDTO> extras = new ArrayList<>();

    public PackageActivityRefDTO(UUID activityId, Integer position, String slug, String name, BigDecimal price,
                                 Integer duration, String imageUrl) {
        this(activityId, position, slug, name, price, duration, imageUrl, new ArrayList<>());
    }
}
