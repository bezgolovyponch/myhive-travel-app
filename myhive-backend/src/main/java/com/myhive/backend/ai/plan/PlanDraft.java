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

    public record ItemDraft(Slot slot, String startHint, UUID activityId, String why) {
    }
}
