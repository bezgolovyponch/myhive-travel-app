package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.plan.ComposedPlan;

import java.util.List;

/**
 * The result of one edit batch: the re-priced plan plus the per-package report. The plan is always
 * returned, edited or not, so a caller that applied nothing can still serve the original.
 */
public record EditOutcome(ComposedPlan plan, List<AppliedEdit> applied, List<RejectedEdit> rejected) {

    public EditOutcome {
        applied = applied == null ? List.of() : List.copyOf(applied);
        rejected = rejected == null ? List.of() : List.copyOf(rejected);
    }

    public boolean anyApplied() {
        return !applied.isEmpty();
    }
}
