package com.myhive.backend.ai.llm;

/** Thrown when the model's JSON output cannot be parsed into a contract type; message names the offending field. */
public class LlmOutputException extends RuntimeException {

    public LlmOutputException(String message, Throwable cause) {
        super(message, cause);
    }

    public LlmOutputException(String message) {
        super(message);
    }
}
