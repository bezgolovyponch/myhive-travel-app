package com.myhive.backend.service.activity;

import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.repository.CategoryRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-row validation: id format and uniqueness, required fields,
 * numeric formats, length caps, category slug existence. Produces
 * a list of ValidatedRow for rows that pass all checks, alongside
 * the errors collected for rows that don't.
 *
 * Package-private; composed by ActivityCsvImporter.
 */
final class ActivityCsvRowValidator {

    static final int MAX_NAME_LEN = 255;
    static final int MAX_DESCRIPTION_LEN = 10_000;
    static final int MAX_INCLUDES_LEN = 10_000;

    private final CategoryRepository categoryRepository;

    ActivityCsvRowValidator(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    record Result(List<ValidatedRow> rows, List<ActivityImportPreviewDTO.RowError> errors) {
    }

    Result validate(List<RawRow> rawRows) {
        List<ValidatedRow> validated = new ArrayList<>();
        List<ActivityImportPreviewDTO.RowError> errors = new ArrayList<>();
        Map<UUID, Integer> seenIds = new HashMap<>();

        for (RawRow raw : rawRows) {
            int errorsAtStart = errors.size();

            UUID id = parseId(raw, seenIds, errors);
            String name = parseName(raw, errors);
            String description = parseTextField(raw, "description", MAX_DESCRIPTION_LEN, errors);
            String includes = parseTextField(raw, "includes", MAX_INCLUDES_LEN, errors);
            BigDecimal price = parsePrice(raw, errors);
            Integer duration = parseDuration(raw, errors);
            List<String> categorySlugs = parseCategories(raw, errors);
            Integer featuredWeight = parseFeaturedWeight(raw, errors);
            BigDecimal minPrice = parseMinPrice(raw, errors);

            if (errors.size() == errorsAtStart) {
                validated.add(new ValidatedRow(
                        raw.csvRowNumber(),
                        id, name, description, price, duration,
                        categorySlugs, includes,
                        featuredWeight,
                        minPrice,
                        raw.get("slug"),
                        raw.get("destination_slug"),
                        raw.get("image_url")
                ));
            }
        }
        return new Result(validated, errors);
    }

    private UUID parseId(RawRow raw, Map<UUID, Integer> seenIds,
                         List<ActivityImportPreviewDTO.RowError> errors) {
        String rawId = raw.get("id");
        if (rawId.isEmpty()) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.MISSING_ID,
                    "id is required", "id"));
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_UUID,
                    "id is not a valid UUID: " + rawId, "id"));
            return null;
        }
        if (seenIds.containsKey(id)) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.DUPLICATE_ID,
                    "duplicate id (also on row " + seenIds.get(id) + "): " + id, "id"));
            return null;
        }
        seenIds.put(id, raw.csvRowNumber());
        return id;
    }

    private String parseName(RawRow raw, List<ActivityImportPreviewDTO.RowError> errors) {
        String name = raw.get("name");
        if (name.isEmpty()) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.NAME_REQUIRED,
                    "name is required", "name"));
            return name;
        }
        if (name.length() > MAX_NAME_LEN) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.FIELD_TOO_LONG,
                    "name exceeds max length " + MAX_NAME_LEN, "name"));
        }
        return name;
    }

    private String parseTextField(RawRow raw, String column, int maxLen,
                                  List<ActivityImportPreviewDTO.RowError> errors) {
        String value = raw.get(column);
        if (value.length() > maxLen) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.FIELD_TOO_LONG,
                    column + " exceeds max length " + maxLen, column));
        }
        return value;
    }

    private BigDecimal parsePrice(RawRow raw, List<ActivityImportPreviewDTO.RowError> errors) {
        String rawPrice = raw.get("price");
        if (rawPrice.isEmpty()) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.PRICE_REQUIRED,
                    "price is required", "price"));
            return null;
        }
        if (rawPrice.contains(",")) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    "price must use '.' as decimal separator: " + rawPrice, "price"));
            return null;
        }
        BigDecimal price;
        try {
            price = new BigDecimal(rawPrice);
        } catch (NumberFormatException e) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    "price is not a valid decimal: " + rawPrice, "price"));
            return null;
        }
        if (price.scale() > 2) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    "price has more than 2 decimal places: " + rawPrice, "price"));
        }
        if (price.signum() < 0) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    "price must be non-negative: " + rawPrice, "price"));
        }
        return price;
    }

    private Integer parseDuration(RawRow raw, List<ActivityImportPreviewDTO.RowError> errors) {
        String rawDuration = raw.get("duration");
        if (rawDuration.isEmpty()) {
            return null;
        }
        int duration;
        try {
            duration = Integer.parseInt(rawDuration);
        } catch (NumberFormatException e) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_INTEGER,
                    "duration is not an integer: " + rawDuration, "duration"));
            return null;
        }
        if (duration < 0) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_INTEGER,
                    "duration must be non-negative: " + rawDuration, "duration"));
            return null;
        }
        return duration;
    }

    /**
     * Optional column. Returns:
     *   - {@code null} if the column is absent from the CSV header → field is NOT updated.
     *   - {@code 0} if the column is present but the cell is blank → mirrors the duration
     *     convention of "blank cell = empty/default value"; for the primitive {@code int}
     *     {@code featuredWeight} (default 0) that is 0.
     *   - the parsed non-negative integer otherwise.
     */
    private Integer parseFeaturedWeight(RawRow raw,
                                        List<ActivityImportPreviewDTO.RowError> errors) {
        if (!raw.hasColumn("featured_weight")) {
            return null;
        }
        String rawValue = raw.get("featured_weight");
        if (rawValue.isEmpty()) {
            return 0;
        }
        int featuredWeight;
        try {
            featuredWeight = Integer.parseInt(rawValue);
        } catch (NumberFormatException e) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_INTEGER,
                    "featured_weight is not an integer: " + rawValue, "featured_weight"));
            return null;
        }
        if (featuredWeight < 0) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_INTEGER,
                    "featured_weight must be non-negative: " + rawValue, "featured_weight"));
            return null;
        }
        return featuredWeight;
    }

    /**
     * Optional column (same convention as featured_weight): null = column absent -> do not
     * update; BigDecimal.ZERO = blank cell -> clear the minimum; otherwise the parsed value.
     */
    private BigDecimal parseMinPrice(RawRow raw, List<ActivityImportPreviewDTO.RowError> errors) {
        if (!raw.hasColumn("min_price")) {
            return null;
        }
        String rawValue = raw.get("min_price");
        if (rawValue.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (rawValue.contains(",")) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    "min_price must use '.' as decimal separator: " + rawValue, "min_price"));
            return null;
        }
        BigDecimal minPrice;
        try {
            minPrice = new BigDecimal(rawValue);
        } catch (NumberFormatException e) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    "min_price is not a valid decimal: " + rawValue, "min_price"));
            return null;
        }
        if (minPrice.scale() > 2) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    "min_price has more than 2 decimal places: " + rawValue, "min_price"));
        }
        if (minPrice.signum() < 0) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    "min_price must be non-negative: " + rawValue, "min_price"));
        }
        return minPrice;
    }

    private List<String> parseCategories(RawRow raw,
                                         List<ActivityImportPreviewDTO.RowError> errors) {
        String rawCategories = raw.get("category_slugs");
        List<String> slugs = rawCategories.isEmpty() ? List.of()
                : Arrays.stream(rawCategories.split(";"))
                  .map(String::trim).filter(s -> !s.isEmpty()).toList();
        List<String> unknownSlugs = new ArrayList<>();
        for (String s : slugs) {
            if (categoryRepository.findBySlug(s).isEmpty()) {
                unknownSlugs.add(s);
            }
        }
        if (!unknownSlugs.isEmpty()) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.UNKNOWN_CATEGORY,
                    "Unknown category slugs: " + String.join(", ", unknownSlugs),
                    "category_slugs"));
        }
        return slugs;
    }
}
