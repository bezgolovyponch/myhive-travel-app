package com.myhive.backend.ai.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BriefMergerTest {

    @Test
    void merge_nullFieldsKeepCurrentValues() {
        Brief current = new Brief(3, 8, List.of("nightlife"), "loud", null, BudgetHint.MID,
                DayEdge.EVENING, DayEdge.MORNING, null);
        Brief update = new Brief(null, 10, null, null, "no museums", null, null, null, null);

        Brief merged = BriefMerger.merge(current, update);

        assertThat(merged.days()).isEqualTo(3);
        assertThat(merged.groupSize()).isEqualTo(10);
        assertThat(merged.categorySlugs()).containsExactly("nightlife");
        assertThat(merged.vibe()).isEqualTo("loud");
        assertThat(merged.dislikes()).isEqualTo("no museums");
        assertThat(merged.budget()).isEqualTo(BudgetHint.MID);
    }

    @Test
    void merge_emptyCategoryListDoesNotClearCategories() {
        Brief current = Brief.empty().withCategorySlugs(List.of("driving"));
        Brief merged = BriefMerger.merge(current, Brief.empty());
        assertThat(merged.categorySlugs()).containsExactly("driving");
    }

    @Test
    void merge_clampsDaysAndGroupSizeAndTrimsText() {
        Brief update = new Brief(12, 1, null, "x".repeat(400), null, null, null, null, null);
        Brief merged = BriefMerger.merge(Brief.empty(), update);
        assertThat(merged.days()).isEqualTo(7);
        assertThat(merged.groupSize()).isEqualTo(2);
        assertThat(merged.vibe()).hasSize(300);
    }

    @Test
    void isReady_requiresDaysGroupAndTasteSignal() {
        assertThat(Brief.empty().isReady()).isFalse();
        assertThat(Brief.empty().missingFields()).containsExactly("days", "groupSize", "preferences");
        Brief withVibe = new Brief(2, 6, List.of(), "chill", null, null, null, null, null);
        assertThat(withVibe.isReady()).isTrue();
        Brief withCategories = new Brief(2, 6, List.of("gaming"), null, null, null, null, null, null);
        assertThat(withCategories.isReady()).isTrue();
    }

    @Test
    void defaults_arrivalAfternoonDepartureMorning() {
        Brief b = Brief.empty();
        assertThat(b.arrivalOrDefault()).isEqualTo(DayEdge.AFTERNOON);
        assertThat(b.departureOrDefault()).isEqualTo(DayEdge.MORNING);
    }
}
