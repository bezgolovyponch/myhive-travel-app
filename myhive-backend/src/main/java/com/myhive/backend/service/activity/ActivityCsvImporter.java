package com.myhive.backend.service.activity;

import com.myhive.backend.dto.ActivityImportApplyRequest;
import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.dto.ActivityImportResultDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.exception.CsvImportException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ActivityCsvImporter {

    // The file-size cap and the parser's row cap protect different things:
    // MAX_FILE_BYTES bounds peak network/parser memory; ActivityCsvParser.MAX_ROWS
    // bounds worst-case cached preview size (each ValidatedRow holds up to
    // ~20KB of strings, so the effective per-token memory ceiling is
    // MAX_FILE_BYTES * ~2 for String/object overhead, NOT MAX_ROWS *
    // 20KB — the file cap binds first in practice).
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private final ActivityRepository activityRepository;
    private final CategoryRepository categoryRepository;
    private final ActivityCsvParser parser;
    private final ActivityCsvRowValidator validator;
    private final ActivityCsvDiffer differ;
    private final PreviewTokenCache tokenCache;

    public ActivityCsvImporter(ActivityRepository activityRepository,
                                CategoryRepository categoryRepository) {
        this.activityRepository = activityRepository;
        this.categoryRepository = categoryRepository;
        this.parser = new ActivityCsvParser();
        this.validator = new ActivityCsvRowValidator(categoryRepository);
        this.differ = new ActivityCsvDiffer();
        this.tokenCache = new PreviewTokenCache();
    }

    public ActivityImportPreviewDTO preview(byte[] fileContent) {
        tokenCache.evictExpired();
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

        ActivityCsvParser.Result parsed = parser.parse(fileContent);
        errors.addAll(parsed.errors());
        warnings.addAll(parsed.warnings());
        if (parsed.isFatal()) {
            return emptyPreview(errors, warnings);
        }

        ActivityCsvRowValidator.Result validated = validator.validate(parsed.rows());
        errors.addAll(validated.errors());

        // Look up activities only for rows whose IDs parsed successfully
        List<UUID> idsToFetch = validated.rows().stream().map(ValidatedRow::activityId).toList();
        Map<UUID, Activity> fromDb = activityRepository.findAllById(idsToFetch).stream()
                .collect(Collectors.toMap(Activity::getId, a -> a));

        ActivityCsvDiffer.Result diffResult = differ.diff(validated.rows(), fromDb);
        errors.addAll(diffResult.errors());
        warnings.addAll(diffResult.warnings());

        String token = errors.isEmpty() ? tokenCache.store(diffResult.changedRows()) : null;

        return new ActivityImportPreviewDTO(
                token,
                parsed.rows().size(),
                diffResult.diffs().size(),
                diffResult.unchangedCount(),
                errors.size(),
                warnings.size(),
                diffResult.diffs(),
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

        PreviewTokenCache.Entry cached = tokenCache.consume(token)
                .orElseThrow(() -> new CsvImportException(CsvImportException.Code.TOKEN_NOT_FOUND,
                        "Preview token not found or already used"));
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
            // Optional column: null means "column absent from CSV — do not touch".
            if (v.featuredWeight() != null) {
                activity.setFeaturedWeight(v.featuredWeight());
            }
            activityRepository.save(activity);
            updated++;
        }
        log.info("Activity CSV import applied: principal={} rowsUpdated={} activityIds={}",
                principal, updated,
                cached.rows().stream().map(ValidatedRow::activityId).toList());
        return new ActivityImportResultDTO(updated, Instant.now());
    }

    private ActivityImportPreviewDTO emptyPreview(
            List<ActivityImportPreviewDTO.RowError> errors,
            List<ActivityImportPreviewDTO.RowWarning> warnings) {
        return new ActivityImportPreviewDTO(
                null, 0, 0, 0, errors.size(), warnings.size(),
                List.of(), errors, warnings);
    }

    /** Visible for testing: force a token's expiry into the past. */
    void expireTokenForTest(UUID token) {
        tokenCache.expireTokenForTest(token);
    }

    /** Visible for testing: clear the entire preview token cache. */
    public void clearCacheForTest() {
        tokenCache.clearForTest();
    }

    private String principalName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "<unknown>" : auth.getName();
    }
}
