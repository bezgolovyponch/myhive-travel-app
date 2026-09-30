package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MissingFieldQuestionTest {

    @Test
    void asksForTheFirstGap_inTheOrderTheBriefReportsThem() {
        String expectedQuestion = "How many days are you in town?";

        assertThat(MissingFieldQuestion.of(Brief.empty().missingFields(), "en")).contains(expectedQuestion);
    }

    @Test
    void asksForBothEndsOfTheTripTogether_whenNeitherIsKnown() {
        String expectedQuestion = "When do you land on day 1 and leave on the last day - morning, afternoon or evening?";
        Brief noEdges = new Brief(3, 8, List.of(), "beer", null, null, null, null, null);

        assertThat(MissingFieldQuestion.of(noEdges.missingFields(), "en")).contains(expectedQuestion);
    }

    @Test
    void asksForTheOneEndThatIsMissing() {
        String expectedQuestion = "When do you leave on the last day - morning, afternoon or evening?";
        Brief noDeparture = new Brief(3, 8, List.of(), "beer", null, null, DayEdge.EVENING, null, null);

        assertThat(MissingFieldQuestion.of(noDeparture.missingFields(), "en")).contains(expectedQuestion);
    }

    @Test
    void asksInGerman_andFallsBackToEnglishForALocaleItHasNoWordsIn() {
        String expectedGerman = "Worauf hat die Gruppe Lust - Bier, Action, eine lange Partynacht?";
        String expectedEnglish = "What is the group into - beer, action, a big night out?";
        List<String> onlyTaste = List.of(Brief.FIELD_PREFERENCES);

        assertThat(MissingFieldQuestion.of(onlyTaste, "de")).contains(expectedGerman);
        assertThat(MissingFieldQuestion.of(onlyTaste, "cs")).contains(expectedEnglish);
    }

    @Test
    void hasNothingToAsk_whenTheBriefHasNoGap_orOneItDoesNotKnow() {
        assertThat(MissingFieldQuestion.of(List.of(), "en")).isEmpty();
        assertThat(MissingFieldQuestion.of(List.of("budget"), "en")).isEmpty();
    }
}
