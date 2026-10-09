package com.myhive.backend.ai.model;

import java.util.List;

/**
 * Something concrete the organizer asked the trip to have, before the packages were built: one
 * activity, or one of several ("tank or shooting"), by exact catalog name - and the day they named for
 * it, when they named one (1 = the first day; null = any day). Every package is held to it.
 */
public record Wish(List<String> activities, Integer dayNumber) {

    public Wish {
        activities = activities == null ? List.of()
                : activities.stream().filter(name -> name != null && !name.isBlank()).map(String::strip).toList();
    }
}
