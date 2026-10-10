package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.entity.ExtraPricing;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One edit that landed, per package. {@code activityName}/{@code replacementName} are the catalog names
 * after resolution, never the model's spelling; {@code replacementName} is null for anything but
 * {@link EditOp#REPLACE}. {@code placedActivityId} is the activity that was put into the plan (the added
 * one for {@code ADD}, the replacement for {@code REPLACE}, null for {@code REMOVE}). {@code dayNumber}
 * and {@code slot} are the removed item's first occurrence for {@code REMOVE}, otherwise the cell the new
 * item landed in. {@code extraName} is the catalog name of the extra an {@code ADD_EXTRA}/{@code REMOVE_EXTRA}
 * chose or dropped (the cell is then the activity's own); null for every other op and on reports stored before it.
 * {@code extraPrice}/{@code extraPricing} are that extra's catalog price and how it is charged, for the
 * confirmation line of an {@code ADD_EXTRA}; null wherever {@code extraName} is, and on reports stored before them.
 */
public record AppliedEdit(EditOp op, String activityName, String replacementName, Tier packageKey, int dayNumber,
                          Slot slot, UUID placedActivityId, String extraName, BigDecimal extraPrice,
                          ExtraPricing extraPricing) {

    public AppliedEdit(EditOp op, String activityName, String replacementName, Tier packageKey, int dayNumber,
                       Slot slot, UUID placedActivityId, String extraName) {
        this(op, activityName, replacementName, packageKey, dayNumber, slot, placedActivityId, extraName, null, null);
    }

    public AppliedEdit(EditOp op, String activityName, String replacementName, Tier packageKey, int dayNumber,
                       Slot slot, UUID placedActivityId) {
        this(op, activityName, replacementName, packageKey, dayNumber, slot, placedActivityId, null);
    }
}
