package com.myhive.backend.service.activity;

public enum ImportErrorCode {
    // File-level errors
    EMPTY_FILE,
    FILE_TOO_LARGE,
    TOO_MANY_ROWS,
    INVALID_ENCODING,
    MISSING_COLUMNS,
    TOO_MANY_IMAGES,

    // File-level warnings
    UNKNOWN_COLUMNS,

    // Row-level errors
    INVALID_UUID,
    ROW_NOT_FOUND,
    DUPLICATE_ID,
    NAME_REQUIRED,
    FIELD_TOO_LONG,
    INVALID_DECIMAL,
    PRICE_REQUIRED,
    INVALID_INTEGER,
    UNKNOWN_CATEGORY,
    // Row-level errors specific to rows with a blank id (creates)
    DESTINATION_REQUIRED,
    UNKNOWN_DESTINATION,
    INVALID_SLUG,
    NAME_EXISTS,
    INVALID_URL,
    IMAGE_UPLOAD_UNAVAILABLE,

    // Row-level warnings
    READ_ONLY_FIELD_CHANGED,
    DUPLICATE_NAME,
    SLUG_TAKEN
}
