package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.model.Tier;

/** One rule breach, precise enough for the repair prompt and for a unit test. dayNumber is null for package-level rules. */
public record Violation(ViolationCode code, Tier packageKey, Integer dayNumber, String detail) {

    public static Violation of(ViolationCode code, Tier packageKey, Integer dayNumber, String detail) {
        return new Violation(code, packageKey, dayNumber, detail);
    }
}
