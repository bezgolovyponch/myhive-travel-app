package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One edit the model asked for on top of the current plan: swap, drop or add an activity,
 * optionally scoped to a package/day/slot. Produced by {@link com.myhive.backend.ai.llm.LlmOutputParser}
 * from the chat turn's {@code edits} array.
 *
 * <p>{@code alternatives} are the catalog names the model deems closest to an activity that is not on
 * offer. They are only looked at when {@code activity} (or {@code replacement}) fails to resolve, and only
 * the ones that resolve themselves are offered back. Never null, usually empty.
 */
public record EditRequest(EditOp op, String activity, String replacement, Tier packageKey, Integer dayNumber,
                          Slot slot, List<String> alternatives) {

    public EditRequest {
        activity = activity == null ? null : activity.strip();
        replacement = replacement == null ? null : replacement.strip();
        alternatives = distinctNonBlank(alternatives);
    }

    /** The shape every caller used before alternatives existed: an edit that names none. */
    public EditRequest(EditOp op, String activity, String replacement, Tier packageKey, Integer dayNumber, Slot slot) {
        this(op, activity, replacement, packageKey, dayNumber, slot, List.of());
    }

    private static List<String> distinctNonBlank(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                distinct.add(name.strip());
            }
        }
        return List.copyOf(distinct);
    }
}
