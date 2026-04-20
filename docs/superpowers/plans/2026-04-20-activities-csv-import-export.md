# Activities CSV Import/Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admins can export all activities to CSV, edit the file (typically via an AI assistant), and import it back with a two-step preview/apply flow that prevents corrupting production data.

**Architecture:** Two backend services (`ActivityCsvExporter`, `ActivityCsvImporter`) + 3 new endpoints in `AdminController`. Import is update-only, matched by `id`. Preview returns a one-shot token (10-min TTL, in-memory); apply consumes the token, re-validates against current DB state, and writes transactionally (all-or-nothing). Frontend adds Export/Import buttons to `AdminActivities.js` and a three-step modal.

**Tech Stack:** Spring Boot 4 / Java 25 / Gradle (backend), OpenCSV 5.11 (CSV parsing), JPA, JUnit 5 + Mockito, Spring MockMvc; React 19 + Bootstrap 5 (frontend).

**Spec:** [`docs/superpowers/specs/2026-04-20-activities-csv-import-export-design.md`](../specs/2026-04-20-activities-csv-import-export-design.md)

---

## File Structure

### Backend — new files

- `myhive-backend/src/main/java/com/myhive/backend/exception/CsvImportException.java` — typed exception with `ErrorCode` (TOKEN_EXPIRED, TOKEN_NOT_FOUND, STATE_CHANGED, HAS_ERRORS)
- `myhive-backend/src/main/java/com/myhive/backend/service/activity/ImportErrorCode.java` — enum of row-level error/warning codes
- `myhive-backend/src/main/java/com/myhive/backend/dto/ActivityImportPreviewDTO.java` — preview response; contains nested `RowDiff`, `FieldChange`, `RowError`, `RowWarning` records
- `myhive-backend/src/main/java/com/myhive/backend/dto/ActivityImportResultDTO.java` — apply response
- `myhive-backend/src/main/java/com/myhive/backend/dto/ActivityImportApplyRequest.java` — apply request body (`{ token }`)
- `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvExporter.java` — writes CSV from DB state
- `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvImporter.java` — parses, validates, diffs, caches preview token, applies transactionally

### Backend — modified files

- `myhive-backend/build.gradle` — add `com.opencsv:opencsv:5.11` dependency
- `myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java` — 3 new endpoints
- `myhive-backend/src/main/java/com/myhive/backend/config/SecurityConfig.java` — scope new endpoints to `ADMIN` only
- `myhive-backend/src/main/java/com/myhive/backend/exception/GlobalExceptionHandler.java` — handle `CsvImportException`

### Backend — tests

- `myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvExporterTest.java` (unit)
- `myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvImporterTest.java` (unit, with real repository mocks)
- `myhive-backend/src/test/java/com/myhive/backend/controller/ActivityCsvImportExportIntegrationTest.java` (`@SpringBootTest`, H2)
- `myhive-backend/src/test/java/com/myhive/backend/TestDataFactory.java` — add `activityWithCategories(...)` helper

### Frontend — new files

- `myhive-react-app/src/components/admin/ImportActivitiesModal.js`

### Frontend — modified files

- `myhive-react-app/src/services/adminApi.js` — add `exportActivitiesCsv()`, `previewActivityImport(file)`, `applyActivityImport(token)`
- `myhive-react-app/src/pages/AdminActivities.js` — add Export + Import buttons, wire the modal

---

## Task 1: Add OpenCSV and scaffold enum, DTOs, exception

**Files:**
- Modify: `myhive-backend/build.gradle`
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/activity/ImportErrorCode.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/exception/CsvImportException.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/ActivityImportPreviewDTO.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/ActivityImportResultDTO.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/ActivityImportApplyRequest.java`

No tests in this task — the enum and records are data-only. Tests come with the services in later tasks.

- [ ] **Step 1: Add OpenCSV to `build.gradle`**

Insert this line in the `dependencies { }` block, right after the `software.amazon.awssdk:s3` line:

```groovy
    // CSV parsing for activities import/export
    implementation 'com.opencsv:opencsv:5.11'
```

- [ ] **Step 2: Create `ImportErrorCode.java`**

```java
package com.myhive.backend.service.activity;

public enum ImportErrorCode {
    // File-level errors
    EMPTY_FILE,
    FILE_TOO_LARGE,
    TOO_MANY_ROWS,
    INVALID_ENCODING,
    MISSING_COLUMNS,

    // File-level warnings
    UNKNOWN_COLUMNS,

    // Row-level errors
    MISSING_ID,
    INVALID_UUID,
    ROW_NOT_FOUND,
    DUPLICATE_ID,
    NAME_REQUIRED,
    FIELD_TOO_LONG,
    INVALID_DECIMAL,
    PRICE_REQUIRED,
    INVALID_INTEGER,
    UNKNOWN_CATEGORY,

    // Row-level warnings
    READ_ONLY_FIELD_CHANGED
}
```

- [ ] **Step 3: Create `CsvImportException.java`**

```java
package com.myhive.backend.exception;

import lombok.Getter;

@Getter
public class CsvImportException extends RuntimeException {

    public enum Code {
        TOKEN_NOT_FOUND,
        TOKEN_EXPIRED,
        STATE_CHANGED,
        HAS_ERRORS
    }

    private final Code code;

    public CsvImportException(Code code, String message) {
        super(message);
        this.code = code;
    }
}
```

- [ ] **Step 4: Create `ActivityImportPreviewDTO.java`**

```java
package com.myhive.backend.dto;

import com.myhive.backend.service.activity.ImportErrorCode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ActivityImportPreviewDTO(
        String token,
        int totalRows,
        int rowsToUpdate,
        int rowsUnchanged,
        int rowsWithErrors,
        int rowsWithWarnings,
        List<RowDiff> changes,
        List<RowError> errors,
        List<RowWarning> warnings
) {
    public record RowDiff(
            int csvRowNumber,
            UUID activityId,
            String activityName,
            Map<String, FieldChange> fieldChanges
    ) {
    }

    public record FieldChange(Object oldValue, Object newValue) {
    }

    public record RowError(int csvRowNumber, ImportErrorCode code, String message, String field) {
    }

    public record RowWarning(int csvRowNumber, ImportErrorCode code, String message, String field) {
    }
}
```

- [ ] **Step 5: Create `ActivityImportResultDTO.java`**

```java
package com.myhive.backend.dto;

import java.time.Instant;

public record ActivityImportResultDTO(int rowsUpdated, Instant appliedAt) {
}
```

- [ ] **Step 6: Create `ActivityImportApplyRequest.java`**

```java
package com.myhive.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ActivityImportApplyRequest(@NotBlank String token) {
}
```

- [ ] **Step 7: Verify compilation**

Run: `./gradlew compileJava` from `myhive-backend/`.
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add myhive-backend/build.gradle \
  myhive-backend/src/main/java/com/myhive/backend/service/activity/ImportErrorCode.java \
  myhive-backend/src/main/java/com/myhive/backend/exception/CsvImportException.java \
  myhive-backend/src/main/java/com/myhive/backend/dto/ActivityImportPreviewDTO.java \
  myhive-backend/src/main/java/com/myhive/backend/dto/ActivityImportResultDTO.java \
  myhive-backend/src/main/java/com/myhive/backend/dto/ActivityImportApplyRequest.java
git commit -m "feat: scaffold types for activities CSV import/export"
```

