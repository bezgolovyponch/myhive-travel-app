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
