package com.myhive.backend.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

public record Brief(Integer days, Integer groupSize, List<String> categorySlugs, String vibe,
                    String dislikes, BudgetHint budget, DayEdge arrival, DayEdge departure, String notes) {

    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 7;
    public static final int MIN_GROUP = 2;
    public static final int MAX_GROUP = 30;
    public static final int MAX_TEXT = 300;

    public Brief {
        categorySlugs = categorySlugs == null ? List.of() : List.copyOf(categorySlugs);
    }

    public static Brief empty() {
        return new Brief(null, null, List.of(), null, null, null, null, null, null);
    }

    public Brief withCategorySlugs(List<String> slugs) {
        return new Brief(days, groupSize, slugs, vibe, dislikes, budget, arrival, departure, notes);
    }

    @JsonIgnore
    public boolean isReady() {
        return missingFields().isEmpty();
    }

    /**
     * What the chat still has to ask before a generation is worth its ~40 s planner call. Arrival and
     * departure are required, not defaulted: the edges decide how much of day 1 and the last day is
     * usable, and generating on the defaults first cost a second full generation the moment the
     * organizer mentioned when they land. Budget stays optional - many groups will not name one.
     */
    @JsonIgnore
    public List<String> missingFields() {
        List<String> missing = new ArrayList<>();
        if (days == null) {
            missing.add("days");
        }
        if (groupSize == null) {
            missing.add("groupSize");
        }
        boolean hasTaste = !categorySlugs.isEmpty() || (vibe != null && !vibe.isBlank());
        if (!hasTaste) {
            missing.add("preferences");
        }
        if (arrival == null) {
            missing.add("arrival");
        }
        if (departure == null) {
            missing.add("departure");
        }
        return missing;
    }

    @JsonIgnore
    public DayEdge arrivalOrDefault() {
        return arrival == null ? DayEdge.AFTERNOON : arrival;
    }

    @JsonIgnore
    public DayEdge departureOrDefault() {
        return departure == null ? DayEdge.MORNING : departure;
    }
}
