package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the model needs to rewrite the copy of the packages an edit just touched: {@code packages} holds
 * only those packages (already edited and re-priced), {@code newActivityIds} the activities whose "why"
 * has to be written from scratch, and {@code touchedDays} the day summaries that went stale.
 */
public record TextRefreshRequest(String locale, String destinationName, List<ComposedPlan.PackageResult> packages,
                                 Map<Tier, Set<UUID>> newActivityIds, Map<Tier, Set<Integer>> touchedDays) {

    public TextRefreshRequest {
        packages = packages == null ? List.of() : List.copyOf(packages);
        newActivityIds = newActivityIds == null ? Map.of() : Map.copyOf(newActivityIds);
        touchedDays = touchedDays == null ? Map.of() : Map.copyOf(touchedDays);
    }

    public Set<UUID> newActivityIdsOf(Tier key) {
        return newActivityIds.getOrDefault(key, Set.of());
    }

    public Set<Integer> touchedDaysOf(Tier key) {
        return touchedDays.getOrDefault(key, Set.of());
    }
}
