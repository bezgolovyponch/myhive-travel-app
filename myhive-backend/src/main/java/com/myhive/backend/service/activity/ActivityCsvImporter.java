package com.myhive.backend.service.activity;

import com.myhive.backend.dto.ActivityImportApplyRequest;
import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.dto.ActivityImportResultDTO;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

        // TODO Task 5+6: per-row validation and diff computation go here.
        return new ActivityImportPreviewDTO(
                null, parsed.rows().size(), 0, 0, errors.size(), warnings.size(),
                List.of(), errors, warnings
        );
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

    private record CachedPreview(List<RawRow> rows, Instant expiresAt) {
    }
}
