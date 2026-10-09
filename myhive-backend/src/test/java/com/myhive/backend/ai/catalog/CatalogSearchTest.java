package com.myhive.backend.ai.catalog;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSearchTest {

    private final List<CatalogActivity> catalog = new ArrayList<>();
    // In the catalog's own order: most popular first.
    private final CatalogActivity riverCruise = activity("River Boat Cruise", "Sightseeing on the Vltava", "wellness");
    private final CatalogActivity steakShow = activity("Steak & Private Show", "Steak, beer and a show", "food-and-drink");
    private final CatalogActivity beerSpa = activity("Beer Spa", "Soak in it", "wellness");
    private final CatalogActivity beerTasting = activity("Beer Tasting Experience", "Six Czech beers", "wellness");
    private final CatalogActivity boatParty = activity("Sunset Boat Party", "Dance on a catamaran", "nightlife");
    private final CatalogActivity tikiBoat = activity("Tiki Boat", "A tropical bar on the river", "wellness");
    private final CatalogActivity hotelShow = activity("Private Hotel Show", "In your apartment",
            "stag-hot-babies-and-pranks");
    private final CatalogActivity czechDinner = activity("Czech dinner with national show", "Folk music", "food-and-drink");

    private CatalogActivity activity(String name, String oneLine, String category) {
        CatalogActivity created = new CatalogActivity(UUID.randomUUID(), name, name, oneLine, 90, true,
                new BigDecimal("20.00"), null, null, List.of(category));
        catalog.add(created);
        return created;
    }

    /** As asked in a live chat: boats got a beer spa under them, and the boat party was never shown. */
    @Test
    void aKindOfActivity_getsEveryActivityOfThatKind_andNothingThatOnlySharesACategory() {
        List<String> modelPicks = List.of(riverCruise.name());

        CatalogSearch.Result found = CatalogSearch.find("what boats do you have?", modelPicks, catalog, Set.of());

        assertThat(found.names()).containsExactly(riverCruise.name(), boatParty.name(), tikiBoat.name());
        assertThat(found.split()).isFalse();
    }

    /** No boat comes with a show: the best of each, side by side, and the caller is told it is a split. */
    @Test
    void twoKindsNoActivityIsBothOf_areOfferedSideBySide() {
        CatalogSearch.Result found = CatalogSearch.find("river cruise with private show", List.of(), catalog, Set.of());

        assertThat(found.split()).isTrue();
        assertThat(found.names()).containsExactly(riverCruise.name(), boatParty.name(), steakShow.name(),
                hotelShow.name());
    }

    /** When something is all of it, only that is offered. */
    @Test
    void twoKindsOneActivityIsBothOf_areAnsweredWithThatActivityAlone() {
        CatalogSearch.Result found = CatalogSearch.find("dinner with strippers", List.of(czechDinner.name()), catalog,
                Set.of());

        assertThat(found.names()).containsExactly(steakShow.name());
        assertThat(found.split()).isFalse();
    }

    @Test
    void whatThePlanHoldsAlready_isNotOfferedAgain() {
        CatalogSearch.Result found = CatalogSearch.find("a boat", List.of(), catalog, Set.of(riverCruise.id()));

        assertThat(found.names()).containsExactly(boatParty.name(), tikiBoat.name());
    }

    /** Words that name no kind and no activity: the model's own picks stand, unfilled. */
    @Test
    void aWishTheWordsSayNothingAbout_keepsTheModelsPicks() {
        CatalogSearch.Result found = CatalogSearch.find("something for the groom", List.of(hotelShow.name()), catalog,
                Set.of());

        assertThat(found.names()).containsExactly(hotelShow.name());
    }

    @Test
    void asksToAdd_tellsAnOrderFromAQuestion() {
        assertThat(CatalogSearch.asksToAdd("I would like to add the river cruise")).isTrue();
        assertThat(CatalogSearch.asksToAdd("put karting in")).isTrue();
        assertThat(CatalogSearch.asksToAdd("river cruise")).isFalse();
        assertThat(CatalogSearch.asksToAdd("what boats do you have?")).isFalse();
    }

    @Test
    void asksForAKind_isAQuestionAboutWhatThereIs_notAboutOneActivity() {
        assertThat(CatalogSearch.asksForAKind("what boats do you have?")).isTrue();
        assertThat(CatalogSearch.asksForAKind("any ideas for beer?")).isTrue();
        assertThat(CatalogSearch.asksForAKind("is the beer spa far from the centre?")).isFalse();
        assertThat(CatalogSearch.asksForAKind("swap the beer bike for karting")).isFalse();
    }

    /** "Three ideas" is three; a word in common with no kind in common is not an answer. */
    @Test
    void theNumberAskedFor_isTheNumberOffered_andAWordAloneIsNotAKind() {
        activity("Water gun battle", "Soak each other", "extreme");

        assertThat(CatalogSearch.find("two boats please", List.of(), catalog, Set.of()).names()).hasSize(2);
        assertThat(CatalogSearch.find("something on the water", List.of(), catalog, Set.of()).names())
                .doesNotContain("Water gun battle");
    }
}
