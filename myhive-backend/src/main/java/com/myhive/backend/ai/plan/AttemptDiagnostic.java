package com.myhive.backend.ai.plan;

import java.util.List;

/**
 * What was wrong with one planner draft (compose = attempt 0, repair = attempt 1) and what became of it,
 * kept for staff only. {@code errorCode} is set when the model call itself failed (timeout, unavailable,
 * unparseable output) and the draft was therefore empty; {@code violations} are the validator's findings
 * on the draft as the model wrote it, as {@code CODE TIER dN: detail}. {@code fixes} tells the two
 * outcomes apart: with any, Java corrected the draft ({@link PlanTrimmer}, one line per correction) and
 * the plan is the model's, less what is listed; with none, the draft was rejected. A run that routed to
 * fallback has both attempts here, rejected, which is what tells a broken rule apart from a failed call.
 */
public record AttemptDiagnostic(int attempt, String errorCode, List<String> violations, List<String> fixes) {

    public AttemptDiagnostic {
        violations = violations == null ? List.of() : List.copyOf(violations);
        fixes = fixes == null ? List.of() : List.copyOf(fixes);
    }

    /** A rejected draft. */
    public AttemptDiagnostic(int attempt, String errorCode, List<String> violations) {
        this(attempt, errorCode, violations, List.of());
    }

    public static AttemptDiagnostic of(int attempt, String errorCode, List<Violation> violations) {
        return of(attempt, errorCode, violations, List.of());
    }

    public static AttemptDiagnostic of(int attempt, String errorCode, List<Violation> violations,
            List<String> fixes) {
        return new AttemptDiagnostic(attempt, errorCode,
                violations.stream().map(AttemptDiagnostic::describe).toList(), fixes);
    }

    private static String describe(Violation v) {
        return v.code() + (v.packageKey() == null ? "" : " " + v.packageKey())
                + (v.dayNumber() == null ? "" : " d" + v.dayNumber()) + ": " + v.detail();
    }
}
