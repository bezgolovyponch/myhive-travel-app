package com.myhive.backend.exception;

/** Thrown when a request conflicts with the current state of a resource (maps to HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
