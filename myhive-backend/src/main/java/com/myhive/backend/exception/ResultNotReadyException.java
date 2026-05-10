package com.myhive.backend.exception;

public class ResultNotReadyException extends RuntimeException {
    public ResultNotReadyException(String message) {
        super(message);
    }
}
