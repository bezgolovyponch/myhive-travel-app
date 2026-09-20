package com.myhive.backend.ai.exception;

import lombok.Getter;

/** A planner quota was hit; {@code code} is the machine-readable reason the frontend switches on. */
@Getter
public class AiLimitException extends RuntimeException {

    private final String code;

    public AiLimitException(String code, String message) {
        super(message);
        this.code = code;
    }
}
