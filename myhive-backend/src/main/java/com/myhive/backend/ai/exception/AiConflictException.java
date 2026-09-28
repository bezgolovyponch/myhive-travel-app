package com.myhive.backend.ai.exception;

import lombok.Getter;

/** The session is not in a state that allows this call; {@code code} names which state got in the way. */
@Getter
public class AiConflictException extends RuntimeException {

    private final String code;

    public AiConflictException(String code, String message) {
        super(message);
        this.code = code;
    }
}
