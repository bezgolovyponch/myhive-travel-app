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
