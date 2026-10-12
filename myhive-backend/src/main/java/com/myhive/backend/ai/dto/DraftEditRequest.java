package com.myhive.backend.ai.dto;

import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.model.Tier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /ai/sessions/&#123;token&#125;/edits}: one tap in the trip draft. {@code op} is ADD or
 * REMOVE (REPLACE is a 400), {@code packageKey} the trim the draft shows; null means every package.
 *
 * <p>{@code extras} is what the activity's card was confirmed with: the codes of the extras ticked on it,
 * all of them. With ADD the activity goes in with those extras; with ADD_EXTRA the activity is already in
 * the plan and its extras become exactly those (unticked ones are dropped). Null on a plain tap.
 */
public record DraftEditRequest(@NotNull EditOp op, @NotNull UUID activityId, Tier packageKey, Integer dayNumber,
                               @Size(max = 10) List<String> extras) {

    public DraftEditRequest(EditOp op, UUID activityId, Tier packageKey, Integer dayNumber) {
        this(op, activityId, packageKey, dayNumber, null);
    }
}
