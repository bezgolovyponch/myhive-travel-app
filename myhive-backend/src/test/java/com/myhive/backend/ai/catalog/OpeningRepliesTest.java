package com.myhive.backend.ai.catalog;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpeningRepliesTest {

    @Test
    void offersTheMostRepeatedBundlesFirst_cappedAtFour() {
        int expectedChips = OpeningReplies.MAX;
        List<CatalogActivity> everything = List.of(
                activity("Prague Pub Crawl", "nightlife"),
                activity("Clay Pigeon Shooting", "extreme"),
                activity("Czech dinner with national show", "food-and-drink"),
                activity("Dwarf Hire", "prank"),
                activity("Go-Karting Experience", "extreme"));

        List<String> chips = OpeningReplies.forCatalog(everything, "en");

        assertThat(chips).hasSize(expectedChips).containsExactly("Bar crawl + club night",
                "Shooting range + night out", "Steak dinner with a show", "A prank on the groom");
    }

    /** The slugs production really has: the chips must not depend on the ones the seed data happens to use. */
    @Test
    void readsCategorySlugsAsWords_whateverTheEnvironmentCallsThem() {
        List<CatalogActivity> production = List.of(
                activity("AK-47 and Glock 17", "guns-and-bullets"),
                activity("Dog chase", "stag-hot-babies-and-pranks"));

        assertThat(OpeningReplies.forCatalog(production, "en"))
                .containsExactly("Shooting range + night out", "A prank on the groom");
    }

    /** A chip must never promise what the catalog cannot plan: no karting on offer, no karting chip. */
    @Test
    void skipsABundleTheCatalogCannotDeliver_andBackfills() {
        List<CatalogActivity> noShowNoShooting = List.of(
                activity("Nightclub VIP Experience", "nightlife"),
                activity("Kartfahren", "extreme"));

        assertThat(OpeningReplies.forCatalog(noShowNoShooting, "de"))
                .containsExactly("Kneipentour + Club", "Kart tagsüber, Club nachts");
    }

    @Test
    void matchesWholeWordsOnly() {
        List<CatalogActivity> lookalikes = List.of(
                activity("Burgundy Wine Tasting", "food-and-drink"),
                activity("Beer Spa with shower", "wellness"));

        assertThat(OpeningReplies.forCatalog(lookalikes, "en")).isEmpty();
    }

    @Test
    void anEmptyCatalog_offersNothing() {
        assertThat(OpeningReplies.forCatalog(List.of(), "en")).isEmpty();
    }

    @Test
    void unknownLocale_fallsBackToEnglish() {
        assertThat(OpeningReplies.forCatalog(List.of(activity("Pub Crawl", "nightlife")), "fr"))
                .containsExactly("Bar crawl + club night");
    }

    private static CatalogActivity activity(String name, String categorySlug) {
        return new CatalogActivity(UUID.randomUUID(), "slug", name, "line", 90, true, new BigDecimal("20.00"), null,
                null, List.of(categorySlug));
    }
}
