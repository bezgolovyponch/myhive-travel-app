package com.myhive.backend.ai.model;

public enum Tier {
    BASIC(2, 360),
    MEDIUM(3, 480),
    PREMIUM(4, 540);

    private final int maxItemsPerDay;
    private final int maxMinutesPerDay;

    Tier(int maxItemsPerDay, int maxMinutesPerDay) {
        this.maxItemsPerDay = maxItemsPerDay;
        this.maxMinutesPerDay = maxMinutesPerDay;
    }

    public int maxItemsPerDay() {
        return maxItemsPerDay;
    }

    public int maxMinutesPerDay() {
        return maxMinutesPerDay;
    }
}
