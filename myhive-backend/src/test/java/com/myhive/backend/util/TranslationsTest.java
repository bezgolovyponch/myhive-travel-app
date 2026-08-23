package com.myhive.backend.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TranslationsTest {

    private static final Map<String, Map<String, String>> TR =
            Map.of("de", Map.of("name", "Prag", "description", "   "));

    @Test
    void normalizeTreatsDefaultAndBlankAsNoLocale() {
        assertNull(Translations.normalize(null));
        assertNull(Translations.normalize(" "));
        assertNull(Translations.normalize("en"));
        assertNull(Translations.normalize("EN"));
        assertEquals("de", Translations.normalize(" De "));
    }

    @Test
    void pickFallsBackFieldByField() {
        assertEquals("Prag", Translations.pick(TR, "de", "name", "Prague"));
        // blank translation → base, so a half-translated record never shows empty text
        assertEquals("Spires", Translations.pick(TR, "de", "description", "Spires"));
        // unknown field / locale / no translations at all → base
        assertEquals("CZ", Translations.pick(TR, "de", "country", "CZ"));
        assertEquals("Prague", Translations.pick(TR, "es", "name", "Prague"));
        assertEquals("Prague", Translations.pick(null, "de", "name", "Prague"));
        assertEquals("Prague", Translations.pick(TR, null, "name", "Prague"));
    }

    @Test
    void converterRoundTripsAndTreatsEmptyAsNull() {
        TranslationsConverter converter = new TranslationsConverter();
        String column = converter.convertToDatabaseColumn(TR);
        assertEquals(TR, converter.convertToEntityAttribute(column));
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToDatabaseColumn(Map.of()));
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToEntityAttribute(" "));
    }
}
