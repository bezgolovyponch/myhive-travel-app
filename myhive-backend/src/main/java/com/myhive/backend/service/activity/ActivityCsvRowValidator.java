package com.myhive.backend.service.activity;

import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.service.RemoteImageFetcher;
import com.myhive.backend.util.SlugUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-row validation: id format and uniqueness, required fields,
 * numeric formats, length caps, category slug existence. Produces
 * a list of ValidatedRow for rows that pass all checks, alongside
 * the errors collected for rows that don't.
 *
 * <p>A blank {@code id} marks a row that creates a new activity. Such rows additionally
 * need an existing {@code destination_slug}; their {@code slug} and {@code image_url} are
 * inputs: a blank slug means auto-generate from the name, a custom slug is normalised the
 * same way the admin form does it, and image_url must be an http(s) URL when set.
 *
 * Package-private; composed by ActivityCsvImporter.
 */
final class ActivityCsvRowValidator {

    static final int MAX_NAME_LEN = 255;
    static final int MAX_DESCRIPTION_LEN = 10_000;
    static final int MAX_INCLUDES_LEN = 10_000;
    static final int MAX_IMAGE_URL_LEN = 500;
    /** Mirrors the {@code activities.slug} column width. */
    static final int MAX_SLUG_LEN = 300;

    private final CategoryRepository categoryRepository;
    private final DestinationRepository destinationRepository;

    ActivityCsvRowValidator(CategoryRepository categoryRepository,
                            DestinationRepository destinationRepository) {
        this.categoryRepository = categoryRepository;
        this.destinationRepository = destinationRepository;
    }

    record Result(List<ValidatedRow> rows,
                  List<ActivityImportPreviewDTO.RowError> errors,
                  List<ActivityImportPreviewDTO.RowWarning> warnings) {
    }

    /** Per-file scratch state: memoised lookups and cross-row duplicate tracking. */
    private static final class FileContext {
        final List<ActivityImportPreviewDTO.RowError> errors = new ArrayList<>();
        final List<ActivityImportPreviewDTO.RowWarning> warnings = new ArrayList<>();
        final Map<UUID, Integer> seenIds = new HashMap<>();
        final Map<String, Integer> seenNewNames = new HashMap<>();
        final Map<String, Boolean> categoryExists = new HashMap<>();
        final Map<String, Boolean> destinationExists = new HashMap<>();
    }

    Result validate(List<RawRow> rawRows) {
        List<ValidatedRow> validated = new ArrayList<>();
        FileContext ctx = new FileContext();

        for (RawRow raw : rawRows) {
            int errorsAtStart = ctx.errors.size();

            boolean isNew = raw.get("id").isEmpty();
            UUID id = isNew ? null : parseId(raw, ctx);
            String name = parseName(raw, ctx.errors);
            String description = parseTextField(raw, "description", MAX_DESCRIPTION_LEN, ctx.errors);
            String includes = parseTextField(raw, "includes", MAX_INCLUDES_LEN, ctx.errors);
            BigDecimal price = parsePrice(raw, ctx.errors);
            Integer duration = parseDuration(raw, ctx.errors);
            List<String> categorySlugs = parseCategories(raw, ctx);
            Integer featuredWeight = parseFeaturedWeight(raw, ctx.errors);
            BigDecimal minPrice = parseMinPrice(raw, ctx.errors);
            String slug = raw.get("slug");
            if (isNew) {
                validateNewRowDestination(raw, ctx);
                validateNewRowImageUrl(raw, ctx.errors);
                slug = normalizeNewRowSlug(raw, name, ctx.errors);
                warnOnDuplicateNewName(raw, name, ctx);
            }

            if (ctx.errors.size() == errorsAtStart) {
                validated.add(new ValidatedRow(
                        raw.csvRowNumber(),
                        id, name, description, price, duration,
                        categorySlugs, includes,
                        featuredWeight,
                        minPrice,
                        slug,
                        raw.get("destination_slug"),
                        raw.get("image_url")
                ));
            }
        }
        return new Result(validated, ctx.errors, ctx.warnings);
    }

