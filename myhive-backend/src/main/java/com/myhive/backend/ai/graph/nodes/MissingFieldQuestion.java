package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.model.Brief;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the chat asks when the model has stopped asking and the brief still has a gap. The mirror image
 * of {@link BuildingReply}: there the model asked while Java was already building, here it said
 * "Building three options right now." while Java was not - it had filed what the group likes under the
 * notes, where Java does not look for it. Told to "just build it", it said the same sentence again, and
 * the organizer was left with a chat that promised packages and nothing to answer.
 */
final class MissingFieldQuestion {

    private static final String ENGLISH = "en";
    private static final String GERMAN = "de";
    /** Asked about together, as the model is told to: one answer covers both ends of the trip. */
    private static final String BOTH_EDGES = Brief.FIELD_ARRIVAL + "+" + Brief.FIELD_DEPARTURE;

    private static final Map<String, Map<String, String>> TEXT = Map.of(
            Brief.FIELD_DAYS, Map.of(
                    ENGLISH, "How many days are you in town?",
                    GERMAN, "Wie viele Tage seid ihr in der Stadt?"),
            Brief.FIELD_GROUP_SIZE, Map.of(
                    ENGLISH, "How many of you are coming?",
                    GERMAN, "Wie viele seid ihr?"),
            Brief.FIELD_PREFERENCES, Map.of(
                    ENGLISH, "What is the group into - beer, action, a big night out?",
                    GERMAN, "Worauf hat die Gruppe Lust - Bier, Action, eine lange Partynacht?"),
            BOTH_EDGES, Map.of(
                    ENGLISH, "When do you land on day 1 and leave on the last day - morning, afternoon or evening?",
                    GERMAN, "Wann kommt ihr am ersten Tag an und wann reist ihr am letzten ab - morgens,"
                            + " nachmittags oder abends?"),
            Brief.FIELD_ARRIVAL, Map.of(
                    ENGLISH, "When do you land on day 1 - morning, afternoon or evening?",
                    GERMAN, "Wann kommt ihr am ersten Tag an - morgens, nachmittags oder abends?"),
            Brief.FIELD_DEPARTURE, Map.of(
                    ENGLISH, "When do you leave on the last day - morning, afternoon or evening?",
                    GERMAN, "Wann reist ihr am letzten Tag ab - morgens, nachmittags oder abends?"));

    private MissingFieldQuestion() {
    }

    /** The question for the first gap in the brief; empty when it has none, or one this class has no words for. */
    static Optional<String> of(List<String> missingFields, String locale) {
        if (missingFields.isEmpty()) {
            return Optional.empty();
        }
        boolean bothEdges = missingFields.contains(Brief.FIELD_ARRIVAL)
                && missingFields.contains(Brief.FIELD_DEPARTURE);
        String first = missingFields.get(0);
        String asked = bothEdges && first.equals(Brief.FIELD_ARRIVAL) ? BOTH_EDGES : first;
        return Optional.ofNullable(TEXT.get(asked))
                .map(text -> text.get(GERMAN.equalsIgnoreCase(locale) ? GERMAN : ENGLISH));
    }
}
