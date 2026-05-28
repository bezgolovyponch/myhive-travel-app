package com.myhive.backend.service.activity;

import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes diffs between validated CSV rows and current DB activities,
 * and produces read-only field warnings. Pure logic, no DB access.
 *
 * Package-private; composed by ActivityCsvImporter.
 */
final class ActivityCsvDiffer {

    record Result(
            List<ActivityImportPreviewDTO.RowDiff> diffs,
            List<ValidatedRow> changedRows,
            int unchangedCount,
            List<ActivityImportPreviewDTO.RowError> errors,
            List<ActivityImportPreviewDTO.RowWarning> warnings
    ) {
    }

    Result diff(List<ValidatedRow> validated, Map<UUID, Activity> fromDb) {
        List<ActivityImportPreviewDTO.RowDiff> diffs = new ArrayList<>();
        List<ValidatedRow> changedRows = new ArrayList<>();
        List<ActivityImportPreviewDTO.RowError> errors = new ArrayList<>();
        List<ActivityImportPreviewDTO.RowWarning> warnings = new ArrayList<>();
        int unchanged = 0;

        for (ValidatedRow v : validated) {
            Activity db = fromDb.get(v.activityId());
            if (db == null) {
                errors.add(new ActivityImportPreviewDTO.RowError(
                        v.csvRowNumber(), ImportErrorCode.ROW_NOT_FOUND,
                        "No activity with id " + v.activityId(), "id"));
                continue;
            }
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
        return new Result(diffs, changedRows, unchanged, errors, warnings);
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
        // Optional column: only diff when the CSV provided a value (column present).
        // Null means the column was absent — leave the DB value alone.
        if (v.featuredWeight() != null && db.getFeaturedWeight() != v.featuredWeight()) {
            changes.put("featured_weight", new ActivityImportPreviewDTO.FieldChange(
                    db.getFeaturedWeight(), v.featuredWeight()));
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
}
