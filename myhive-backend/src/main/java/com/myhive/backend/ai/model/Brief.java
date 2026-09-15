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
