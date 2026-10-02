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

    /** The names {@link #missingFields()} reports a gap under; the API serves them as they are. */
    public static final String FIELD_DAYS = "days";
    public static final String FIELD_GROUP_SIZE = "groupSize";
    public static final String FIELD_PREFERENCES = "preferences";
    public static final String FIELD_ARRIVAL = "arrival";
    public static final String FIELD_DEPARTURE = "departure";

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
     * A group without tickets answers {@link DayEdge#FLEXIBLE}, which closes the gap like any other edge.
     */
    @JsonIgnore
    public List<String> missingFields() {
        List<String> missing = new ArrayList<>();
        if (days == null) {
            missing.add(FIELD_DAYS);
        }
        if (groupSize == null) {
            missing.add(FIELD_GROUP_SIZE);
        }
        boolean hasTaste = !categorySlugs.isEmpty() || (vibe != null && !vibe.isBlank());
        if (!hasTaste) {
            missing.add(FIELD_PREFERENCES);
        }
        if (arrival == null) {
            missing.add(FIELD_ARRIVAL);
        }
        if (departure == null) {
            missing.add(FIELD_DEPARTURE);
        }
        return missing;
    }

    /**
     * The chat model sometimes files what the group likes under the notes ("likes beer, karting") and
     * leaves the vibe empty. When taste is the only thing still missing and there are notes, they are
     * read as the vibe; in every other case the brief comes back as it is. Whether the notes may be
     * read that way is the caller's call - see {@code ChatTurnNode}.
     */
    public Brief withNotesAsTaste() {
        boolean onlyTasteMissing = missingFields().equals(List.of(FIELD_PREFERENCES));
        if (!onlyTasteMissing || notes == null || notes.isBlank()) {
            return this;
        }
        return new Brief(days, groupSize, categorySlugs, notes, dislikes, budget, arrival, departure, null);
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
