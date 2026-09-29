package com.myhive.backend.ai.plan;

import java.util.List;

/**
 * Why one planner draft (compose = attempt 0, repair = attempt 1) was rejected, kept for staff only.
 * {@code errorCode} is set when the model call itself failed (timeout, unavailable, unparseable output)
 * and the draft was therefore empty; {@code violations} are the validator's findings as
 * {@code CODE TIER dN: detail}. A run that routed to fallback has both attempts here, which is what tells
 * a broken rule apart from a failed call.
 */
public record AttemptDiagnostic(int attempt, String errorCode, List<String> violations) {

    public AttemptDiagnostic {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public static AttemptDiagnostic of(int attempt, String errorCode, List<Violation> violations) {
        return new AttemptDiagnostic(attempt, errorCode,
                violations.stream().map(AttemptDiagnostic::describe).toList());
    }

    private static String describe(Violation v) {
        return v.code() + (v.packageKey() == null ? "" : " " + v.packageKey())
                + (v.dayNumber() == null ? "" : " d" + v.dayNumber()) + ": " + v.detail();
    }
}
