package com.myhive.backend.service.activity;

import com.myhive.backend.dto.ActivityImportApplyRequest;
import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.dto.ActivityImportResultDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.exception.CsvImportException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityCsvImporter {

    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    static final int MAX_ROWS = 10_000;
    static final String[] REQUIRED_COLUMNS = {
            "id", "slug", "destination_slug", "name", "description",
            "price", "duration", "category_slugs", "image_url", "includes"
    };
    static final int MAX_DESCRIPTION_LEN = 10_000;
    static final int MAX_INCLUDES_LEN = 10_000;
    static final int MAX_NAME_LEN = 255;
    static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    private final ActivityRepository activityRepository;
    private final CategoryRepository categoryRepository;
    private final Map<UUID, CachedPreview> tokenCache = new ConcurrentHashMap<>();

    public ActivityImportPreviewDTO preview(byte[] fileContent) {
        List<ActivityImportPreviewDTO.RowError> errors = new ArrayList<>();
        List<ActivityImportPreviewDTO.RowWarning> warnings = new ArrayList<>();

        if (fileContent == null || fileContent.length == 0) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    0, ImportErrorCode.EMPTY_FILE, "File is empty", null));
            return emptyPreview(errors, warnings);
        }
        if (fileContent.length > MAX_FILE_BYTES) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    0, ImportErrorCode.FILE_TOO_LARGE,
                    "File exceeds maximum size of " + MAX_FILE_BYTES + " bytes", null));
            return emptyPreview(errors, warnings);
        }

        ParseOutcome parsed;
        try {
            parsed = parse(fileContent, errors, warnings);
        } catch (IOException | CsvValidationException e) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    0, ImportErrorCode.INVALID_ENCODING, e.getMessage(), null));
            return emptyPreview(errors, warnings);
        }
        if (parsed == null) {
            return emptyPreview(errors, warnings);
        }

        List<ValidatedRow> validated = validateRows(parsed.rows(), errors);

        // Look up activities only for rows whose IDs parsed successfully
        List<UUID> idsToFetch = validated.stream().map(ValidatedRow::activityId).toList();
        Map<UUID, Activity> fromDb = activityRepository.findAllById(idsToFetch).stream()
                .collect(Collectors.toMap(Activity::getId, a -> a));

        List<ValidatedRow> matched = new ArrayList<>();
        for (ValidatedRow v : validated) {
            if (!fromDb.containsKey(v.activityId())) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        v.csvRowNumber(), ImportErrorCode.ROW_NOT_FOUND,
                        "No activity with id " + v.activityId(), "id"));
            } else {
                matched.add(v);
            }
        }

        List<ActivityImportPreviewDTO.RowDiff> diffs = new ArrayList<>();
        int unchanged = 0;
        for (ValidatedRow v : matched) {
            Activity db = fromDb.get(v.activityId());
            addReadOnlyWarnings(v, db, warnings);
            Map<String, ActivityImportPreviewDTO.FieldChange> fieldChanges = computeFieldChanges(v, db);
            if (fieldChanges.isEmpty()) {
                unchanged++;
            } else {
                diffs.add(new ActivityImportPreviewDTO.RowDiff(
                        v.csvRowNumber(), v.activityId(), db.getName(), fieldChanges));
            }
        }

        String token = null;
        if (errors.isEmpty()) {
            UUID tokenUuid = UUID.randomUUID();
            tokenCache.put(tokenUuid,
                    new CachedPreview(matched, Instant.now().plus(TOKEN_TTL)));
            token = tokenUuid.toString();
        }

        return new ActivityImportPreviewDTO(
                token,
                parsed.rows().size(),
                diffs.size(),
                unchanged,
                errors.size(),
                warnings.size(),
                diffs,
                errors,
                warnings);
    }

    @Transactional
    public ActivityImportResultDTO apply(ActivityImportApplyRequest request) {
        throw new CsvImportException(CsvImportException.Code.TOKEN_NOT_FOUND,
                "Apply not yet implemented");
    }

    /* ------------------------- parsing ------------------------- */

    private ParseOutcome parse(byte[] bytes,
                               List<ActivityImportPreviewDTO.RowError> errors,
                               List<ActivityImportPreviewDTO.RowWarning> warnings) throws IOException, CsvValidationException {
        byte[] stripped = stripBom(bytes);
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(stripped), StandardCharsets.UTF_8);
             CSVReader csv = new CSVReaderBuilder(reader).build()) {

            String[] header = csv.readNext();
            if (header == null) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        0, ImportErrorCode.EMPTY_FILE, "File contains no header row", null));
                return null;
            }
            Map<String, Integer> headerIndex = new LinkedHashMap<>();
            for (int i = 0; i < header.length; i++) {
                headerIndex.put(header[i].trim(), i);
            }

            List<String> missing = new ArrayList<>();
            for (String required : REQUIRED_COLUMNS) {
                if (!headerIndex.containsKey(required)) {
                    missing.add(required);
                }
            }
            if (!missing.isEmpty()) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        1, ImportErrorCode.MISSING_COLUMNS,
                        "Missing required columns: " + String.join(", ", missing), null));
                return null;
            }

            Set<String> requiredSet = Set.of(REQUIRED_COLUMNS);
            List<String> unknown = headerIndex.keySet().stream()
                    .filter(c -> !requiredSet.contains(c))
                    .toList();
            if (!unknown.isEmpty()) {
                warnings.add(new ActivityImportPreviewDTO.RowWarning(
                        1, ImportErrorCode.UNKNOWN_COLUMNS,
                        "Unknown columns ignored: " + String.join(", ", unknown), null));
            }

            List<RawRow> rows = new ArrayList<>();
            String[] line;
            int lineNumber = 1;
            while ((line = csv.readNext()) != null) {
                lineNumber++;
                if (line.length == 0 || (line.length == 1 && (line[0] == null || line[0].isBlank()))) {
                    continue;
                }
                if (rows.size() >= MAX_ROWS) {
                    errors.add(new ActivityImportPreviewDTO.RowError(
                            lineNumber, ImportErrorCode.TOO_MANY_ROWS,
                            "File exceeds maximum of " + MAX_ROWS + " rows", null));
                    return null;
                }
                rows.add(new RawRow(lineNumber, line, headerIndex));
            }
            return new ParseOutcome(rows, headerIndex);
        }
    }

    private byte[] stripBom(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            byte[] out = new byte[bytes.length - 3];
            System.arraycopy(bytes, 3, out, 0, out.length);
            return out;
        }
        return bytes;
    }

    private ActivityImportPreviewDTO emptyPreview(
            List<ActivityImportPreviewDTO.RowError> errors,
            List<ActivityImportPreviewDTO.RowWarning> warnings) {
        return new ActivityImportPreviewDTO(
                null, 0, 0, 0, errors.size(), warnings.size(),
                List.of(), errors, warnings);
    }

    /* ------------------------- inner types ------------------------- */

    private record RawRow(int csvRowNumber, String[] values, Map<String, Integer> headerIndex) {
        String get(String column) {
            Integer idx = headerIndex.get(column);
            if (idx == null || idx >= values.length) {
                return "";
            }
            return values[idx] == null ? "" : values[idx].trim();
        }
    }

    private record ParseOutcome(List<RawRow> rows, Map<String, Integer> headerIndex) {
    }

    private record ValidatedRow(
            int csvRowNumber,
            UUID activityId,
            String name,
            String description,
            BigDecimal price,
            Integer duration,
            List<UUID> categoryIds,
            List<String> categorySlugs,
            String includes,
            // read-only fields captured for warning comparison in Task 6:
            String csvSlug,
            String csvDestinationSlug,
            String csvImageUrl
    ) {
    }

    private record CachedPreview(List<ValidatedRow> rows, Instant expiresAt) {
    }

    private List<ValidatedRow> validateRows(
            List<RawRow> rawRows,
            List<ActivityImportPreviewDTO.RowError> errors) {

        List<ValidatedRow> validated = new ArrayList<>();
        Map<UUID, Integer> seenIds = new HashMap<>();

        for (RawRow raw : rawRows) {
            boolean rowHasError = false;

            // id
            String rawId = raw.get("id");
            UUID id = null;
            if (rawId.isEmpty()) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        raw.csvRowNumber(), ImportErrorCode.MISSING_ID,
                        "id is required", "id"));
                rowHasError = true;
            } else {
                try {
                    id = UUID.fromString(rawId);
                } catch (IllegalArgumentException e) {
                    errors.add(new ActivityImportPreviewDTO.RowError(
                            raw.csvRowNumber(), ImportErrorCode.INVALID_UUID,
                            "id is not a valid UUID: " + rawId, "id"));
                    rowHasError = true;
                }
            }
            if (id != null && seenIds.containsKey(id)) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        raw.csvRowNumber(), ImportErrorCode.DUPLICATE_ID,
                        "duplicate id (also on row " + seenIds.get(id) + "): " + id, "id"));
                rowHasError = true;
            }

            // name
            String name = raw.get("name");
            if (name.isEmpty()) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        raw.csvRowNumber(), ImportErrorCode.NAME_REQUIRED,
                        "name is required", "name"));
                rowHasError = true;
            } else if (name.length() > MAX_NAME_LEN) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        raw.csvRowNumber(), ImportErrorCode.FIELD_TOO_LONG,
                        "name exceeds max length " + MAX_NAME_LEN, "name"));
                rowHasError = true;
            }

            // description / includes length
            String description = raw.get("description");
            if (description.length() > MAX_DESCRIPTION_LEN) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        raw.csvRowNumber(), ImportErrorCode.FIELD_TOO_LONG,
                        "description exceeds max length " + MAX_DESCRIPTION_LEN, "description"));
                rowHasError = true;
            }
            String includes = raw.get("includes");
            if (includes.length() > MAX_INCLUDES_LEN) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        raw.csvRowNumber(), ImportErrorCode.FIELD_TOO_LONG,
                        "includes exceeds max length " + MAX_INCLUDES_LEN, "includes"));
                rowHasError = true;
            }

            // price
            String rawPrice = raw.get("price");
            BigDecimal price = null;
            if (rawPrice.isEmpty()) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        raw.csvRowNumber(), ImportErrorCode.PRICE_REQUIRED,
                        "price is required", "price"));
                rowHasError = true;
            } else if (rawPrice.contains(",")) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                        "price must use '.' as decimal separator: " + rawPrice, "price"));
                rowHasError = true;
            } else {
                try {
                    price = new BigDecimal(rawPrice);
                    if (price.scale() > 2) {
                        errors.add(new ActivityImportPreviewDTO.RowError(
                                raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                                "price has more than 2 decimal places: " + rawPrice, "price"));
                        rowHasError = true;
                    }
                    if (price.signum() < 0) {
                        errors.add(new ActivityImportPreviewDTO.RowError(
                                raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                                "price must be non-negative: " + rawPrice, "price"));
                        rowHasError = true;
                    }
                } catch (NumberFormatException e) {
                    errors.add(new ActivityImportPreviewDTO.RowError(
                            raw.csvRowNumber(), ImportErrorCode.INVALID_DECIMAL,
                            "price is not a valid decimal: " + rawPrice, "price"));
                    rowHasError = true;
                }
            }

            // duration
            String rawDuration = raw.get("duration");
            Integer duration = null;
            if (!rawDuration.isEmpty()) {
                try {
                    duration = Integer.parseInt(rawDuration);
                    if (duration < 0) {
                        errors.add(new ActivityImportPreviewDTO.RowError(
                                raw.csvRowNumber(), ImportErrorCode.INVALID_INTEGER,
                                "duration must be non-negative: " + rawDuration, "duration"));
                        rowHasError = true;
                        duration = null;
                    }
                } catch (NumberFormatException e) {
                    errors.add(new ActivityImportPreviewDTO.RowError(
                            raw.csvRowNumber(), ImportErrorCode.INVALID_INTEGER,
                            "duration is not an integer: " + rawDuration, "duration"));
                    rowHasError = true;
                }
            }

            // categories
            String rawCategories = raw.get("category_slugs");
            List<String> slugs = rawCategories.isEmpty() ? List.of()
                    : Arrays.stream(rawCategories.split(";"))
                      .map(String::trim).filter(s -> !s.isEmpty()).toList();
            List<UUID> categoryIds = new ArrayList<>();
            List<String> unknownSlugs = new ArrayList<>();
            for (String s : slugs) {
                categoryRepository.findBySlug(s).ifPresentOrElse(
                        c -> categoryIds.add(c.getId()),
                        () -> unknownSlugs.add(s));
            }
            if (!unknownSlugs.isEmpty()) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        raw.csvRowNumber(), ImportErrorCode.UNKNOWN_CATEGORY,
                        "Unknown category slugs: " + String.join(", ", unknownSlugs),
                        "category_slugs"));
                rowHasError = true;
            }

            if (id != null) {
                seenIds.putIfAbsent(id, raw.csvRowNumber());
            }

            if (!rowHasError) {
                validated.add(new ValidatedRow(
                        raw.csvRowNumber(),
                        id,
                        name,
                        description,
                        price,
                        duration,
                        categoryIds,
                        slugs,
                        includes,
                        raw.get("slug"),
                        raw.get("destination_slug"),
                        raw.get("image_url")
                ));
            }
        }
        return validated;
    }

    private void addReadOnlyWarnings(
            ValidatedRow v,
            Activity db,
            List<ActivityImportPreviewDTO.RowWarning> warnings) {
        if (!v.csvSlug().isEmpty() && !v.csvSlug().equals(nullToEmpty(db.getSlug()))) {
            warnings.add(new ActivityImportPreviewDTO.RowWarning(
                    v.csvRowNumber(), ImportErrorCode.READ_ONLY_FIELD_CHANGED,
                    "slug is read-only; imported value will be ignored", "slug"));
        }
        String dbDestSlug = db.getDestination() == null ? "" : nullToEmpty(db.getDestination().getSlug());
        if (!v.csvDestinationSlug().isEmpty() && !v.csvDestinationSlug().equals(dbDestSlug)) {
            warnings.add(new ActivityImportPreviewDTO.RowWarning(
                    v.csvRowNumber(), ImportErrorCode.READ_ONLY_FIELD_CHANGED,
                    "destination_slug is read-only; imported value will be ignored", "destination_slug"));
        }
        if (!v.csvImageUrl().isEmpty() && !v.csvImageUrl().equals(nullToEmpty(db.getImageUrl()))) {
            warnings.add(new ActivityImportPreviewDTO.RowWarning(
                    v.csvRowNumber(), ImportErrorCode.READ_ONLY_FIELD_CHANGED,
                    "image_url is read-only; imported value will be ignored", "image_url"));
        }
    }

    private Map<String, ActivityImportPreviewDTO.FieldChange> computeFieldChanges(
            ValidatedRow v, Activity db) {
        Map<String, ActivityImportPreviewDTO.FieldChange> changes = new LinkedHashMap<>();
        if (!Objects.equals(nullToEmpty(db.getName()), v.name())) {
            changes.put("name", new ActivityImportPreviewDTO.FieldChange(db.getName(), v.name()));
        }
        if (!Objects.equals(nullToEmpty(db.getDescription()), v.description())) {
            changes.put("description", new ActivityImportPreviewDTO.FieldChange(db.getDescription(), v.description()));
        }
        BigDecimal dbPrice = db.getPrice() == null ? null
                : db.getPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal csvPrice = v.price() == null ? null
                : v.price().setScale(2, RoundingMode.HALF_UP);
        if (!Objects.equals(dbPrice, csvPrice)) {
            changes.put("price", new ActivityImportPreviewDTO.FieldChange(dbPrice, csvPrice));
        }
        if (!Objects.equals(db.getDuration(), v.duration())) {
            changes.put("duration", new ActivityImportPreviewDTO.FieldChange(db.getDuration(), v.duration()));
        }
        if (!Objects.equals(nullToEmpty(db.getIncludes()), v.includes())) {
            changes.put("includes", new ActivityImportPreviewDTO.FieldChange(db.getIncludes(), v.includes()));
        }
        Set<String> dbSlugs = db.getCategories() == null ? Set.of()
                : db.getCategories().stream()
                    .map(Category::getSlug)
                    .collect(Collectors.toCollection(TreeSet::new));
        Set<String> csvSlugs = new TreeSet<>(v.categorySlugs());
        if (!dbSlugs.equals(csvSlugs)) {
            changes.put("category_slugs", new ActivityImportPreviewDTO.FieldChange(
                    String.join(";", dbSlugs), String.join(";", csvSlugs)));
        }
        return changes;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
