package com.myhive.backend.ai.dto;

import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.model.Tier;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Body of {@code POST /ai/sessions/&#123;token&#125;/edits}: one tap in the trip draft. {@code op} is ADD or
 * REMOVE (REPLACE is a 400), {@code packageKey} the trim the draft shows; null means every package.
 */
public record DraftEditRequest(@NotNull EditOp op, @NotNull UUID activityId, Tier packageKey, Integer dayNumber) {
}
