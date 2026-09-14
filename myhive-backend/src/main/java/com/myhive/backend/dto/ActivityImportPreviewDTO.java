package com.myhive.backend.dto;

import com.myhive.backend.service.activity.ImportErrorCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ActivityImportPreviewDTO(
        String token,
        int totalRows,
        int rowsToUpdate,
        int rowsToCreate,
        int rowsUnchanged,
        int rowsWithErrors,
        int rowsWithWarnings,
        List<RowDiff> changes,
        List<RowCreate> creates,
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

    /** A row with a blank id that will become a new activity. {@code slug} is null when auto-generated. */
    public record RowCreate(
            int csvRowNumber,
            String name,
            String destinationSlug,
            String slug,
            BigDecimal price,
            List<String> categorySlugs,
            String imageUrl
    ) {
    }

    public record FieldChange(Object oldValue, Object newValue) {
    }

    public record RowError(int csvRowNumber, ImportErrorCode code, String message, String field) {
    }

    public record RowWarning(int csvRowNumber, ImportErrorCode code, String message, String field) {
    }
}
