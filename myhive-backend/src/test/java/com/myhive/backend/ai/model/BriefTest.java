package com.myhive.backend.ai.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BriefTest {

    private static final String NOTES = "likes beer, karting";

    @Test
    void withNotesAsTaste_readsTheNotesAsTheVibe_whenTasteIsAllThatIsMissing() {
        Brief tasteInNotes = new Brief(2, 6, List.of(), null, null, BudgetHint.MID, DayEdge.MORNING, DayEdge.EVENING,
                NOTES);

        Brief read = tasteInNotes.withNotesAsTaste();

        assertThat(read.vibe()).isEqualTo(NOTES);
        assertThat(read.notes()).isNull();
        assertThat(read.isReady()).isTrue();
        assertThat(read.budget()).isEqualTo(BudgetHint.MID);
    }

    /** With the days still unknown the chat goes on asking, and the taste may well be its next question. */
    @Test
    void withNotesAsTaste_leavesTheBriefAlone_whileSomethingElseIsMissingToo() {
        Brief noDays = new Brief(null, 6, List.of(), null, null, null, DayEdge.MORNING, DayEdge.EVENING, NOTES);

        assertThat(noDays.withNotesAsTaste()).isEqualTo(noDays);
    }

    @Test
    void withNotesAsTaste_leavesTheBriefAlone_whenItHasTasteOrNoNotes() {
        Brief withVibe = new Brief(2, 6, List.of(), "wild", null, null, DayEdge.MORNING, DayEdge.EVENING, NOTES);
        Brief withoutNotes = new Brief(2, 6, List.of(), null, null, null, DayEdge.MORNING, DayEdge.EVENING, " ");

        assertThat(withVibe.withNotesAsTaste()).isEqualTo(withVibe);
        assertThat(withoutNotes.withNotesAsTaste()).isEqualTo(withoutNotes);
    }
}