---

## Task 2: Handle `CsvImportException` in `GlobalExceptionHandler`

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/exception/GlobalExceptionHandler.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/exception/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Write failing test**

Append to `GlobalExceptionHandlerTest.java` (inside the existing test class):

```java
    @Test
    void handleCsvImportException_returnsBadRequestWithCode() {
        CsvImportException ex = new CsvImportException(CsvImportException.Code.TOKEN_EXPIRED,
                "Preview token has expired");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/admin/activities/import/apply");

        ResponseEntity<ErrorResponse> response = handler.handleCsvImport(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Preview token has expired");
        assertThat(response.getBody().getError()).isEqualTo("TOKEN_EXPIRED");
    }
```

Add imports if not already present at the top of the file:

```java
import com.myhive.backend.exception.CsvImportException;
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests 'GlobalExceptionHandlerTest.handleCsvImportException_returnsBadRequestWithCode'`
Expected: FAIL — "cannot find method handleCsvImport" or similar.

- [ ] **Step 3: Add handler method**

In `GlobalExceptionHandler.java`, add before `handleGenericException`:

```java
    @ExceptionHandler(CsvImportException.class)
    public ResponseEntity<ErrorResponse> handleCsvImport(CsvImportException ex, HttpServletRequest request) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(ex.getCode().name())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
```

Add import at top of file:

```java
import com.myhive.backend.exception.CsvImportException;
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests 'GlobalExceptionHandlerTest'`
Expected: all tests in that class PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/exception/GlobalExceptionHandler.java \
  myhive-backend/src/test/java/com/myhive/backend/exception/GlobalExceptionHandlerTest.java
git commit -m "feat: map CsvImportException to 400 with error code in response"
```

---

## Task 3: `ActivityCsvExporter` — write-side

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvExporter.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvExporterTest.java`

- [ ] **Step 1: Write failing tests**

Create `ActivityCsvExporterTest.java`:

```java
package com.myhive.backend.service.activity;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.ActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityCsvExporterTest {

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private ActivityCsvExporter exporter;

    private Destination destination;
    private Category categoryBeach;
    private Category categoryFamily;

    @BeforeEach
    void setUp() {
        destination = TestDataFactory.destination();
        categoryBeach = TestDataFactory.category("Beach");
        categoryFamily = TestDataFactory.category("Family");
    }

    @Test
    void export_writesBomAndHeader() {
        when(activityRepository.findAll()).thenReturn(List.of());

        String csv = exporter.exportAll();

        assertThat(csv).startsWith("\uFEFF");
        String firstLine = csv.substring(1).split("\r?\n", 2)[0];
        assertThat(firstLine).isEqualTo(
                "id,slug,destination_slug,name,description,price,duration,category_slugs,image_url,includes");
    }

    @Test
    void export_writesActivityRowWithCategoriesJoined() {
        Activity activity = TestDataFactory.activity(destination, categoryBeach, categoryFamily);
        when(activityRepository.findAll()).thenReturn(List.of(activity));

        String csv = exporter.exportAll();

        assertThat(csv).contains(activity.getId().toString());
        assertThat(csv).contains("beach;family");
    }

    @Test
    void export_escapesQuotesAndNewlinesInDescription() {
        Activity activity = TestDataFactory.activity(destination);
        activity.setDescription("Line one\nLine \"two\"");
        when(activityRepository.findAll()).thenReturn(List.of(activity));

        String csv = exporter.exportAll();

        assertThat(csv).contains("\"Line one\nLine \"\"two\"\"\"");
    }

    @Test
    void export_prefixesFormulaInjectionWithSingleQuote() {
        Activity activity = TestDataFactory.activity(destination);
        activity.setName("=CMD(\"calc\")");
        activity.setIncludes("-1+1");
        activity.setDescription("@lookup");
        when(activityRepository.findAll()).thenReturn(List.of(activity));

        String csv = exporter.exportAll();

        assertThat(csv).contains("\"'=CMD(\"\"calc\"\")\"");
        assertThat(csv).contains("'-1+1");
        assertThat(csv).contains("'@lookup");
    }

    @Test
    void export_blankPriceOrDurationWrittenAsEmpty() {
        Activity activity = TestDataFactory.activity(destination);
        activity.setPrice(new BigDecimal("12.50"));
        activity.setDuration(null);
        when(activityRepository.findAll()).thenReturn(List.of(activity));

        String csv = exporter.exportAll();

        assertThat(csv).contains("12.50");
        // duration column must be empty (two consecutive commas around it)
        String dataLine = csv.split("\r?\n")[1];
        String[] columns = dataLine.split(",", -1);
        assertThat(columns[6]).isEmpty();
    }
}
```

- [ ] **Step 2: Run to verify tests fail**

Run: `./gradlew test --tests 'ActivityCsvExporterTest'`
Expected: FAIL — class `ActivityCsvExporter` does not exist.

- [ ] **Step 3: Implement `ActivityCsvExporter.java`**

Create `ActivityCsvExporter.java`:

```java
package com.myhive.backend.service.activity;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.repository.ActivityRepository;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityCsvExporter {

    static final String[] HEADER = {
            "id", "slug", "destination_slug", "name", "description",
            "price", "duration", "category_slugs", "image_url", "includes"
    };

    private static final String BOM = "\uFEFF";

    private final ActivityRepository activityRepository;

    @Transactional(readOnly = true)
    public String exportAll() {
        List<Activity> activities = activityRepository.findAll();
        StringWriter out = new StringWriter();
        out.write(BOM);
        try (CSVWriter writer = new CSVWriter(out,
                CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {
            writer.writeNext(HEADER, false);
            for (Activity a : activities) {
                writer.writeNext(toRow(a), false);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write CSV", e);
        }
        return out.toString();
    }

    private String[] toRow(Activity a) {
        return new String[]{
                String.valueOf(a.getId()),
                nullSafe(a.getSlug()),
                a.getDestination() == null ? "" : nullSafe(a.getDestination().getSlug()),
                sanitize(nullSafe(a.getName())),
                sanitize(nullSafe(a.getDescription())),
                formatPrice(a.getPrice()),
                a.getDuration() == null ? "" : a.getDuration().toString(),
                joinCategorySlugs(a),
                nullSafe(a.getImageUrl()),
                sanitize(nullSafe(a.getIncludes()))
        };
    }

    private String joinCategorySlugs(Activity a) {
        if (a.getCategories() == null || a.getCategories().isEmpty()) {
            return "";
        }
        return a.getCategories().stream()
                .sorted(Comparator.comparing(Category::getSlug))
                .map(Category::getSlug)
                .collect(Collectors.joining(";"));
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "";
        }
        return price.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private String sanitize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        char c = s.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            return "'" + s;
        }
        return s;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests 'ActivityCsvExporterTest'`
