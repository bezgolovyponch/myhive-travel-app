package com.myhive.backend.ai.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CatalogActivity(UUID id, String slug, String name, String oneLine, int durationMinutes,
                              boolean durationKnown, BigDecimal price, BigDecimal minPrice, String imageUrl,
                              List<String> categorySlugs) {
}
