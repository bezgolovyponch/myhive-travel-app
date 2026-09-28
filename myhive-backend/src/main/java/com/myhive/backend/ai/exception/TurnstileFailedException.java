package com.myhive.backend.ai.exception;

/** The captcha token was missing or rejected while {@code app.ai.turnstile-required} is on. */
public class TurnstileFailedException extends RuntimeException {

    public TurnstileFailedException() {
        super("Captcha verification failed");
    }
}
