package com.myhive.backend.exception;

import lombok.Getter;

@Getter
public class CsvImportException extends RuntimeException {

    public enum Code {
        TOKEN_NOT_FOUND,
        TOKEN_EXPIRED,
        STATE_CHANGED
    }

    private final Code code;

    public CsvImportException(Code code, String message) {
        super(message);
        this.code = code;
    }
}
