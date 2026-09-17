package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;

/**
 * One edit the model asked for on top of the current plan: swap, drop or add an activity,
 * optionally scoped to a package/day/slot. Produced by {@link com.myhive.backend.ai.llm.LlmOutputParser}
 * from the chat turn's {@code edits} array.
 */
public record EditRequest(EditOp op, String activity, String replacement, Tier packageKey, Integer dayNumber,
                          Slot slot) {

    public EditRequest {
        activity = activity == null ? null : activity.strip();
        replacement = replacement == null ? null : replacement.strip();
    }
}
