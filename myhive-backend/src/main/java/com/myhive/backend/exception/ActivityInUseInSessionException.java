package com.myhive.backend.exception;

import java.util.List;

public class ActivityInUseInSessionException extends RuntimeException {

    private final List<String> sessionShareTokens;

    public ActivityInUseInSessionException(String message, List<String> sessionShareTokens) {
        super(message);
        this.sessionShareTokens = sessionShareTokens;
    }

    public List<String> getSessionShareTokens() {
        return sessionShareTokens;
    }
}
