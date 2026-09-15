package com.myhive.backend.ai.llm;

/** Token/latency accounting for one model call. {@link #none()} is used where no real call was made. */
public record LlmUsage(String model, Integer promptTokens, Integer completionTokens, long latencyMs) {

    public static LlmUsage none() {
        return new LlmUsage(null, null, null, 0L);
    }
}
