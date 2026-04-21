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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.util.HashSet;
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
@Slf4j
public class ActivityCsvImporter {

    // The file-size cap and the row cap protect different things:
    // MAX_FILE_BYTES bounds peak network/parser memory; MAX_ROWS bounds
    // worst-case cached preview size (each ValidatedRow holds up to
    // ~20KB of strings, so the effective per-token memory ceiling is
    // MAX_FILE_BYTES * ~2 for String/object overhead, NOT MAX_ROWS *
    // 20KB — the file cap binds first in practice).
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
        tokenCache.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(Instant.now()));
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
        List<ValidatedRow> changedRows = new ArrayList<>();
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
                changedRows.add(v);
            }
        }

        String token = null;
        if (errors.isEmpty()) {
            UUID tokenUuid = UUID.randomUUID();
            tokenCache.put(tokenUuid,
                    new CachedPreview(changedRows, Instant.now().plus(TOKEN_TTL)));
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
        UUID token;
        try {
            token = UUID.fromString(request.token());
        } catch (IllegalArgumentException e) {
            throw new CsvImportException(CsvImportException.Code.TOKEN_NOT_FOUND,
                    "Preview token not found or already used");
        }

        CachedPreview cached = tokenCache.remove(token);
        if (cached == null) {
            throw new CsvImportException(CsvImportException.Code.TOKEN_NOT_FOUND,
                    "Preview token not found or already used");
        }
        if (Instant.now().isAfter(cached.expiresAt())) {
            throw new CsvImportException(CsvImportException.Code.TOKEN_EXPIRED,
                    "Preview token has expired");
        }

        String principal = principalName();
        log.info("Activity CSV import apply requested: principal={} rowCount={}",
                principal, cached.rows().size());

        int updated = 0;
        for (ValidatedRow v : cached.rows()) {
            Activity activity = activityRepository.findById(v.activityId())
                    .orElseThrow(() -> new CsvImportException(
                            CsvImportException.Code.STATE_CHANGED,
                            "Activity " + v.activityId() + " no longer exists (row "
                                    + v.csvRowNumber() + ")"));

            Set<Category> categories = new HashSet<>();
            for (String slug : v.categorySlugs()) {
                Category cat = categoryRepository.findBySlug(slug)
                        .orElseThrow(() -> new CsvImportException(
                                CsvImportException.Code.STATE_CHANGED,
                                "Category '" + slug + "' no longer exists (row "
                                        + v.csvRowNumber() + ")"));
                categories.add(cat);
            }

            activity.setName(v.name());
            activity.setDescription(v.description().isEmpty() ? null : v.description());
            activity.setPrice(v.price());
            activity.setDuration(v.duration());
            activity.setIncludes(v.includes().isEmpty() ? null : v.includes());
            activity.setCategories(categories);
            activityRepository.save(activity);
            updated++;
        }
        log.info("Activity CSV import applied: principal={} rowsUpdated={} activityIds={}",
                principal, updated,
                cached.rows().stream().map(ValidatedRow::activityId).toList());
        return new ActivityImportResultDTO(updated, Instant.now());
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

    private record ParseOutcome(List<RawRow> rows, Map<String, Integer> headerIndex) {
    }

    private record CachedPreview(List<ValidatedRow> rows, Instant expiresAt) {
    }

    private List<ValidatedRow> validateRows(
            List<RawRow> rawRows,
            List<ActivityImportPreviewDTO.RowError> errors) {

        List<ValidatedRow> validated = new ArrayList<>();
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

            if (errors.size() == errorsAtStart) {
                validated.add(new ValidatedRow(
                        raw.csvRowNumber(),
                        id, name, description, price, duration,
                        categorySlugs, includes,
                        raw.get("slug"),
                        raw.get("destination_slug"),
                        raw.get("image_url")
                ));
            }
        }
        return validated;
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
            changes.put("name", new ActivityImportPreviewDTO.FieldChange(nullToEmpty(db.getName()), v.name()));
        }
        if (!Objects.equals(nullToEmpty(db.getDescription()), v.description())) {
            changes.put("description", new ActivityImportPreviewDTO.FieldChange(nullToEmpty(db.getDescription()), v.description()));
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
            changes.put("includes", new ActivityImportPreviewDTO.FieldChange(nullToEmpty(db.getIncludes()), v.includes()));
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

    /** Visible for testing: force a token's expiry into the past. */
    void expireTokenForTest(UUID token) {
        CachedPreview c = tokenCache.get(token);
        if (c != null) {
            tokenCache.put(token, new CachedPreview(c.rows(), Instant.now().minusSeconds(1)));
        }
    }

    /** Visible for testing: clear the entire preview token cache. */
    public void clearCacheForTest() {
        tokenCache.clear();
    }

    private String principalName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "<unknown>" : auth.getName();
    }
}
