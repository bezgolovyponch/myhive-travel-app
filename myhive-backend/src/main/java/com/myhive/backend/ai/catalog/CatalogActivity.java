package com.myhive.backend.ai.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** {@code includes} is the activity's "what is included" text in the snapshot's language, or null. */
public record CatalogActivity(UUID id, String slug, String name, String oneLine, int durationMinutes,
                              boolean durationKnown, BigDecimal price, BigDecimal minPrice, String imageUrl,
                              List<String> categorySlugs, String includes) {

    public CatalogActivity(UUID id, String slug, String name, String oneLine, int durationMinutes,
                           boolean durationKnown, BigDecimal price, BigDecimal minPrice, String imageUrl,
                           List<String> categorySlugs) {
        this(id, slug, name, oneLine, durationMinutes, durationKnown, price, minPrice, imageUrl, categorySlugs, null);
    }
}
