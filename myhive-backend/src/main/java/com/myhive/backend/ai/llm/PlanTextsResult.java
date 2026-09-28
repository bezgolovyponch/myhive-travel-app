package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.model.Tier;

import java.util.Map;

/** One texts answer for a freshly composed plan: the copy per package plus the accounting for that call. */
public record PlanTextsResult(Map<Tier, PackageTexts> texts, LlmUsage usage) {

    public PlanTextsResult {
        texts = texts == null ? Map.of() : Map.copyOf(texts);
    }
}
