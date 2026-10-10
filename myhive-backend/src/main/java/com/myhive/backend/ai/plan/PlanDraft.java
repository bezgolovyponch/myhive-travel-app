package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;

import java.util.List;
import java.util.UUID;

/** Exactly what the planner model returns, before any pricing or validation. */
public record PlanDraft(List<PackageDraft> packages) {

    public PlanDraft {
        packages = packages == null ? List.of() : List.copyOf(packages);
    }

    public record PackageDraft(Tier key, String title, String tagline, String description, List<DayDraft> days) {
        public PackageDraft {
            days = days == null ? List.of() : List.copyOf(days);
        }
    }

    public record DayDraft(int dayNumber, String title, String summary, List<ItemDraft> items) {
        public DayDraft {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /**
     * {@code extraCodes} are the activity's extras the organizer asked for in the chat; the planner model
     * never writes them (they enter through chat edits only), and they are never null.
     */
    public record ItemDraft(Slot slot, String startHint, UUID activityId, String why, List<String> extraCodes) {

        public ItemDraft {
            extraCodes = extraCodes == null ? List.of() : List.copyOf(extraCodes);
        }

        public ItemDraft(Slot slot, String startHint, UUID activityId, String why) {
            this(slot, startHint, activityId, why, List.of());
        }
    }
}