    private UUID parseId(RawRow raw, FileContext ctx) {
        String rawId = raw.get("id");
        UUID id;
        try {
            id = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            ctx.errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_UUID,
                    "id is not a valid UUID: " + rawId, "id"));
            return null;
        }
        Integer earlierRow = ctx.seenIds.putIfAbsent(id, raw.csvRowNumber());
        if (earlierRow != null) {
            ctx.errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.DUPLICATE_ID,
                    "duplicate id (also on row " + earlierRow + "): " + id, "id"));
            return null;
        }
        return id;
    }

    private void validateNewRowDestination(RawRow raw, FileContext ctx) {
        String destinationSlug = raw.get("destination_slug");
        if (destinationSlug.isEmpty()) {
            ctx.errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.DESTINATION_REQUIRED,
                    "destination_slug is required for a new activity (blank id)", "destination_slug"));
            return;
        }
        boolean exists = ctx.destinationExists.computeIfAbsent(destinationSlug,
                slug -> destinationRepository.findBySlug(slug).isPresent());
        if (!exists) {
            ctx.errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.UNKNOWN_DESTINATION,
                    "Unknown destination slug: " + destinationSlug, "destination_slug"));
        }
    }

    private void validateNewRowImageUrl(RawRow raw, List<ActivityImportPreviewDTO.RowError> errors) {
        String imageUrl = raw.get("image_url");
        if (imageUrl.isEmpty()) {
            return;
        }
        if (imageUrl.length() > MAX_IMAGE_URL_LEN) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.FIELD_TOO_LONG,
                    "image_url exceeds max length " + MAX_IMAGE_URL_LEN, "image_url"));
            return;
        }
        Optional<String> problem = RemoteImageFetcher.validate(imageUrl);
        if (problem.isPresent()) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_URL,
                    "image_url " + problem.get() + ": " + imageUrl, "image_url"));
        }
    }

    /**
     * Runs the same slugify step the writer will run, so the preview shows the slug that
     * gets stored and an un-slugifiable name/slug fails here instead of mid-transaction.
     * Returns the normalised custom slug, or "" when the slug is to be generated from the name.
     */
    private String normalizeNewRowSlug(RawRow raw, String name, List<ActivityImportPreviewDTO.RowError> errors) {
        String custom = raw.get("slug");
        String source = custom.isEmpty() ? name : custom;
        if (source.isEmpty()) {
            return ""; // blank name is already reported as NAME_REQUIRED
        }
        String field = custom.isEmpty() ? "name" : "slug";
        String slug;
        try {
            slug = SlugUtils.generateSlug(source);
        } catch (BadRequestException e) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_SLUG,
                    field + " cannot be turned into a URL slug (use Latin, Cyrillic or accented characters): "
                            + source, field));
            return "";
        }
        if (slug.length() > MAX_SLUG_LEN) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.FIELD_TOO_LONG,
                    "slug derived from " + field + " exceeds max length " + MAX_SLUG_LEN, field));
            return "";
        }
        return custom.isEmpty() ? "" : slug;
    }

    /** Two new rows with the same name in the same destination are almost always a copy-paste slip. */
    private void warnOnDuplicateNewName(RawRow raw, String name, FileContext ctx) {
        if (name.isEmpty()) {
            return;
        }
        String key = raw.get("destination_slug") + "\n" + name.toLowerCase(Locale.ROOT);
        Integer earlierRow = ctx.seenNewNames.putIfAbsent(key, raw.csvRowNumber());
        if (earlierRow != null) {
            ctx.warnings.add(new ActivityImportPreviewDTO.RowWarning(
                    raw.csvRowNumber(), ImportErrorCode.DUPLICATE_NAME,
                    "same name as new row " + earlierRow + " in this destination: " + name, "name"));
        }
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
        return parseMoney(raw, "price", rawPrice, errors);
    }

    private Integer parseDuration(RawRow raw, List<ActivityImportPreviewDTO.RowError> errors) {
        String rawDuration = raw.get("duration");
        if (rawDuration.isEmpty()) {
            return null;
        }
        return parseNonNegativeInt(raw, "duration", rawDuration, errors);
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
        return parseNonNegativeInt(raw, "featured_weight", rawValue, errors);
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
        return parseMoney(raw, "min_price", rawValue, errors);
    }

    private BigDecimal parseMoney(RawRow raw, String column, String rawValue,
                                  List<ActivityImportPreviewDTO.RowError> errors) {
        if (rawValue.contains(",")) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    column + " must use '.' as decimal separator: " + rawValue, column));
            return null;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(rawValue);
        } catch (NumberFormatException e) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    column + " is not a valid decimal: " + rawValue, column));
            return null;
        }
        if (value.scale() > 2) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    column + " has more than 2 decimal places: " + rawValue, column));
        }
        if (value.signum() < 0) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                    column + " must be non-negative: " + rawValue, column));
        }
        return value;
    }

    private Integer parseNonNegativeInt(RawRow raw, String column, String rawValue,
                                        List<ActivityImportPreviewDTO.RowError> errors) {
        int value;
        try {
            value = Integer.parseInt(rawValue);
        } catch (NumberFormatException e) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_INTEGER,
                    column + " is not an integer: " + rawValue, column));
            return null;
        }
        if (value < 0) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.INVALID_INTEGER,
                    column + " must be non-negative: " + rawValue, column));
            return null;
        }
        return value;
    }

    private List<String> parseCategories(RawRow raw, FileContext ctx) {
        String rawCategories = raw.get("category_slugs");
        List<String> slugs = rawCategories.isEmpty() ? List.of()
                : Arrays.stream(rawCategories.split(";"))
                  .map(String::trim).filter(s -> !s.isEmpty()).toList();
        List<String> unknownSlugs = new ArrayList<>();
        for (String s : slugs) {
            boolean exists = ctx.categoryExists.computeIfAbsent(s,
                    slug -> categoryRepository.findBySlug(slug).isPresent());
            if (!exists) {
                unknownSlugs.add(s);
            }
        }
        if (!unknownSlugs.isEmpty()) {
            ctx.errors.add(new ActivityImportPreviewDTO.RowError(
                    raw.csvRowNumber(), ImportErrorCode.UNKNOWN_CATEGORY,
                    "Unknown category slugs: " + String.join(", ", unknownSlugs),
                    "category_slugs"));
        }
        return slugs;
    }
}
