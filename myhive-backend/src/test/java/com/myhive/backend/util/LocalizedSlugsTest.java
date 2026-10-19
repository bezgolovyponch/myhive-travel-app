package com.myhive.backend.util;

import com.myhive.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalizedSlugsTest {

    @Test
    void normalize_trimsAndLowerCases() {
        String expectedSlug = "axtwerfen";

        String result = LocalizedSlugs.normalize("  Axtwerfen ");

        assertThat(result).isEqualTo(expectedSlug);
    }

    @Test
    void normalize_blankIsNull() {
        assertThat(LocalizedSlugs.normalize(null)).isNull();
        assertThat(LocalizedSlugs.normalize("")).isNull();
        assertThat(LocalizedSlugs.normalize("   ")).isNull();
    }

    @Test
    void normalize_rejectsUmlautsAndSpaces() {
        assertThatThrownBy(() -> LocalizedSlugs.normalize("schädel"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("slugDe: use lowercase letters, digits and hyphens");
        assertThatThrownBy(() -> LocalizedSlugs.normalize("axt werfen"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void normalize_rejectsLeadingTrailingOrDoubleHyphens() {
        assertThatThrownBy(() -> LocalizedSlugs.normalize("-"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> LocalizedSlugs.normalize("-axt"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> LocalizedSlugs.normalize("axt-"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> LocalizedSlugs.normalize("axt--werfen"))
                .isInstanceOf(BadRequestException.class);
    }
}
