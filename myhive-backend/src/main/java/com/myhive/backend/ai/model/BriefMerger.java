package com.myhive.backend.ai.model;

import java.util.List;

/** Field-wise merge: a non-null update wins, null keeps the current value; empty lists never clear. */
public final class BriefMerger {

    private BriefMerger() {}

    public static Brief merge(Brief current, Brief update) {
        List<String> categories = update.categorySlugs().isEmpty()
                ? current.categorySlugs() : update.categorySlugs();
        return new Brief(
                clamp(pick(update.days(), current.days()), Brief.MIN_DAYS, Brief.MAX_DAYS),
                clamp(pick(update.groupSize(), current.groupSize()), Brief.MIN_GROUP, Brief.MAX_GROUP),
                categories,
                trim(pick(update.vibe(), current.vibe())),
                trim(pick(update.dislikes(), current.dislikes())),
                pick(update.budget(), current.budget()),
                pick(update.arrival(), current.arrival()),
                pick(update.departure(), current.departure()),
                trim(pick(update.notes(), current.notes())));
    }

    private static <T> T pick(T update, T current) {
        return update != null ? update : current;
    }

    private static Integer clamp(Integer value, int min, int max) {
        if (value == null) {
            return null;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > Brief.MAX_TEXT ? trimmed.substring(0, Brief.MAX_TEXT) : trimmed;
    }
}
