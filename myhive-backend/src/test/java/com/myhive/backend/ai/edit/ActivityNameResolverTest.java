package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.catalog.CatalogActivity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityNameResolverTest {

    private final List<CatalogActivity> catalog = new ArrayList<>();
    private final CatalogActivity beerSpa = activity("Beer Spa");
    private final CatalogActivity beerBike = activity("Beer Bike");
    private final CatalogActivity riverCruise = activity("River Cruise");
    private final CatalogActivity shootingRange = activity("Shooting Range");

    private CatalogActivity activity(String name) {
        CatalogActivity created = new CatalogActivity(UUID.randomUUID(), name.toLowerCase(Locale.ROOT).replace(' ', '-'),
                name, "one line", 90, true, new BigDecimal("20.00"), null, "img", List.of());
        catalog.add(created);
        return created;
    }

    @Test
    void resolve_matchesTheExactNameIgnoringCaseAndWhitespace() {
        CatalogActivity expectedActivity = beerSpa;

        ActivityNameResolver.Resolution resolution = ActivityNameResolver.resolve("  bEER   spa ", catalog);

        assertThat(resolution).isEqualTo(new ActivityNameResolver.Found(expectedActivity));
    }

    @Test
    void resolve_matchesAUniqueContainsCandidate() {
        CatalogActivity expectedActivity = riverCruise;

        ActivityNameResolver.Resolution resolution = ActivityNameResolver.resolve("Cruise", catalog);

        assertThat(resolution).isEqualTo(new ActivityNameResolver.Found(expectedActivity));
    }

    @Test
    void resolve_matchesACatalogNameContainedInTheQuery() {
        CatalogActivity expectedActivity = riverCruise;

        ActivityNameResolver.Resolution resolution =
                ActivityNameResolver.resolve("the river cruise on the Vltava please", catalog);

        assertThat(resolution).isEqualTo(new ActivityNameResolver.Found(expectedActivity));
    }

    @Test
    void resolve_listsTheCandidatesSortedWhenSeveralMatch() {
        List<String> expectedCandidates = List.of(beerBike.name(), beerSpa.name());

        ActivityNameResolver.Resolution resolution = ActivityNameResolver.resolve("Beer", catalog);

        assertThat(resolution).isEqualTo(new ActivityNameResolver.Ambiguous(expectedCandidates));
    }

    @Test
    void resolve_isAmbiguousWhenTwoCatalogRowsShareTheSameName() {
        String expectedName = shootingRange.name();
        activity(expectedName);

        ActivityNameResolver.Resolution resolution = ActivityNameResolver.resolve(expectedName, catalog);

        assertThat(resolution).isEqualTo(new ActivityNameResolver.Ambiguous(List.of(expectedName, expectedName)));
    }

    @Test
    void resolve_isNotFoundWhenNothingMatches() {
        ActivityNameResolver.Resolution resolution = ActivityNameResolver.resolve("Bungee Jumping", catalog);

        assertThat(resolution).isEqualTo(new ActivityNameResolver.NotFound());
    }

    @Test
    void resolve_underThreeCharactersMatchesOnlyExactly() {
        CatalogActivity expectedActivity = activity("Go");

        ActivityNameResolver.Resolution exact = ActivityNameResolver.resolve("go", catalog);
        ActivityNameResolver.Resolution tooShortForContains = ActivityNameResolver.resolve("Be", catalog);

        assertThat(exact).isEqualTo(new ActivityNameResolver.Found(expectedActivity));
        assertThat(tooShortForContains).isEqualTo(new ActivityNameResolver.NotFound());
    }

    @Test
    void resolve_isNotFoundForABlankOrMissingName() {
        assertThat(ActivityNameResolver.resolve("   ", catalog)).isEqualTo(new ActivityNameResolver.NotFound());
        assertThat(ActivityNameResolver.resolve(null, catalog)).isEqualTo(new ActivityNameResolver.NotFound());
    }
}