Expected: all 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvExporter.java \
  myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvExporterTest.java
git commit -m "feat: add ActivityCsvExporter with BOM, RFC4180 escaping, and formula-injection defense"
```

---

## Task 4: `ActivityCsvImporter` — file-level validation and parsing skeleton

Build the importer incrementally. In this task: public API signature, file-level validation, header parsing, raw row extraction. No diff yet.

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvImporter.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvImporterTest.java`

- [ ] **Step 1: Write failing tests**

Create `ActivityCsvImporterTest.java`:

```java
package com.myhive.backend.service.activity;

import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityCsvImporterTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ActivityCsvImporter importer;

    private String header() {
        return "id,slug,destination_slug,name,description,price,duration,category_slugs,image_url,includes\n";
    }

    @Test
    void preview_emptyBytes_returnsEmptyFileError() {
        ActivityImportPreviewDTO preview = importer.preview(new byte[0]);

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .containsExactly(ImportErrorCode.EMPTY_FILE);
    }

    @Test
    void preview_fileTooLarge_returnsFileTooLargeError() {
        byte[] big = new byte[(5 * 1024 * 1024) + 1];

        ActivityImportPreviewDTO preview = importer.preview(big);

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .containsExactly(ImportErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void preview_missingRequiredColumn_returnsMissingColumnsError() {
        String csv = "id,name,price\n" + "x,y,1.00\n";

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.MISSING_COLUMNS);
    }

    @Test
    void preview_unknownColumn_producesWarningOnly() {
        when(activityRepository.findAllById(List.of())).thenReturn(List.of());
        String csv = header().replace("\n", ",extra_col\n");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.warnings())
                .extracting(ActivityImportPreviewDTO.RowWarning::code)
                .contains(ImportErrorCode.UNKNOWN_COLUMNS);
        assertThat(preview.errors()).isEmpty();
    }

    @Test
    void preview_bomStripped_headerStillRecognized() {
        when(activityRepository.findAllById(List.of())).thenReturn(List.of());
        byte[] withBom = ("\uFEFF" + header()).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ActivityImportPreviewDTO preview = importer.preview(withBom);

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.totalRows()).isZero();
    }

    @Test
    void preview_rowsOverLimit_returnsTooManyRowsError() {
        StringBuilder csv = new StringBuilder(header());
        java.util.UUID id = java.util.UUID.randomUUID();
        for (int i = 0; i <= 10_000; i++) {
            csv.append(id).append(",,,,,1.0,,,,\n");
        }

        ActivityImportPreviewDTO preview = importer.preview(csv.toString().getBytes());

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.TOO_MANY_ROWS);
    }
}
```

- [ ] **Step 2: Run tests to verify all fail**

Run: `./gradlew test --tests 'ActivityCsvImporterTest'`
Expected: compile error — `ActivityCsvImporter` doesn't exist.

- [ ] **Step 3: Create `ActivityCsvImporter.java` skeleton with file-level validation**

```java
package com.myhive.backend.service.activity;

import com.myhive.backend.dto.ActivityImportApplyRequest;
import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.dto.ActivityImportResultDTO;
import com.myhive.backend.exception.CsvImportException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
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
    static final java.time.Duration TOKEN_TTL = java.time.Duration.ofMinutes(10);

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
        } catch (Exception e) {
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
                               List<ActivityImportPreviewDTO.RowWarning> warnings) throws Exception {
        byte[] stripped = stripBom(bytes);
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(stripped), StandardCharsets.UTF_8);
             CSVReader csv = new CSVReaderBuilder(reader).build()) {

            String[] header = csv.readNext();
            if (header == null) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        0, ImportErrorCode.EMPTY_FILE, "File contains no header row", null));
                return null;
            }
            Map<String, Integer> headerIndex = new HashMap<>();
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
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests 'ActivityCsvImporterTest'`
Expected: all 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvImporter.java \
  myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvImporterTest.java
git commit -m "feat: add ActivityCsvImporter skeleton with file-level validation and CSV parsing"
```

---

## Task 5: `ActivityCsvImporter` — row-level validation

Add per-row validation: UUID parsing, required fields, numeric formats, duplicate ids, unknown categories. Still no DB match yet (that's the diff step).

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvImporter.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvImporterTest.java`

- [ ] **Step 1: Write failing tests**

Append to `ActivityCsvImporterTest.java`:

```java
    private String row(String id, String slug, String destSlug, String name, String desc,
                       String price, String duration, String categorySlugs,
                       String imageUrl, String includes) {
        return id + "," + slug + "," + destSlug + ",\"" + name + "\",\"" + desc + "\","
                + price + "," + duration + "," + categorySlugs + ","
                + imageUrl + ",\"" + includes + "\"\n";
    }

    @Test
    void preview_rowWithBlankId_producesMissingIdError() {
        when(activityRepository.findAllById(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        String csv = header() + row("", "s", "d", "n", "", "1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.MISSING_ID);
    }

    @Test
    void preview_rowWithBadUuid_producesInvalidUuidError() {
        when(activityRepository.findAllById(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        String csv = header() + row("not-a-uuid", "s", "d", "n", "", "1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.INVALID_UUID);
    }

    @Test
    void preview_duplicateIds_producesDuplicateIdError() {
        when(activityRepository.findAllById(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        java.util.UUID id = java.util.UUID.randomUUID();
        String csv = header()
                + row(id.toString(), "", "", "A", "", "1.00", "", "", "", "")
                + row(id.toString(), "", "", "B", "", "1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.DUPLICATE_ID);
    }

    @Test
    void preview_blankName_producesNameRequiredError() {
        when(activityRepository.findAllById(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        java.util.UUID id = java.util.UUID.randomUUID();
        String csv = header() + row(id.toString(), "", "", "", "", "1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.NAME_REQUIRED);
    }

    @Test
    void preview_priceWithComma_producesInvalidDecimalError() {
        when(activityRepository.findAllById(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        java.util.UUID id = java.util.UUID.randomUUID();
        String csv = header() + row(id.toString(), "", "", "N", "", "1,50", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.INVALID_DECIMAL);
    }

    @Test
    void preview_negativePrice_producesInvalidDecimalError() {
        when(activityRepository.findAllById(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        java.util.UUID id = java.util.UUID.randomUUID();
        String csv = header() + row(id.toString(), "", "", "N", "", "-1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.INVALID_DECIMAL);
    }

    @Test
    void preview_blankPrice_producesPriceRequiredError() {
        when(activityRepository.findAllById(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        java.util.UUID id = java.util.UUID.randomUUID();
        String csv = header() + row(id.toString(), "", "", "N", "", "", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.PRICE_REQUIRED);
    }

    @Test
    void preview_nonIntDuration_producesInvalidIntegerError() {
        when(activityRepository.findAllById(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        java.util.UUID id = java.util.UUID.randomUUID();
        String csv = header() + row(id.toString(), "", "", "N", "", "1.00", "abc", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.INVALID_INTEGER);
    }

    @Test
    void preview_unknownCategorySlug_producesUnknownCategoryError() {
        when(activityRepository.findAllById(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        when(categoryRepository.findBySlug("ghost")).thenReturn(Optional.empty());
        java.util.UUID id = java.util.UUID.randomUUID();
        String csv = header() + row(id.toString(), "", "", "N", "", "1.00", "", "ghost", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.UNKNOWN_CATEGORY);
    }

    @Test
    void preview_nameTooLong_producesFieldTooLongError() {
        when(activityRepository.findAllById(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        java.util.UUID id = java.util.UUID.randomUUID();
        String longName = "X".repeat(256);
        String csv = header() + row(id.toString(), "", "", longName, "", "1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.FIELD_TOO_LONG);
    }
```

