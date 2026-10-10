package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.model.Tier;

import java.util.List;

/**
 * One edit that could not be applied. {@code activityName} is the model's spelling when the name could not
 * be resolved and the catalog name otherwise; {@code packageKey} is null when the rejection happened before
 * the edit was targeted at a package (name resolution, or an activity no package holds).
 *
 * <p>{@code alternatives} are catalog names offered in place of an activity the catalog lacks: filled only
 * on {@code UNKNOWN_ACTIVITY}, and only with names that resolve, in the catalog's own spelling. Never null;
 * a report stored before the field existed reads back with an empty list.
 *
 * <p>{@code extraName} is the extra an {@code ADD_EXTRA}/{@code REMOVE_EXTRA} named: the organizer's spelling
 * on {@code UNKNOWN_EXTRA}, the catalog's otherwise; null for every other op.
 */
public record RejectedEdit(EditOp op, String activityName, Tier packageKey, EditRejectionReason reason, String detail,
                           List<String> alternatives, String extraName) {

    public RejectedEdit {
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
    }

    /** A rejection that names no extra. */
    public RejectedEdit(EditOp op, String activityName, Tier packageKey, EditRejectionReason reason, String detail,
                        List<String> alternatives) {
        this(op, activityName, packageKey, reason, detail, alternatives, null);
    }

    /** The shape every caller used before alternatives existed: a rejection with nothing to offer instead. */
    public RejectedEdit(EditOp op, String activityName, Tier packageKey, EditRejectionReason reason, String detail) {
        this(op, activityName, packageKey, reason, detail, List.of());
    }
}
