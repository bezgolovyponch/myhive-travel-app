package com.myhive.backend.ai.exception;

import lombok.Getter;

/** The model could not answer this turn; {@code code} is {@code LLM_TIMEOUT} or {@code LLM_UNAVAILABLE}. */
@Getter
public class LlmCallFailedException extends RuntimeException {

    private final String code;

    public LlmCallFailedException(String code, Throwable cause) {
        super("The assistant could not answer right now", cause);
        this.code = code;
    }
}