Add `import java.util.Optional;` if missing.

- [ ] **Step 2: Run tests to verify new ones fail**

Run: `./gradlew test --tests 'ActivityCsvImporterTest'`
Expected: new tests FAIL (no validation yet).

- [ ] **Step 3: Implement per-row validation**

In `ActivityCsvImporter.java`:

**3a.** Add a new private record for the validated row, above `CachedPreview`:

```java
    private record ValidatedRow(
            int csvRowNumber,
            UUID activityId,
            String name,
            String description,
            java.math.BigDecimal price,
            Integer duration,
            List<UUID> categoryIds,
            List<String> categorySlugs,
            String includes,
            // read-only fields captured for warning comparison:
            String csvSlug,
            String csvDestinationSlug,
            String csvImageUrl
    ) {
    }
```

**3b.** Add this private method at the bottom of the class (before the closing brace):

```java
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
            java.math.BigDecimal price = null;
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
                    price = new java.math.BigDecimal(rawPrice);
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
```

**3c.** Update the `preview()` method body. Replace the TODO placeholder block with:

```java
        List<ValidatedRow> validated = validateRows(parsed.rows(), errors);
        // diff/token logic comes in Task 6
        return new ActivityImportPreviewDTO(
                null, parsed.rows().size(), 0, 0,
                errors.size(), warnings.size(),
                List.of(), errors, warnings
        );
```

Add `import java.util.Arrays;` at top if missing.

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests 'ActivityCsvImporterTest'`
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvImporter.java \
  myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvImporterTest.java
git commit -m "feat: add per-row validation to ActivityCsvImporter"
```

---

## Task 6: `ActivityCsvImporter` — diff computation and preview token

