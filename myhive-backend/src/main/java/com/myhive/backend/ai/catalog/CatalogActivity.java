package com.myhive.backend.ai.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * {@code includes} is the activity's "what is included" text in the snapshot's language, or null.
 * {@code extras} are the optional add-ons it offers, in catalog order; never null (a snapshot stored
 * before extras existed reads as offering none).
 */
public record CatalogActivity(UUID id, String slug, String name, String oneLine, int durationMinutes,
                              boolean durationKnown, BigDecimal price, BigDecimal minPrice, String imageUrl,
                              List<String> categorySlugs, String includes, List<CatalogExtra> extras) {

    public CatalogActivity {
        extras = extras == null ? List.of() : List.copyOf(extras);
    }

    public CatalogActivity(UUID id, String slug, String name, String oneLine, int durationMinutes,
                           boolean durationKnown, BigDecimal price, BigDecimal minPrice, String imageUrl,
                           List<String> categorySlugs, String includes) {
        this(id, slug, name, oneLine, durationMinutes, durationKnown, price, minPrice, imageUrl, categorySlugs,
                includes, List.of());
    }

    public CatalogActivity(UUID id, String slug, String name, String oneLine, int durationMinutes,
                           boolean durationKnown, BigDecimal price, BigDecimal minPrice, String imageUrl,
                           List<String> categorySlugs) {
        this(id, slug, name, oneLine, durationMinutes, durationKnown, price, minPrice, imageUrl, categorySlugs, null);
    }
}
