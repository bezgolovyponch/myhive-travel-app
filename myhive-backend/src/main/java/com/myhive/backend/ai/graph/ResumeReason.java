package com.myhive.backend.ai.graph;

/** Why a parked thread is being resumed; written by the services before every {@code stream(resume())}. */
public enum ResumeReason {
    USER_MESSAGE, GENERATE, SELECT
}
