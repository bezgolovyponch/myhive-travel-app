package com.myhive.backend.ai.exception;

import lombok.Getter;

/**
 * A planner session or generation could not be found. Kept separate from
 * {@code ResourceNotFoundException} so the 404 body carries a machine-readable
 * {@code error} code ({@code SESSION_NOT_FOUND} / {@code GENERATION_NOT_FOUND}).
 */
@Getter
public class AiNotFoundException extends RuntimeException {

    private final String code;

    public AiNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }
}
