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

    private BuildingReply() {
    }

    /** The model's own words when they ask nothing; the stock line when they do, or when there are none. */
    static String of(String modelReply, String locale) {
        if (modelReply != null && !modelReply.isBlank() && !modelReply.contains(QUESTION_MARK)) {
            return modelReply;
        }
        return TEXT.get(GERMAN.equalsIgnoreCase(locale) ? GERMAN : "en");
    }
}
