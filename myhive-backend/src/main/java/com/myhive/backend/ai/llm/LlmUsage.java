package com.myhive.backend.ai.llm;

/** Token/latency accounting for one model call. {@link #none()} is used where no real call was made. */
public record LlmUsage(String model, Integer promptTokens, Integer completionTokens, long latencyMs) {

    public static LlmUsage none() {
        return new LlmUsage(null, null, null, 0L);
    }

    /**
     * The accounting of two calls that made one generation - the planner's structure and the chat
     * model's copy - as one: tokens and latency summed, the model named being this one's (the planner).
     */
    public LlmUsage plus(LlmUsage other) {
        if (other == null) {
            return this;
        }
        return new LlmUsage(model != null ? model : other.model(), sum(promptTokens, other.promptTokens()),
                sum(completionTokens, other.completionTokens()), latencyMs + other.latencyMs());
    }

    private static Integer sum(Integer a, Integer b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + b;
    }
}
