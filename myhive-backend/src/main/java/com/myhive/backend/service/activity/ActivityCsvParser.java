package com.myhive.backend.service.activity;

import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses raw CSV bytes into {@link RawRow} objects, validating only
 * the file-shape concerns: encoding, header presence, required
 * columns, row count cap. Row content validation lives in
 * {@link ActivityCsvRowValidator}.
 *
 * Package-private; composed by ActivityCsvImporter.
 */
final class ActivityCsvParser {

    static final int MAX_ROWS = 10_000;
    static final String[] REQUIRED_COLUMNS = {
            "id", "slug", "destination_slug", "name", "description",
            "price", "duration", "category_slugs", "image_url", "includes"
    };
    static final Set<String> OPTIONAL_COLUMNS = Set.of("featured_weight");

    record Result(
            List<RawRow> rows,
            List<ActivityImportPreviewDTO.RowError> errors,
            List<ActivityImportPreviewDTO.RowWarning> warnings
    ) {
        boolean isFatal() {
            return !errors.isEmpty();
        }
    }

    Result parse(byte[] bytes) {
        List<ActivityImportPreviewDTO.RowError> errors = new ArrayList<>();
        List<ActivityImportPreviewDTO.RowWarning> warnings = new ArrayList<>();

        byte[] stripped = stripBom(bytes);
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(stripped), StandardCharsets.UTF_8);
             CSVReader csv = new CSVReaderBuilder(reader).build()) {

            String[] header = csv.readNext();
            if (header == null) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        0, ImportErrorCode.EMPTY_FILE, "File contains no header row", null));
                return new Result(List.of(), errors, warnings);
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
                return new Result(List.of(), errors, warnings);
            }

            Set<String> requiredSet = Set.of(REQUIRED_COLUMNS);
            List<String> unknown = headerIndex.keySet().stream()
                    .filter(c -> !requiredSet.contains(c) && !OPTIONAL_COLUMNS.contains(c))
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
                    return new Result(List.of(), errors, warnings);
                }
                rows.add(new RawRow(lineNumber, line, headerIndex));
            }
            return new Result(rows, errors, warnings);

        } catch (IOException | CsvValidationException e) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    0, ImportErrorCode.INVALID_ENCODING, e.getMessage(), null));
            return new Result(List.of(), errors, warnings);
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
}
