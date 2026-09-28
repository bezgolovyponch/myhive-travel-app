package com.myhive.backend.ai.model;

public enum DayEdge {
    MORNING(Slot.MORNING), AFTERNOON(Slot.AFTERNOON), EVENING(Slot.EVENING);

    private final Slot slot;

    DayEdge(Slot slot) {
        this.slot = slot;
    }

    public Slot slot() {
        return slot;
    }
}
