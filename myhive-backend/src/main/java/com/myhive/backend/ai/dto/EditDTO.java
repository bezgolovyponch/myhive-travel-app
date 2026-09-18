package com.myhive.backend.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * What one edit turn did to the packages. {@code generationId} is the {@code EDITED} row the batch
 * produced, and is null when nothing was applied — a fully rejected batch creates no row and leaves the
 * organizer on the packages they already had. {@code tierRulesRelaxed} says that the tier heuristics
 * (a strictly rising per-person price, one activity per tier the others lack) no longer hold once a
 * package was edited by hand; {@code textsRefreshed} whether the copy of what was touched was rewritten.
 */
public record EditDTO(UUID generationId, List<AppliedEditDTO> applied, List<RejectedEditDTO> rejected,
                      boolean tierRulesRelaxed, boolean textsRefreshed) {

    /**
     * One edit that landed, per package. {@code activity}/{@code replacement} are catalog names, never the
     * model's spelling, and {@code replacement} is null for anything but {@code REPLACE}. {@code dayNumber}
     * and {@code slot} are the cell the new item landed in, or the removed item's cell for {@code REMOVE}.
     */
    public record AppliedEditDTO(String op, String activity, String replacement, String packageKey, int dayNumber,
                                 String slot) {
    }

    /**
     * One edit that could not be applied. {@code packageKey} is null when the rejection happened before the
     * edit was aimed at a package; {@code detail} is a short technical hint, not customer-facing copy — the
     * chat reply already carries the sentence the group reads.
     */
    public record RejectedEditDTO(String op, String activity, String packageKey, String reason, String detail) {
    }
}
