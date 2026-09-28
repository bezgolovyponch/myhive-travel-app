package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.plan.PlanDraft;

public record PlanDraftResult(PlanDraft draft, LlmUsage usage) {
}