Look up each validated row's activity in the DB, compute diffs, detect read-only field changes (warning), and store parsed rows in the token cache when no errors.

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvImporter.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvImporterTest.java`

- [ ] **Step 1: Write failing tests**

Append to `ActivityCsvImporterTest.java`:

```java
    @Test
    void preview_unknownId_producesRowNotFoundError() {
        java.util.UUID missingId = java.util.UUID.randomUUID();
        when(activityRepository.findAllById(List.of(missingId))).thenReturn(List.of());
        String csv = header() + row(missingId.toString(), "", "", "N", "", "1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.ROW_NOT_FOUND);
    }

    @Test
    void preview_allFieldsUnchanged_producesNoDiffButIncrementsUnchanged() {
        com.myhive.backend.entity.Destination dest = TestDataFactory.destination();
        com.myhive.backend.entity.Activity existing = TestDataFactory.activity(dest);
        existing.setPrice(new java.math.BigDecimal("99.99"));
        when(activityRepository.findAllById(List.of(existing.getId())))
                .thenReturn(List.of(existing));

        String csv = header() + row(
                existing.getId().toString(),
                existing.getSlug(),
                dest.getSlug(),
                existing.getName(),
                existing.getDescription(),
                "99.99",
                existing.getDuration().toString(),
                "",
                existing.getImageUrl(),
                existing.getIncludes());

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.rowsUnchanged()).isEqualTo(1);
        assertThat(preview.rowsToUpdate()).isZero();
        assertThat(preview.changes()).isEmpty();
        assertThat(preview.token()).isNotNull();
    }

    @Test
    void preview_nameChanged_producesSingleFieldDiff() {
        com.myhive.backend.entity.Destination dest = TestDataFactory.destination();
        com.myhive.backend.entity.Activity existing = TestDataFactory.activity(dest);
        when(activityRepository.findAllById(List.of(existing.getId())))
                .thenReturn(List.of(existing));

        String csv = header() + row(
                existing.getId().toString(), existing.getSlug(), dest.getSlug(),
                "New Name", existing.getDescription(),
                existing.getPrice().toPlainString(),
                existing.getDuration().toString(),
                "", existing.getImageUrl(), existing.getIncludes());

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.rowsToUpdate()).isEqualTo(1);
        assertThat(preview.changes()).hasSize(1);
        ActivityImportPreviewDTO.RowDiff diff = preview.changes().getFirst();
        assertThat(diff.fieldChanges()).containsKey("name");
        assertThat(diff.fieldChanges().get("name").newValue()).isEqualTo("New Name");
        assertThat(preview.token()).isNotNull();
    }

    @Test
    void preview_readOnlyFieldChanged_producesWarningNotError() {
        com.myhive.backend.entity.Destination dest = TestDataFactory.destination();
        com.myhive.backend.entity.Activity existing = TestDataFactory.activity(dest);
        when(activityRepository.findAllById(List.of(existing.getId())))
                .thenReturn(List.of(existing));

        String csv = header() + row(
                existing.getId().toString(),
                "different-slug", // read-only, changed
                dest.getSlug(),
                existing.getName(), existing.getDescription(),
                existing.getPrice().toPlainString(),
                existing.getDuration().toString(),
                "", existing.getImageUrl(), existing.getIncludes());

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.warnings())
                .extracting(ActivityImportPreviewDTO.RowWarning::code)
                .contains(ImportErrorCode.READ_ONLY_FIELD_CHANGED);
        assertThat(preview.token()).isNotNull();
    }

    @Test
    void preview_errorsPresent_tokenIsNull() {
        when(activityRepository.findAllById(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        String csv = header() + row("", "", "", "N", "", "1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.token()).isNull();
    }
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew test --tests 'ActivityCsvImporterTest'`
Expected: new tests FAIL — diff not implemented, no token issued.

- [ ] **Step 3: Implement diff + token**

In `ActivityCsvImporter.java`:

**3a.** Replace the body of `preview()` from after `validateRows(...)` with:

```java
        List<ValidatedRow> validated = validateRows(parsed.rows(), errors);

        // Look up activities only for rows whose IDs parsed successfully
        List<UUID> idsToFetch = validated.stream().map(ValidatedRow::activityId).toList();
        Map<UUID, com.myhive.backend.entity.Activity> fromDb =
                activityRepository.findAllById(idsToFetch).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.myhive.backend.entity.Activity::getId, a -> a));

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
            com.myhive.backend.entity.Activity db = fromDb.get(v.activityId());
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
```

**3b.** Add these two new methods to the class:

```java
    private void addReadOnlyWarnings(
            ValidatedRow v,
            com.myhive.backend.entity.Activity db,
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
            ValidatedRow v, com.myhive.backend.entity.Activity db) {
        Map<String, ActivityImportPreviewDTO.FieldChange> changes = new java.util.LinkedHashMap<>();
        if (!Objects.equals(nullToEmpty(db.getName()), v.name())) {
            changes.put("name", new ActivityImportPreviewDTO.FieldChange(db.getName(), v.name()));
        }
        if (!Objects.equals(nullToEmpty(db.getDescription()), v.description())) {
            changes.put("description", new ActivityImportPreviewDTO.FieldChange(db.getDescription(), v.description()));
        }
        java.math.BigDecimal dbPrice = db.getPrice() == null ? null
                : db.getPrice().setScale(2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal csvPrice = v.price() == null ? null
                : v.price().setScale(2, java.math.RoundingMode.HALF_UP);
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
                    .map(com.myhive.backend.entity.Category::getSlug)
                    .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        Set<String> csvSlugs = new java.util.TreeSet<>(v.categorySlugs());
        if (!dbSlugs.equals(csvSlugs)) {
            changes.put("category_slugs", new ActivityImportPreviewDTO.FieldChange(
                    String.join(";", dbSlugs), String.join(";", csvSlugs)));
        }
        return changes;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
```

Add `import java.util.Objects;` if missing.

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests 'ActivityCsvImporterTest'`
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvImporter.java \
  myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvImporterTest.java
git commit -m "feat: compute diffs and issue preview token for activities CSV import"
```

---

## Task 7: `ActivityCsvImporter.apply()` — transactional write

Consume the token, re-validate against current DB state, apply updates in one transaction; on any mismatch, roll back with `STATE_CHANGED`.

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvImporter.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvImporterTest.java`

- [ ] **Step 1: Write failing tests**

Append to `ActivityCsvImporterTest.java`:

```java
    @Test
    void apply_unknownToken_throwsTokenNotFound() {
        ActivityImportApplyRequest req = new ActivityImportApplyRequest(java.util.UUID.randomUUID().toString());

        assertThatThrownBy(() -> importer.apply(req))
                .isInstanceOf(com.myhive.backend.exception.CsvImportException.class)
                .satisfies(e -> assertThat(
                        ((com.myhive.backend.exception.CsvImportException) e).getCode())
                        .isEqualTo(com.myhive.backend.exception.CsvImportException.Code.TOKEN_NOT_FOUND));
    }

    @Test
    void apply_validToken_persistsChangesAndInvalidatesToken() {
        com.myhive.backend.entity.Destination dest = TestDataFactory.destination();
        com.myhive.backend.entity.Activity existing = TestDataFactory.activity(dest);
        when(activityRepository.findAllById(List.of(existing.getId()))).thenReturn(List.of(existing));
        when(activityRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(activityRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        String csv = header() + row(
                existing.getId().toString(), existing.getSlug(), dest.getSlug(),
                "Renamed", existing.getDescription(),
                existing.getPrice().toPlainString(),
                existing.getDuration().toString(),
                "", existing.getImageUrl(), existing.getIncludes());
        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());
        assertThat(preview.token()).isNotNull();

        ActivityImportResultDTO result = importer.apply(new ActivityImportApplyRequest(preview.token()));

        assertThat(result.rowsUpdated()).isEqualTo(1);
        assertThat(existing.getName()).isEqualTo("Renamed");

        // second apply with the same token is rejected
        assertThatThrownBy(() ->
                importer.apply(new ActivityImportApplyRequest(preview.token())))
                .isInstanceOf(com.myhive.backend.exception.CsvImportException.class);
    }

    @Test
    void apply_activityDeletedBetweenPreviewAndApply_throwsStateChanged() {
        com.myhive.backend.entity.Destination dest = TestDataFactory.destination();
        com.myhive.backend.entity.Activity existing = TestDataFactory.activity(dest);
        when(activityRepository.findAllById(List.of(existing.getId()))).thenReturn(List.of(existing));

        String csv = header() + row(
                existing.getId().toString(), existing.getSlug(), dest.getSlug(),
                "Renamed", existing.getDescription(),
                existing.getPrice().toPlainString(),
                existing.getDuration().toString(),
                "", existing.getImageUrl(), existing.getIncludes());
        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        // simulate deletion
        when(activityRepository.findById(existing.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                importer.apply(new ActivityImportApplyRequest(preview.token())))
                .isInstanceOf(com.myhive.backend.exception.CsvImportException.class)
                .satisfies(e -> assertThat(
                        ((com.myhive.backend.exception.CsvImportException) e).getCode())
                        .isEqualTo(com.myhive.backend.exception.CsvImportException.Code.STATE_CHANGED));
    }

    @Test
    void apply_expiredToken_throwsTokenExpired() {
        // use reflection to backdate an entry
        com.myhive.backend.entity.Destination dest = TestDataFactory.destination();
        com.myhive.backend.entity.Activity existing = TestDataFactory.activity(dest);
        when(activityRepository.findAllById(List.of(existing.getId()))).thenReturn(List.of(existing));

        String csv = header() + row(
                existing.getId().toString(), existing.getSlug(), dest.getSlug(),
                "Renamed", existing.getDescription(),
                existing.getPrice().toPlainString(),
                existing.getDuration().toString(),
                "", existing.getImageUrl(), existing.getIncludes());
        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        // backdate via package-private helper
        importer.expireTokenForTest(java.util.UUID.fromString(preview.token()));

        assertThatThrownBy(() ->
                importer.apply(new ActivityImportApplyRequest(preview.token())))
                .isInstanceOf(com.myhive.backend.exception.CsvImportException.class)
                .satisfies(e -> assertThat(
                        ((com.myhive.backend.exception.CsvImportException) e).getCode())
                        .isEqualTo(com.myhive.backend.exception.CsvImportException.Code.TOKEN_EXPIRED));
    }
```

Add imports if missing:
```java
import com.myhive.backend.dto.ActivityImportApplyRequest;
import com.myhive.backend.dto.ActivityImportResultDTO;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests 'ActivityCsvImporterTest'`
Expected: new tests FAIL.

- [ ] **Step 3: Implement `apply()`**

In `ActivityCsvImporter.java`:

**3a.** Replace the stub `apply(...)` with:

```java
    @Transactional
    public ActivityImportResultDTO apply(ActivityImportApplyRequest request) {
        UUID token;
        try {
            token = UUID.fromString(request.token());
        } catch (IllegalArgumentException e) {
            throw new CsvImportException(CsvImportException.Code.TOKEN_NOT_FOUND,
                    "Invalid token format");
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

        int updated = 0;
        for (ValidatedRow v : cached.rows()) {
            com.myhive.backend.entity.Activity activity = activityRepository.findById(v.activityId())
                    .orElseThrow(() -> new CsvImportException(
                            CsvImportException.Code.STATE_CHANGED,
                            "Activity " + v.activityId() + " no longer exists (row "
                                    + v.csvRowNumber() + ")"));

            Set<com.myhive.backend.entity.Category> categories = new java.util.HashSet<>();
            for (String slug : v.categorySlugs()) {
                com.myhive.backend.entity.Category cat = categoryRepository.findBySlug(slug)
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
        return new ActivityImportResultDTO(updated, Instant.now());
    }
```

**3b.** Add a package-private test hook right before the final closing brace of the class:

```java
    /** Visible for testing: force a token's expiry into the past. */
    void expireTokenForTest(UUID token) {
        CachedPreview c = tokenCache.get(token);
        if (c != null) {
            tokenCache.put(token, new CachedPreview(c.rows(), Instant.now().minusSeconds(1)));
        }
    }
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests 'ActivityCsvImporterTest'`
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvImporter.java \
  myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvImporterTest.java
git commit -m "feat: apply activities CSV import transactionally with token invalidation and state-change detection"
```

---

## Task 8: Wire controller endpoints and tighten security

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/config/SecurityConfig.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/controller/ActivityCsvImportExportIntegrationTest.java` (new file)

- [ ] **Step 1: Write failing integration test**

Create `ActivityCsvImportExportIntegrationTest.java`:

```java
package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

import static com.myhive.backend.util.JwtTestHelper.adminJwt;
import static com.myhive.backend.util.JwtTestHelper.managerJwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestSecurityConfig.class)
class ActivityCsvImportExportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DestinationRepository destinationRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private Destination destination;
    private Activity activity;
    private Category beach;

    @BeforeEach
    void setUp() {
        destination = new Destination();
        destination.setName("Bali");
        destination.setSlug("bali");
        destination.setCountry("Indonesia");
        destination = destinationRepository.save(destination);

        beach = new Category();
        beach.setName("Beach");
        beach.setSlug("beach");
        beach = categoryRepository.save(beach);

        activity = new Activity();
        activity.setDestination(destination);
        activity.setSlug("surf");
        activity.setName("Surf lesson");
        activity.setDescription("Old desc");
        activity.setPrice(new BigDecimal("50.00"));
        activity.setDuration(90);
        activity.setIncludes("Board");
        activity.setCategories(new java.util.HashSet<>(Set.of(beach)));
        activity = activityRepository.save(activity);
    }

    @Test
    void export_asAdmin_returnsCsv() throws Exception {
        mockMvc.perform(get("/admin/activities/export").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Surf lesson")));
    }

    @Test
    void export_asManager_returnsForbidden() throws Exception {
        mockMvc.perform(get("/admin/activities/export").with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void export_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/activities/export"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void importRoundTrip_descriptionChange_persists() throws Exception {
        MvcResult exportResult = mockMvc.perform(get("/admin/activities/export").with(adminJwt()))
                .andExpect(status().isOk()).andReturn();
        String csv = exportResult.getResponse().getContentAsString();

        // swap old desc for new
        String edited = csv.replace("Old desc", "New desc");
        MockMultipartFile file = new MockMultipartFile(
                "file", "activities.csv", "text/csv", edited.getBytes());

        MvcResult previewResult = mockMvc.perform(multipart("/admin/activities/import/preview")
                        .file(file).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.rowsToUpdate").value(1))
                .andReturn();

        JsonNode node = objectMapper.readTree(previewResult.getResponse().getContentAsString());
        String token = node.get("token").asText();

        mockMvc.perform(post("/admin/activities/import/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsUpdated").value(1));

        Activity reloaded = activityRepository.findById(activity.getId()).orElseThrow();
        assertThat(reloaded.getDescription()).isEqualTo("New desc");
    }

    @Test
    void importPreview_asManager_returnsForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "activities.csv", "text/csv", "id\n".getBytes());
        mockMvc.perform(multipart("/admin/activities/import/preview")
                        .file(file).with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void importApply_expiredToken_returnsBadRequestWithCode() throws Exception {
        mockMvc.perform(post("/admin/activities/import/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + java.util.UUID.randomUUID() + "\"}")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TOKEN_NOT_FOUND"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'ActivityCsvImportExportIntegrationTest'`
Expected: FAIL — endpoints don't exist, 404.

- [ ] **Step 3: Tighten `SecurityConfig`**

In `SecurityConfig.java`, replace the admin authorization block with:

```java
                        // Admin endpoints — CSV import/export: ADMIN only (must come before /admin/activities/**)
                        .requestMatchers("/admin/activities/export").hasRole("ADMIN")
                        .requestMatchers("/admin/activities/import/**").hasRole("ADMIN")
                        // Admin endpoints — activities & blog: ADMIN or MANAGER
                        .requestMatchers("/admin/activities/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/admin/blog/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/admin/upload").hasAnyRole("ADMIN", "MANAGER")
                        // Admin endpoints — everything else: ADMIN only
                        .requestMatchers("/admin/**").hasRole("ADMIN")
```

- [ ] **Step 4: Add endpoints to `AdminController`**

In `AdminController.java`:

**4a.** Add imports at the top:

```java
import com.myhive.backend.service.activity.ActivityCsvExporter;
import com.myhive.backend.service.activity.ActivityCsvImporter;
import org.springframework.http.HttpHeaders;
```

**4b.** Declare new injected fields alongside the existing services:

```java
    private final ActivityCsvExporter activityCsvExporter;
    private final ActivityCsvImporter activityCsvImporter;
```

**4c.** Add endpoint methods after the existing `deleteActivity(...)` method:

```java
    @GetMapping(value = "/activities/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportActivities() {
        String csv = activityCsvExporter.exportAll();
        byte[] body = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = "activities-" + java.time.LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8")
                .body(body);
    }

    @PostMapping("/activities/import/preview")
    public ResponseEntity<ActivityImportPreviewDTO> previewActivityImport(
            @RequestParam("file") MultipartFile file) throws IOException {
        byte[] bytes = file == null ? new byte[0] : file.getBytes();
        return ResponseEntity.ok(activityCsvImporter.preview(bytes));
    }

    @PostMapping("/activities/import/apply")
    public ResponseEntity<ActivityImportResultDTO> applyActivityImport(
            @Valid @RequestBody ActivityImportApplyRequest request) {
        return ResponseEntity.ok(activityCsvImporter.apply(request));
    }
```

**4d.** Add the DTO imports at the top of the file:

```java
import com.myhive.backend.dto.ActivityImportApplyRequest;
import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.dto.ActivityImportResultDTO;
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test --tests 'ActivityCsvImportExportIntegrationTest'`
Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java \
  myhive-backend/src/main/java/com/myhive/backend/config/SecurityConfig.java \
  myhive-backend/src/test/java/com/myhive/backend/controller/ActivityCsvImportExportIntegrationTest.java
git commit -m "feat: expose /admin/activities/export + /import endpoints, restricted to ADMIN"
```

---

## Task 9: Full backend regression run

Catch any unintended breakage in neighbouring tests.

- [ ] **Step 1: Run the whole test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: If any unrelated test fails, fix it and commit**

Common breakage to check: order of SecurityConfig matchers; any test asserting specific header behavior on `/admin/activities/**`.

- [ ] **Step 3: If no changes needed, skip commit and move on**

---

## Task 10: Frontend — `adminApi.js` methods

**Files:**
- Modify: `myhive-react-app/src/services/adminApi.js`

No dedicated test — will be exercised via the UI and the backend integration tests already cover the wire format.

- [ ] **Step 1: Add three methods to the returned object in `createAdminApi`**

Insert these methods in `adminApi.js` right after `deleteActivity`:

```javascript
        async exportActivitiesCsv() {
            const token = await getAccessToken();
            const response = await fetch(`${API_BASE_URL}/admin/activities/export`, {
                headers: {Authorization: `Bearer ${token}`},
            });
            await handleError(response, 'Failed to export activities');
            const blob = await response.blob();
            const filename = `activities-${new Date().toISOString().slice(0, 10)}.csv`;
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
        },

        async previewActivityImport(file) {
            const token = await getAccessToken();
            const formData = new FormData();
            formData.append('file', file);
            const response = await fetch(`${API_BASE_URL}/admin/activities/import/preview`, {
                method: 'POST',
                headers: {Authorization: `Bearer ${token}`},
                body: formData,
            });
            await handleError(response, 'Failed to preview import');
            return response.json();
        },

        async applyActivityImport(importToken) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/activities/import/apply`, {
                method: 'POST',
                headers,
                body: JSON.stringify({token: importToken}),
            });
            await handleError(response, 'Failed to apply import');
            return response.json();
        },
```

- [ ] **Step 2: Commit**

```bash
git add myhive-react-app/src/services/adminApi.js
git commit -m "feat: add export/preview/apply activity CSV methods to adminApi"
```

---

## Task 11: Frontend — `ImportActivitiesModal`

**Files:**
- Create: `myhive-react-app/src/components/admin/ImportActivitiesModal.js`

- [ ] **Step 1: Create the modal component**

```jsx
import {useCallback, useState} from 'react';
import {Alert, Badge, Button, Form, Modal, Spinner, Table} from 'react-bootstrap';

const STEP_UPLOAD = 'upload';
const STEP_REVIEW = 'review';
const STEP_RESULT = 'result';

function fieldLabel(key) {
    return {
        name: 'Name',
        description: 'Description',
        price: 'Price',
        duration: 'Duration',
        includes: 'Includes',
        category_slugs: 'Categories',
    }[key] || key;
}

function formatValue(v) {
    if (v === null || v === undefined || v === '') {
        return <span className="text-muted fst-italic">empty</span>;
    }
    return String(v);
}

function ImportActivitiesModal({show, onHide, adminApi, onImported}) {
    const [step, setStep] = useState(STEP_UPLOAD);
    const [file, setFile] = useState(null);
    const [preview, setPreview] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [result, setResult] = useState(null);

    const reset = useCallback(() => {
        setStep(STEP_UPLOAD);
        setFile(null);
        setPreview(null);
        setError('');
        setResult(null);
    }, []);

    const handleClose = () => {
        reset();
        onHide();
    };

    const handlePreview = async () => {
        if (!file) {
            return;
        }
        setLoading(true);
        setError('');
        try {
            const p = await adminApi.previewActivityImport(file);
            setPreview(p);
            setStep(STEP_REVIEW);
        } catch (e) {
            setError(e.message || 'Failed to preview');
        } finally {
            setLoading(false);
        }
    };

    const handleApply = async () => {
        setLoading(true);
        setError('');
        try {
            const r = await adminApi.applyActivityImport(preview.token);
            setResult(r);
            setStep(STEP_RESULT);
        } catch (e) {
            setError(e.message || 'Failed to apply');
        } finally {
            setLoading(false);
        }
    };

    const handleFinish = () => {
        if (onImported) {
            onImported();
        }
        handleClose();
    };

    const canApply = preview
        && preview.token
        && preview.rowsWithErrors === 0
        && preview.rowsToUpdate > 0;

    return (
        <Modal show={show} onHide={handleClose} size="lg" centered>
            <Modal.Header closeButton>
                <Modal.Title className="fs-5">Import activities from CSV</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                {error && <Alert variant="danger" dismissible onClose={() => setError('')}>{error}</Alert>}

                {step === STEP_UPLOAD && (
                    <>
                        <p className="small text-muted">
                            Upload the CSV you exported (and possibly edited). Only existing activities matched by
                            <code className="mx-1">id</code> will be updated. Read-only fields
                            (<code>slug</code>, <code>destination_slug</code>, <code>image_url</code>) are ignored.
                        </p>
                        <Form.Control
                            type="file"
                            accept=".csv,text/csv"
                            onChange={(e) => setFile(e.target.files?.[0] || null)}
                        />
                    </>
                )}

                {step === STEP_REVIEW && preview && (
                    <>
                        <div className="d-flex gap-3 mb-3">
                            <Badge bg="primary">{preview.rowsToUpdate} to update</Badge>
                            <Badge bg="secondary">{preview.rowsUnchanged} unchanged</Badge>
                            {preview.rowsWithWarnings > 0 && (
                                <Badge bg="warning" text="dark">{preview.rowsWithWarnings} warnings</Badge>
                            )}
                            {preview.rowsWithErrors > 0 && (
                                <Badge bg="danger">{preview.rowsWithErrors} errors</Badge>
                            )}
                        </div>

                        {preview.errors.length > 0 && (
                            <Alert variant="danger">
                                <strong>Fix these errors and re-upload:</strong>
                                <ul className="mb-0 small">
                                    {preview.errors.map((err, i) => (
                                        <li key={i}>
                                            Row {err.csvRowNumber} [{err.code}]
                                            {err.field ? ` (${err.field})` : ''}: {err.message}
                                        </li>
                                    ))}
                                </ul>
                            </Alert>
                        )}

                        {preview.warnings.length > 0 && (
                            <Alert variant="warning">
                                <ul className="mb-0 small">
                                    {preview.warnings.map((w, i) => (
                                        <li key={i}>
                                            Row {w.csvRowNumber} [{w.code}]
                                            {w.field ? ` (${w.field})` : ''}: {w.message}
                                        </li>
                                    ))}
                                </ul>
                            </Alert>
                        )}

                        {preview.changes.length > 0 && (
                            <div style={{maxHeight: 320, overflowY: 'auto'}}>
                                <Table size="sm" hover>
                                    <thead>
                                    <tr>
                                        <th>Row</th>
                                        <th>Activity</th>
                                        <th>Field</th>
                                        <th>Before</th>
                                        <th>After</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {preview.changes.flatMap((diff) =>
                                        Object.entries(diff.fieldChanges).map(([field, change]) => (
                                            <tr key={`${diff.activityId}-${field}`}>
                                                <td className="small">{diff.csvRowNumber}</td>
                                                <td className="small">{diff.activityName}</td>
                                                <td className="small fw-semibold">{fieldLabel(field)}</td>
                                                <td className="small text-muted">{formatValue(change.oldValue)}</td>
                                                <td className="small">{formatValue(change.newValue)}</td>
                                            </tr>
                                        ))
                                    )}
                                    </tbody>
                                </Table>
                            </div>
                        )}
                    </>
                )}

                {step === STEP_RESULT && result && (
                    <Alert variant="success" className="mb-0">
                        Updated {result.rowsUpdated} {result.rowsUpdated === 1 ? 'activity' : 'activities'}.
                    </Alert>
                )}
            </Modal.Body>
            <Modal.Footer>
                {step === STEP_UPLOAD && (
                    <>
                        <Button variant="outline-secondary" onClick={handleClose}>Cancel</Button>
                        <Button variant="primary" onClick={handlePreview} disabled={!file || loading}>
                            {loading ? <Spinner animation="border" size="sm"/> : 'Preview'}
                        </Button>
                    </>
                )}
                {step === STEP_REVIEW && (
                    <>
                        <Button variant="outline-secondary" onClick={reset}>Back</Button>
                        <Button variant="primary" onClick={handleApply} disabled={!canApply || loading}>
                            {loading ? <Spinner animation="border" size="sm"/>
                                : `Apply ${preview.rowsToUpdate} change${preview.rowsToUpdate === 1 ? '' : 's'}`}
                        </Button>
                    </>
                )}
                {step === STEP_RESULT && (
                    <Button variant="primary" onClick={handleFinish}>Close</Button>
                )}
            </Modal.Footer>
        </Modal>
    );
}

export default ImportActivitiesModal;
```

- [ ] **Step 2: Commit**

```bash
git add myhive-react-app/src/components/admin/ImportActivitiesModal.js
git commit -m "feat: add ImportActivitiesModal for CSV import preview + apply"
```

---

## Task 12: Wire buttons into `AdminActivities.js`

**Files:**
- Modify: `myhive-react-app/src/pages/AdminActivities.js`

- [ ] **Step 1: Add imports and state**

Add this import near the top of the file (after the existing imports):

```javascript
import ImportActivitiesModal from '../components/admin/ImportActivitiesModal';
```

Inside the `AdminActivities` function, after the existing `useState` lines for filters, add:

```javascript
    const [showImportModal, setShowImportModal] = useState(false);

    const handleExport = async () => {
        setError('');
        try {
            await adminApi.exportActivitiesCsv();
        } catch (e) {
            setError(e.message || 'Failed to export activities');
        }
    };
```

- [ ] **Step 2: Add buttons to the header**

Replace the header `<div className="d-flex gap-2">...</div>` block with:

```jsx
                <div className="d-flex gap-2">
                    <Button variant="outline-secondary" size="sm" onClick={fetchData}>Refresh</Button>
                    <Button variant="outline-secondary" size="sm" onClick={handleExport}>Export CSV</Button>
                    <Button variant="outline-secondary" size="sm" onClick={() => setShowImportModal(true)}>
                        Import CSV
                    </Button>
                    <Button variant="primary" size="sm" onClick={openCreate}>+ Add Activity</Button>
                </div>
```

- [ ] **Step 3: Render the modal**

Right before the closing `</>` at the end of the returned JSX (after `<DeleteConfirmModal .../>`), add:

```jsx
            <ImportActivitiesModal
                show={showImportModal}
                onHide={() => setShowImportModal(false)}
                adminApi={adminApi}
                onImported={fetchData}
            />
```

- [ ] **Step 4: Manual smoke test**

Start the backend:
```bash
cd myhive-backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
```
In another terminal:
```bash
cd myhive-react-app && npm start
```

1. Log in to `/admin` as ADMIN.
2. Go to Activities.
3. Click Export CSV → file downloads, opens in Excel without encoding issues, includes every activity.
4. Edit one description, save as UTF-8.
5. Click Import CSV → upload file → Preview → confirm the diff shows your edit → Apply → refresh activities list and verify the description changed.
6. Try uploading a file with a bad UUID → should show an error in the review step with Apply disabled.

Expected: all 6 checks pass.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/pages/AdminActivities.js
git commit -m "feat: expose CSV import/export buttons on AdminActivities page"
```

---

## Task 13: Optional — `TestDataFactory` helper for future CSV tests

Only do this if writing more round-trip tests; if not used, skip.

**Files:**
- Modify: `myhive-backend/src/test/java/com/myhive/backend/TestDataFactory.java`

- [ ] **Step 1: Add a helper that builds an activity with N named categories**

Append to `TestDataFactory.java`:

```java
    public static Activity activityWithCategories(Destination destination, String... categorySlugs) {
        Activity a = activity(destination);
        java.util.Set<Category> categories = new java.util.HashSet<>();
        for (String slug : categorySlugs) {
            Category c = new Category();
            c.setId(UUID.randomUUID());
            c.setSlug(slug);
            c.setName(slug.substring(0, 1).toUpperCase() + slug.substring(1));
            c.setCreatedAt(LocalDateTime.now());
            categories.add(c);
        }
        a.setCategories(categories);
        return a;
    }
```

- [ ] **Step 2: Run the full test suite to confirm nothing is broken**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add myhive-backend/src/test/java/com/myhive/backend/TestDataFactory.java
git commit -m "test: add TestDataFactory helper for activities with named categories"
```

---

## Task 14: Update CLAUDE.md and memory docs

Per the project's workflow rules (`CLAUDE.md`): after the user approves, update docs that describe architecture/services.

**Files:**
- Modify: `CLAUDE.md` — add the new endpoints to the "Key Architectural Patterns" section
- Modify: `C:/Users/dijtb/.claude/projects/C--Users-dijtb-IdeaProjects-myhive-travel-app/memory/project_overview.md` — mention CSV import/export
- (Optional) `README.md` if it documents admin features

- [ ] **Step 1: Update CLAUDE.md**

In the **"Key Architectural Patterns"** section, add a new bullet:

```markdown
- **CSV import/export for activities**: Admin-only bulk editing via `GET /admin/activities/export` (UTF-8 BOM, RFC 4180, formula-injection safe) and a two-step `POST /admin/activities/import/preview` → `POST /admin/activities/import/apply` flow. Preview issues a 10-minute one-shot token; apply re-validates against current DB and commits transactionally (all-or-nothing).
```

- [ ] **Step 2: Update `project_overview.md`**

Add a corresponding short note so future sessions know the pattern exists.

- [ ] **Step 3: Commit docs**

```bash
git add CLAUDE.md
git commit -m "docs: document activities CSV import/export in CLAUDE.md"
```

(Memory file is under the user home, not the repo — no git commit needed.)

---

## Self-Review Notes

All spec requirements map to tasks:

- CSV format (header, delimiter, encoding, BOM, escaping, formula defense) → Task 3
- API endpoints → Task 8
- File-level validation → Task 4
- Row-level validation (every error code) → Task 5
- Read-only field warnings → Task 6
- Diff computation, `NO_CHANGES`, token issuance → Task 6
- Apply transaction, re-validation, state-change detection, token one-shot use → Task 7
- Authorization (`ADMIN` only for new endpoints, MANAGER rejection) → Task 8
- UI three-step modal → Tasks 10–12
- Exception-to-HTTP mapping → Task 2
- End-to-end round-trip tested → Task 8
- Docs updated → Task 14

No placeholders. Type names are consistent across tasks: `ActivityImportPreviewDTO`, `ActivityImportResultDTO`, `ActivityImportApplyRequest`, `ImportErrorCode`, `CsvImportException`, `ActivityCsvExporter`, `ActivityCsvImporter`.
