package com.myhive.backend.ai.exception;

/** The {@code app.ai.enabled} kill switch is off: every planner endpoint answers 503 AI_DISABLED. */
public class AiDisabledException extends RuntimeException {

    public AiDisabledException() {
        super("The AI planner is currently disabled");
    }
}
