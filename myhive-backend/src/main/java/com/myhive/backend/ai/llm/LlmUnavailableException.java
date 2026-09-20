package com.myhive.backend.ai.llm;

/** Thrown when the model could not be reached at all: transport failure, timeout or interruption. */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
