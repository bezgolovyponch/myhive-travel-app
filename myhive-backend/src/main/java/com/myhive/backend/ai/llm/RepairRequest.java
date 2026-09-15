package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.plan.Violation;

import java.util.List;

public record RepairRequest(PlanRequest original, PlanDraft draft, List<Violation> violations) {
}
