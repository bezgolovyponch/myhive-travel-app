package com.myhive.backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PublicPathsTest {

    @Test
    void hubDestinationGetsKeywordPathPerLocale() {
        assertEquals("/prague-stag-do", PublicPaths.destinationPath("prague", null));
        assertEquals("/prague-stag-do", PublicPaths.destinationPath("prague", "en"));
        assertEquals("/de/jga-prag", PublicPaths.destinationPath("prague", "de"));
    }

    @Test
    void otherDestinationsKeepTheLegacyRoute() {
        assertEquals("/destination/krakow", PublicPaths.destinationPath("krakow", ""));
        assertEquals("/de/destination/krakow", PublicPaths.destinationPath("krakow", "de"));
    }

    @Test
    void nullSlugDoesNotThrow() {
        assertEquals("/destination/null", PublicPaths.destinationPath(null, "en"));
    }
}
