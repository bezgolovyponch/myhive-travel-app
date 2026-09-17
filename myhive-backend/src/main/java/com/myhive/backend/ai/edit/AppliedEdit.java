package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;

import java.util.UUID;

/**
 * One edit that landed, per package. {@code activityName}/{@code replacementName} are the catalog names
 * after resolution, never the model's spelling; {@code replacementName} is null for anything but
 * {@link EditOp#REPLACE}. {@code placedActivityId} is the activity that was put into the plan (the added
 * one for {@code ADD}, the replacement for {@code REPLACE}, null for {@code REMOVE}). {@code dayNumber}
 * and {@code slot} are the removed item's first occurrence for {@code REMOVE}, otherwise the cell the new
 * item landed in.
 */
public record AppliedEdit(EditOp op, String activityName, String replacementName, Tier packageKey, int dayNumber,
                          Slot slot, UUID placedActivityId) {
}
