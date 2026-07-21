package com.myhive.backend.service.activity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal record types shared between the CSV import helpers
 * (parser, validator, differ) and the orchestrator. Package-private
 * by design — these are not part of the public API.
 */
final class CsvImportTypes {
    private CsvImportTypes() {
    }
}

record RawRow(int csvRowNumber, String[] values, Map<String, Integer> headerIndex) {
    String get(String column) {
        Integer idx = headerIndex.get(column);
        if (idx == null || idx >= values.length) {
            return "";
        }
        return values[idx] == null ? "" : values[idx].trim();
    }

    boolean hasColumn(String column) {
        return headerIndex.containsKey(column);
    }
}

record ValidatedRow(
        int csvRowNumber,
        UUID activityId,
        String name,
        String description,
        BigDecimal price,
        Integer duration,
        List<String> categorySlugs,
        String includes,
        // Optional mutable field: null means "column absent from CSV; do not update".
        // Non-null means "update featured_weight to this value" (0 when cell is blank).
        Integer featuredWeight,
        // Optional mutable field: null means "column absent from CSV; do not update".
        // BigDecimal.ZERO means "blank cell -> clear the minimum". Otherwise the new value.
        BigDecimal minPrice,
        // read-only fields captured for warning comparison:
        String csvSlug,
        String csvDestinationSlug,
        String csvImageUrl
) {
}
