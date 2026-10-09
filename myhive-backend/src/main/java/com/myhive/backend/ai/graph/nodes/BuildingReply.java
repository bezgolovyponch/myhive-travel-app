package com.myhive.backend.ai.graph.nodes;

import java.util.Map;

/**
 * What the chat says on the turn that starts a generation. The model is told to announce the build and
 * ask nothing, and live it asked anyway - four runs out of four, three of them about the arrival and
 * departure the entry screen had already set. From that turn on every message is refused until the
 * packages land, so a question in the reply cannot be answered: it is replaced, not trusted.
 */
final class BuildingReply {

    private static final String GERMAN = "de";
    private static final String QUESTION_MARK = "?";

    private static final Map<String, String> TEXT = Map.of(
            "en", "On it - building your three options now.",
            GERMAN, "Alles klar - ich baue jetzt eure drei Optionen.");

    /** When the chat points at the row of activities above it instead of listing them again. */
    private static final Map<String, String> RECOMMENDING = Map.of(
            "en", "Here is what fits - tap a name to see it, or add it.",
            GERMAN, "Das passt dazu - tippe einen Namen an, um es zu sehen, oder füg es hinzu.");

    /** When the model announced a build on a turn that builds nothing. */
    private static final Map<String, String> NOTHING_TO_BUILD = Map.of(
            "en", "Your trip plan stays as it is. Tell me what to add, swap, move or take out.",
            GERMAN, "Dein Trip-Plan bleibt, wie er ist. Sag mir, was dazu soll, getauscht, verschoben oder raus kann.");

    /** How a model says it is building: the words of its own instruction, in either language. */
    private static final java.util.List<String> BUILD_WORDS = java.util.List.of(
            "three options", "building your", "building the", "drei optionen", "ich baue");

    private BuildingReply() {
    }

    /**
     * The model is told to say it is building three options once the brief is complete - and the brief
     * stays complete on every later turn, so it went on saying so under a draft the organizer was
     * editing. On a turn that starts no build that is a promise nobody keeps.
     */
    static boolean announcesABuild(String modelReply) {
        if (modelReply == null) {
            return false;
        }
        String lower = modelReply.toLowerCase(java.util.Locale.ROOT);
        return BUILD_WORDS.stream().anyMatch(lower::contains);
    }

    static String recommending(String locale) {
        return RECOMMENDING.get(GERMAN.equalsIgnoreCase(locale) ? GERMAN : "en");
    }

    /**
     * Said instead of rebuilding at once: what the change costs, and that the plan stays until they say so.
     */
    static String rebuildWarning(String locale, Integer days, Integer groupSize) {
        return GERMAN.equalsIgnoreCase(locale)
                ? "Für %d Tage und %d Leute baue ich den Plan neu - was du geändert hast, geht dabei verloren. Dein Plan bleibt erst mal, wie er ist. Soll ich neu bauen?".formatted(days, groupSize)
                : "For %d days and %d people I rebuild the plan from scratch - what you changed in it is lost. Your plan stays as it is for now. Shall I rebuild?".formatted(days, groupSize);
    }

    /** The two answers to the warning, as the organizer would type them. */
    static java.util.List<String> rebuildChoices(String locale, Integer days, Integer groupSize) {
        return GERMAN.equalsIgnoreCase(locale)
                ? java.util.List.of("Ja, neu bauen: %d Tage, %d Leute".formatted(days, groupSize), "Nein, Plan behalten")
                : java.util.List.of("Yes, rebuild: %d days, %d people".formatted(days, groupSize), "No, keep my plan");
    }

    static String nothingToBuild(String locale) {
        return NOTHING_TO_BUILD.get(GERMAN.equalsIgnoreCase(locale) ? GERMAN : "en");
    }

    /** The model's own words when they ask nothing; the stock line when they do, or when there are none. */
    static String of(String modelReply, String locale) {
        if (modelReply != null && !modelReply.isBlank() && !modelReply.contains(QUESTION_MARK)) {
            return modelReply;
        }
        return TEXT.get(GERMAN.equalsIgnoreCase(locale) ? GERMAN : "en");
    }
}
