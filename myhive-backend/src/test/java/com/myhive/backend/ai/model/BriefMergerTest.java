package com.myhive.backend.ai.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BriefMergerTest {

    @Test
    void merge_nullFieldsKeepCurrentValues() {
        int expectedDays = 3;
        int expectedGroupSize = 10;
        String expectedCategory = "nightlife";
        String expectedVibe = "loud";
        String expectedDislikes = "no museums";
        BudgetHint expectedBudget = BudgetHint.MID;
        Brief current = new Brief(expectedDays, 8, List.of(expectedCategory), expectedVibe, null,
                expectedBudget, DayEdge.EVENING, DayEdge.MORNING, null);
        Brief update = new Brief(null, expectedGroupSize, null, null, expectedDislikes, null, null, null, null);

        Brief merged = BriefMerger.merge(current, update);

        assertThat(merged.days()).isEqualTo(expectedDays);
        assertThat(merged.groupSize()).isEqualTo(expectedGroupSize);
        assertThat(merged.categorySlugs()).containsExactly(expectedCategory);
        assertThat(merged.vibe()).isEqualTo(expectedVibe);
        assertThat(merged.dislikes()).isEqualTo(expectedDislikes);
        assertThat(merged.budget()).isEqualTo(expectedBudget);
    }

    @Test
    void merge_emptyCategoryListDoesNotClearCategories() {
        String expectedCategory = "driving";
        Brief current = Brief.empty().withCategorySlugs(List.of(expectedCategory));

        Brief merged = BriefMerger.merge(current, Brief.empty());

        assertThat(merged.categorySlugs()).containsExactly(expectedCategory);
    }

    @Test
    void merge_clampsDaysAndGroupSizeAndTrimsText() {
        int expectedMaxDays = Brief.MAX_DAYS;
        int expectedMinGroupSize = Brief.MIN_GROUP;
        int expectedMaxTextLength = Brief.MAX_TEXT;
        Brief update = new Brief(12, 1, null, "x".repeat(400), null, null, null, null, null);

        Brief merged = BriefMerger.merge(Brief.empty(), update);

        assertThat(merged.days()).isEqualTo(expectedMaxDays);
        assertThat(merged.groupSize()).isEqualTo(expectedMinGroupSize);
        assertThat(merged.vibe()).hasSize(expectedMaxTextLength);
    }

    @Test
    void isReady_requiresDaysGroupAndTasteSignal() {
        List<String> expectedMissing = List.of("days", "groupSize", "preferences");

        assertThat(Brief.empty().isReady()).isFalse();
        assertThat(Brief.empty().missingFields()).containsExactlyElementsOf(expectedMissing);

        Brief withVibe = new Brief(2, 6, List.of(), "chill", null, null, null, null, null);
        assertThat(withVibe.isReady()).isTrue();

        Brief withCategories = new Brief(2, 6, List.of("gaming"), null, null, null, null, null, null);
        assertThat(withCategories.isReady()).isTrue();
    }

    @Test
    void defaults_arrivalAfternoonDepartureMorning() {
        DayEdge expectedArrival = DayEdge.AFTERNOON;
        DayEdge expectedDeparture = DayEdge.MORNING;
        Brief b = Brief.empty();

        assertThat(b.arrivalOrDefault()).isEqualTo(expectedArrival);
        assertThat(b.departureOrDefault()).isEqualTo(expectedDeparture);
    }
}
