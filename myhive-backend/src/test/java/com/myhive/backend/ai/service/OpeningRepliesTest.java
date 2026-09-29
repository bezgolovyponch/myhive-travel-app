package com.myhive.backend.ai.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpeningRepliesTest {

    @Test
    void offersTheMostRepeatedBundlesFirst_cappedAtFour() {
        List<String> everything = List.of("nightlife", "shooting", "dining", "show", "prank", "driving");

        assertThat(OpeningReplies.forCategories(everything, "en")).containsExactly(
                "Bar crawl + club night", "Shooting range + night out", "Steak dinner with a show", "A prank on the groom");
    }

    /** A chip must never promise what the destination cannot plan: no show category, no dinner-with-a-show chip. */
    @Test
    void skipsABundleTheDestinationHasNoCategoriesFor_andBackfills() {
        List<String> noShow = List.of("nightlife", "dining", "driving");

        assertThat(OpeningReplies.forCategories(noShow, "de"))
                .containsExactly("Kneipentour + Club", "Kart tagsüber, Club nachts");
    }

    @Test
    void unknownLocale_fallsBackToEnglish() {
        assertThat(OpeningReplies.forCategories(List.of("nightlife"), "fr")).containsExactly("Bar crawl + club night");
    }
}
