package com.myhive.backend.ai.edit;

import java.util.List;

/**
 * What one edit turn did, ready to persist and to hand back to the chat client. {@code tierRulesRelaxed}
 * tells the client that {@code TIER_ORDER}/{@code TIER_NOT_DISTINCT} no longer hold once any edit landed;
 * {@code textsRefreshed} says whether {@link TextRefresher} actually rewrote the touched copy.
 */
public record EditReport(List<AppliedEdit> applied, List<RejectedEdit> rejected, boolean tierRulesRelaxed,
                         boolean textsRefreshed) {

    public EditReport {
        applied = applied == null ? List.of() : List.copyOf(applied);
        rejected = rejected == null ? List.of() : List.copyOf(rejected);
    }

    public static EditReport of(EditOutcome outcome, boolean textsRefreshed) {
        return new EditReport(outcome.applied(), outcome.rejected(), outcome.anyApplied(), textsRefreshed);
    }

    /** Every requested edit was rejected for the same reason before any of them could be targeted. */
    public static EditReport allRejected(List<EditRequest> edits, EditRejectionReason reason) {
        List<RejectedEdit> rejected = edits.stream()
                .map(edit -> new RejectedEdit(edit.op(), edit.activity(), edit.packageKey(), reason, null))
                .toList();
        return new EditReport(List.of(), rejected, false, false);
    }

    public boolean anyApplied() {
        return !applied.isEmpty();
    }
}
