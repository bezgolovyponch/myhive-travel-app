package com.myhive.backend.util;

import com.myhive.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlugUtilsTest {

    @Test
    void generateSlug_basicInput_returnsKebabCase() {
        assertEquals("prague-castle", SlugUtils.generateSlug("Prague Castle"));
    }

    @Test
    void generateSlug_mixedCase_returnsLowercase() {
        assertEquals("sunset-boat-party", SlugUtils.generateSlug("Sunset Boat Party"));
    }

    @Test
    void generateSlug_unicodeAccents_stripsAccents() {
        assertEquals("zurich", SlugUtils.generateSlug("Z\u00fcrich"));
    }

    @Test
    void generateSlug_specialCharacters_replacesWithHyphens() {
        assertEquals("hello-world", SlugUtils.generateSlug("Hello, World!"));
    }

    @Test
    void generateSlug_multipleSpacesAndHyphens_collapsesToSingleHyphen() {
        assertEquals("a-b-c", SlugUtils.generateSlug("  a   b   c  "));
    }

    @Test
    void generateSlug_leadingTrailingSpecials_trims() {
        assertEquals("test", SlugUtils.generateSlug("---test---"));
    }

    @Test
    void generateSlug_nullInput_returnsEmpty() {
        assertEquals("", SlugUtils.generateSlug(null));
    }

    @Test
    void generateSlug_blankInput_returnsEmpty() {
        assertEquals("", SlugUtils.generateSlug("   "));
    }

    @Test
    void generateSlug_numbersPreserved() {
        assertEquals("top-5-destinations-for-2026", SlugUtils.generateSlug("Top 5 Destinations for 2026"));
    }

    @Test
    void generateSlug_germanEszett_transliterates() {
        assertEquals("strasse", SlugUtils.generateSlug("Straße"));
    }

    @Test
    void generateSlug_spanishTilde_transliterates() {
        assertEquals("espana", SlugUtils.generateSlug("España"));
    }

    @Test
    void generateSlug_cyrillic_transliterates() {
        assertEquals("moskva", SlugUtils.generateSlug("Москва"));
    }

    @Test
    void generateSlug_czechDiacritics_transliterates() {
        assertEquals("pribram-na-sumave", SlugUtils.generateSlug("Příbram na Šumavě"));
    }

    @Test
    void generateSlug_frenchAccents_transliterates() {
        assertEquals("cafe-resume-naive", SlugUtils.generateSlug("Café résumé naïve"));
    }

    @Test
    void generateSlug_emojiOnly_throwsBadRequest() {
        assertThrows(BadRequestException.class, () -> SlugUtils.generateSlug("\uD83D\uDD25\uD83C\uDF0D"));
    }

    @Test
    void generateSlug_symbolsOnly_throwsBadRequest() {
        assertThrows(BadRequestException.class, () -> SlugUtils.generateSlug("@#$%^&*"));
    }

    @Test
    void generateUniqueSlug_generatesAndEnsuresUnique() {
        Set<String> existing = Set.of("prague");
        String result = SlugUtils.generateUniqueSlug("Prague", existing::contains);
        assertEquals("prague-2", result);
    }

    @Test
    void ensureUnique_noCollision_returnsBaseSlug() {
        String result = SlugUtils.ensureUnique("prague", slug -> false);
        assertEquals("prague", result);
    }

    @Test
    void ensureUnique_singleCollision_appendsSuffix() {
        Set<String> existing = Set.of("prague");
        String result = SlugUtils.ensureUnique("prague", existing::contains);
        assertEquals("prague-2", result);
    }

    @Test
    void ensureUnique_multipleCollisions_incrementsSuffix() {
        Set<String> existing = Set.of("prague", "prague-2", "prague-3");
        String result = SlugUtils.ensureUnique("prague", existing::contains);
        assertEquals("prague-4", result);
    }

    @Test
    void resolveSlug_customSlugProvided_sanitizesAndUses() {
        String result = SlugUtils.resolveSlug("My Custom Slug!", "Fallback Name", slug -> false);
        assertEquals("my-custom-slug", result);
    }

    @Test
    void resolveSlug_customSlugBlank_fallsBackToName() {
        String result = SlugUtils.resolveSlug("", "Prague Castle", slug -> false);
        assertEquals("prague-castle", result);
    }

    @Test
    void resolveSlug_customSlugNull_fallsBackToName() {
        String result = SlugUtils.resolveSlug(null, "Prague Castle", slug -> false);
        assertEquals("prague-castle", result);
    }

    @Test
    void resolveSlug_customSlugWithCollision_appendsSuffix() {
        Set<String> existing = Set.of("custom-slug");
        String result = SlugUtils.resolveSlug("Custom Slug", "Fallback", existing::contains);
        assertEquals("custom-slug-2", result);
    }
}
