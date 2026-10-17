package com.myhive.backend.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PublicPathsTest {

    @Test
    void hubDestinationGetsKeywordPathPerLocale() {
        String expectedEnglishHub = "/prague-stag-do";
        String expectedGermanHub = "/de/jga-prag";

        assertThat(PublicPaths.destinationPath("prague", null)).isEqualTo(expectedEnglishHub);
        assertThat(PublicPaths.destinationPath("prague", "en")).isEqualTo(expectedEnglishHub);
        assertThat(PublicPaths.destinationPath("prague", "de")).isEqualTo(expectedGermanHub);
    }

    @Test
    void otherDestinationsKeepTheLegacyRoute() {
        String expectedEnglishRoute = "/destination/krakow";
        String expectedGermanRoute = "/de/destination/krakow";

        assertThat(PublicPaths.destinationPath("krakow", "")).isEqualTo(expectedEnglishRoute);
        assertThat(PublicPaths.destinationPath("krakow", "de")).isEqualTo(expectedGermanRoute);
    }

    @Test
    void nullSlugIsAProgrammingError() {
        assertThatThrownBy(() -> PublicPaths.destinationPath(null, "en"))
                .isInstanceOf(NullPointerException.class);
    }
}
