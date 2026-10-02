package com.myhive.backend.ai.model;

public enum DayEdge {
    MORNING(Slot.MORNING), AFTERNOON(Slot.AFTERNOON), EVENING(Slot.EVENING),
    /**
     * The organizer does not know yet - flights not booked. An answer, not a gap: the chat stops asking
     * and the plan assumes a midday edge, so day 1 and the last day both keep something worth doing
     * (a MORNING departure default would leave the last day empty).
     */
    FLEXIBLE(Slot.AFTERNOON);

    private final Slot slot;

    DayEdge(Slot slot) {
        this.slot = slot;
    }

    public Slot slot() {
        return slot;
    }
}
