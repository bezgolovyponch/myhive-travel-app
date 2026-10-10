package com.myhive.backend.util;

import com.myhive.backend.dto.ActivityExtraDTO;
import com.myhive.backend.entity.ActivityExtra;
import com.myhive.backend.entity.ExtraPricing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityExtrasTest {

    @Test
    void normalizeGeneratesCodeFromNameAndKeepsOrder() {
        ActivityExtra given = new ActivityExtra(null, "Private Show", "15 min", new BigDecimal("250"), ExtraPricing.PER_GROUP, null);
        List<ActivityExtra> result = ActivityExtras.normalize(List.of(given));
        String expectedCode = "private-show";
        assertEquals(expectedCode, result.getFirst().code());
        assertEquals(new BigDecimal("250.00"), result.getFirst().price());
    }

    @Test
    void normalizeRejectsDuplicateCodes() {
        ActivityExtra a = new ActivityExtra("show", "Show", null, BigDecimal.TEN, ExtraPricing.PER_GROUP, null);
        ActivityExtra b = new ActivityExtra("show", "Other", null, BigDecimal.ONE, ExtraPricing.PER_PERSON, null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ActivityExtras.normalize(List.of(a, b)));
        assertTrue(e.getMessage().contains("show"));
    }

    @Test
    void normalizeRejectsTooManyNegativePriceAndMissingPricing() {
        List<ActivityExtra> eleven = IntStream.range(0, 11)
                .mapToObj(i -> new ActivityExtra("e" + i, "E" + i, null, BigDecimal.ONE, ExtraPricing.PER_GROUP, null)).toList();
        assertThrows(IllegalArgumentException.class, () -> ActivityExtras.normalize(eleven));
        assertThrows(IllegalArgumentException.class, () -> ActivityExtras.normalize(List.of(
                new ActivityExtra("x", "X", null, new BigDecimal("-1"), ExtraPricing.PER_GROUP, null))));
        assertThrows(IllegalArgumentException.class, () -> ActivityExtras.normalize(List.of(
                new ActivityExtra("x", "X", null, BigDecimal.ONE, null, null))));
    }

    @Test
    void parseJsonAndCanonicalJsonRoundTrip() {
        String expectedJson = "[{\"code\":\"private-show\",\"name\":\"Private show\",\"description\":\"15 min\","
                + "\"price\":250.00,\"pricing\":\"PER_GROUP\",\"translations\":{\"de\":{\"name\":\"Private Show\"}}}]";
        List<ActivityExtra> parsed = ActivityExtras.parseJson(expectedJson);
        assertEquals(expectedJson, ActivityExtras.canonicalJson(parsed));
        assertEquals("", ActivityExtras.canonicalJson(null));
        assertEquals("", ActivityExtras.canonicalJson(List.of()));
    }

    @Test
    void parseJsonRejectsMalformedInputWithAMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ActivityExtras.parseJson("[{\"name\": “Show”}]"));
        assertNotNull(e.getMessage());
    }

    @Test
    void toDtosLocalisesNameAndDescriptionAndHidesTranslationsForALocale() {
        ActivityExtra extra = new ActivityExtra("show", "Private show", "15 min", new BigDecimal("250.00"), ExtraPricing.PER_GROUP,
                Map.of("de", Map.of("name", "Private Show", "description", "15 Minuten")));
        ActivityExtraDTO de = ActivityExtras.toDtos(List.of(extra), "de").getFirst();
        assertEquals("Private Show", de.getName());
        assertEquals("15 Minuten", de.getDescription());
        assertNull(de.getTranslations());
        ActivityExtraDTO raw = ActivityExtras.toDtos(List.of(extra), null).getFirst();
        assertEquals("Private show", raw.getName());
        assertNotNull(raw.getTranslations());
    }

    @Test
    void selectKeepsTheNamedExtrasInCatalogOrderAndIgnoresUnknownCodes() {
        ActivityExtra show = new ActivityExtra("show", "Show", null, BigDecimal.TEN, ExtraPricing.PER_GROUP, null);
        ActivityExtra photos = new ActivityExtra("photos", "Photos", null, BigDecimal.ONE, ExtraPricing.PER_PERSON, null);
        ActivityExtra drinks = new ActivityExtra("drinks", "Drinks", null, BigDecimal.ONE, ExtraPricing.PER_PERSON, null);
        List<ActivityExtra> expected = List.of(show, drinks);

        List<ActivityExtra> selected = ActivityExtras.select(List.of(show, photos, drinks), List.of("drinks", "nope", "show"));

        assertEquals(expected, selected);
        assertTrue(ActivityExtras.select(null, List.of("show")).isEmpty());
        assertTrue(ActivityExtras.select(List.of(show), null).isEmpty());
    }
}
