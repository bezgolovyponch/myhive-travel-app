package com.myhive.backend.service.activity;

import com.myhive.backend.dto.ActivityImportApplyRequest;
import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.dto.ActivityImportResultDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.exception.CsvImportException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.service.ImageUploadService;
import com.myhive.backend.service.RemoteImageFetcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the two-step CSV import: {@link #preview} parses, validates and diffs a file
 * without writing anything and hands back a one-shot token; {@link #apply} consumes the
 * token, re-hosts any remote images, then delegates the DB writes to
 * {@link ActivityImportWriter} in one transaction.
 *
 * <p>Rows with an id update the matching activity; rows with a blank id create one.
 */
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

    /**
     * Remote images are downloaded synchronously inside apply(), one after another, so this
     * bounds how many a single request may re-host (~1s per image against a well-behaved host).
     */
    static final int MAX_REMOTE_IMAGES = 50;

    /**
     * Wall-clock budget for re-hosting all images of one apply(). Cloudflare/Render cut the
     * client off at ~100s; committing after the browser has given up would leave the admin
     * unsure whether to retry, so the import fails loudly (before any DB write) instead.
     */
    static final Duration REHOST_BUDGET = Duration.ofSeconds(60);

    private final ActivityRepository activityRepository;
    private final ActivityImportWriter writer;
    private final Optional<ImageUploadService> imageUploadService;
    private final RemoteImageFetcher remoteImageFetcher;
    private final ActivityCsvParser parser;
    private final ActivityCsvRowValidator validator;
    private final ActivityCsvDiffer differ;
    private final PreviewTokenCache tokenCache;
    private Clock clock = Clock.systemUTC();

    public ActivityCsvImporter(ActivityRepository activityRepository,
                               CategoryRepository categoryRepository,
                               DestinationRepository destinationRepository,
                               ActivityImportWriter writer,
                               Optional<ImageUploadService> imageUploadService,
                               RemoteImageFetcher remoteImageFetcher) {
        this.activityRepository = activityRepository;
        this.writer = writer;
        this.imageUploadService = imageUploadService;
        this.remoteImageFetcher = remoteImageFetcher;
        this.parser = new ActivityCsvParser();
        this.validator = new ActivityCsvRowValidator(categoryRepository, destinationRepository);
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
        warnings.addAll(validated.warnings());

        List<ValidatedRow> updates = validated.rows().stream().filter(r -> !r.isNew()).toList();
        List<ValidatedRow> creates = validated.rows().stream().filter(ValidatedRow::isNew).toList();

        List<UUID> idsToFetch = updates.stream().map(ValidatedRow::activityId).toList();
        Map<UUID, Activity> fromDb = activityRepository.findAllById(idsToFetch).stream()
                .collect(Collectors.toMap(Activity::getId, a -> a));
        ActivityCsvDiffer.Result diffResult = differ.diff(updates, fromDb);
        errors.addAll(diffResult.errors());
        warnings.addAll(diffResult.warnings());

        checkCreates(creates, errors, warnings);

        List<ValidatedRow> rowsToApply = new ArrayList<>(diffResult.changedRows());
        rowsToApply.addAll(creates);
        String token = errors.isEmpty() ? tokenCache.store(rowsToApply) : null;

        return new ActivityImportPreviewDTO(
                token,
                parsed.rows().size(),
                diffResult.diffs().size(),
                creates.size(),
                diffResult.unchangedCount(),
                errors.size(),
                warnings.size(),
                diffResult.diffs(),
                creates.stream().map(ActivityCsvImporter::toRowCreate).toList(),
                errors,
                warnings);
    }

    /**
     * Create-only checks that need the DB or the upload configuration. A custom slug that is
     * already taken is a warning (it gets suffixed). A name that already exists in the
     * destination is an error: the likeliest cause is an exported file whose id column was
     * blanked by a spreadsheet round-trip, and applying it would duplicate the whole catalog.
     * Remote images that cannot be re-hosted are errors too (apply would fail anyway).
     */
    private void checkCreates(List<ValidatedRow> creates,
                              List<ActivityImportPreviewDTO.RowError> errors,
                              List<ActivityImportPreviewDTO.RowWarning> warnings) {
        Set<String> slugsInFile = new HashSet<>();
        int remoteImages = 0;
        for (ValidatedRow row : creates) {
            if (!row.csvSlug().isEmpty()) {
                boolean takenInFile = !slugsInFile.add(row.csvSlug());
                if (takenInFile || activityRepository.existsBySlug(row.csvSlug())) {
                    warnings.add(new ActivityImportPreviewDTO.RowWarning(
                            row.csvRowNumber(), ImportErrorCode.SLUG_TAKEN,
                            "slug '" + row.csvSlug() + "' is already taken; a numeric suffix will be added",
                            "slug"));
                }
            }
            if (activityRepository.existsByDestinationSlugAndNameIgnoreCase(row.csvDestinationSlug(), row.name())) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        row.csvRowNumber(), ImportErrorCode.NAME_EXISTS,
                        "an activity named '" + row.name() + "' already exists in destination '"
                                + row.csvDestinationSlug() + "' — fill in its id to update it, or rename",
                        "name"));
            }
            if (needsRehosting(row)) {
                remoteImages++;
                if (imageUploadService.isEmpty()) {
                    errors.add(new ActivityImportPreviewDTO.RowError(
                            row.csvRowNumber(), ImportErrorCode.IMAGE_UPLOAD_UNAVAILABLE,
                            "image_url points outside our image storage and image uploads are not "
                                    + "configured on this server; leave the cell blank", "image_url"));
                }
            }
        }
        if (remoteImages > MAX_REMOTE_IMAGES) {
            errors.add(new ActivityImportPreviewDTO.RowError(
                    0, ImportErrorCode.TOO_MANY_IMAGES,
                    "File references " + remoteImages + " remote images; maximum per import is "
                            + MAX_REMOTE_IMAGES + " — split the file", "image_url"));
        }
    }

    private boolean needsRehosting(ValidatedRow row) {
        if (row.csvImageUrl().isEmpty()) {
            return false;
        }
        return imageUploadService.map(s -> !s.isHostedUrl(row.csvImageUrl())).orElse(true);
    }

    private static ActivityImportPreviewDTO.RowCreate toRowCreate(ValidatedRow row) {
        return new ActivityImportPreviewDTO.RowCreate(
                row.csvRowNumber(),
                row.name(),
                row.csvDestinationSlug(),
                row.csvSlug().isEmpty() ? null : row.csvSlug(),
                row.price(),
                row.categorySlugs(),
                row.csvImageUrl().isEmpty() ? null : row.csvImageUrl());
    }

    /**
     * The token is only consumed once every remote image is safely in our bucket: a failed
     * download leaves the preview valid, so the admin can fix the image host and press Apply
     * again without re-uploading the file. The consume is atomic, so two concurrent applies of
     * one token still write once.
     */
    public ActivityImportResultDTO apply(ActivityImportApplyRequest request) {
        UUID token = parseToken(request.token());
        PreviewTokenCache.Entry cached = requireLive(tokenCache.peek(token));
        String principal = principalName();
        log.info("Activity CSV import apply requested: principal={} rowCount={}",
                principal, cached.rows().size());

        Map<Integer, String> imageUrlByRow = rehostImages(cached.rows());
        requireLive(tokenCache.consume(token));
        ActivityImportWriter.Result written = writer.write(cached.rows(), imageUrlByRow);

        log.info("Activity CSV import applied: principal={} rowsUpdated={} rowsCreated={} updatedIds={} createdIds={}",
                principal, written.updatedIds().size(), written.createdIds().size(),
                written.updatedIds(), written.createdIds());
        return new ActivityImportResultDTO(
                written.updatedIds().size(), written.createdIds().size(), Instant.now());
    }

    private static UUID parseToken(String rawToken) {
        try {
            return UUID.fromString(rawToken);
        } catch (IllegalArgumentException e) {
            throw new CsvImportException(CsvImportException.Code.TOKEN_NOT_FOUND,
                    "Preview token not found or already used");
        }
    }

    private PreviewTokenCache.Entry requireLive(Optional<PreviewTokenCache.Entry> entry) {
        PreviewTokenCache.Entry cached = entry.orElseThrow(() -> new CsvImportException(
                CsvImportException.Code.TOKEN_NOT_FOUND, "Preview token not found or already used"));
        if (clock.instant().isAfter(cached.expiresAt())) {
            throw new CsvImportException(CsvImportException.Code.TOKEN_EXPIRED,
                    "Preview token has expired");
        }
        return cached;
    }

    /**
     * Downloads every remote image of the new rows and stores it in our bucket, before the
     * DB transaction opens. Any failure aborts the whole import (nothing has been written
     * yet); objects already uploaded for earlier rows are left behind as harmless orphans.
     */
    private Map<Integer, String> rehostImages(List<ValidatedRow> rows) {
        Instant deadline = clock.instant().plus(REHOST_BUDGET);
        Map<Integer, String> imageUrlByRow = new HashMap<>();
        for (ValidatedRow row : rows) {
            if (!row.isNew() || row.csvImageUrl().isEmpty()) {
                continue;
            }
            String finalUrl = imageUploadService
                    .filter(uploads -> !uploads.isHostedUrl(row.csvImageUrl()))
                    .map(uploads -> rehost(row, uploads, deadline))
                    .orElse(row.csvImageUrl());
            imageUrlByRow.put(row.csvRowNumber(), finalUrl);
        }
        return imageUrlByRow;
    }

    private String rehost(ValidatedRow row, ImageUploadService uploads, Instant deadline) {
        if (clock.instant().isAfter(deadline)) {
            throw imageFailure(row, "image downloads exceeded the " + REHOST_BUDGET.toSeconds()
                    + "s budget for one import — split the file or use faster image hosts");
        }
        try {
            RemoteImageFetcher.FetchedImage fetched = remoteImageFetcher.fetch(row.csvImageUrl());
            return uploads.uploadImage(fetched.bytes(), fetched.contentType(), fetched.fileName());
        } catch (IOException | RuntimeException e) {
            // FetchException, BadRequestException from the image pipeline, S3 SDK failures:
            // all of them mean "this row's image could not be re-hosted" to the admin.
            throw imageFailure(row, e.getMessage());
        }
    }

    private static CsvImportException imageFailure(ValidatedRow row, String reason) {
        return new CsvImportException(CsvImportException.Code.IMAGE_FETCH_FAILED,
                "Could not fetch image for row " + row.csvRowNumber() + " (" + row.csvImageUrl() + "): " + reason);
    }

    private ActivityImportPreviewDTO emptyPreview(
            List<ActivityImportPreviewDTO.RowError> errors,
            List<ActivityImportPreviewDTO.RowWarning> warnings) {
        return new ActivityImportPreviewDTO(
                null, 0, 0, 0, 0, errors.size(), warnings.size(),
                List.of(), List.of(), errors, warnings);
    }

    /** Visible for testing: force a token's expiry into the past. */
    void expireTokenForTest(UUID token) {
        tokenCache.expireTokenForTest(token);
    }

    /** Visible for testing: drive the re-hosting budget and token expiry from a controlled clock. */
    void setClockForTest(Clock clock) {
        this.clock = clock;
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
