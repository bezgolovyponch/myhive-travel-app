package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.model.Tier;

import java.util.Map;

/** One text-refresh answer: the rewritten copy per package plus the accounting for that call. */
public record TextRefreshResult(Map<Tier, PackageTexts> texts, LlmUsage usage) {

    public TextRefreshResult {
        texts = texts == null ? Map.of() : Map.copyOf(texts);
    }
}
