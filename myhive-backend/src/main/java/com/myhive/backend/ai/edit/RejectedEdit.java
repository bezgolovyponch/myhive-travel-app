package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.model.Tier;

/**
 * One edit that could not be applied. {@code activityName} is the model's spelling when the name could not
 * be resolved and the catalog name otherwise; {@code packageKey} is null when the rejection happened before
 * the edit was targeted at a package (name resolution, or an activity no package holds).
 */
public record RejectedEdit(EditOp op, String activityName, Tier packageKey, EditRejectionReason reason, String detail) {
}
