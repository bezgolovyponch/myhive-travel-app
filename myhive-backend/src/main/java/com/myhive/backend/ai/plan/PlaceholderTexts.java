package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.util.Translations;

import java.util.Map;

/**
 * The stock names a package and a day carry before - or without - any model-written copy: the tier's
 * name and "Day N", in the plan's language. Used by the deterministic fallback for its draft and by
 * {@link PlanTextWriter} for the skeleton that is published while the texts are still being written.
 */
public final class PlaceholderTexts {

    private static final String ENGLISH = "en";

    private static final Map<String, String[]> TITLES = Map.of(
            ENGLISH, new String[] {"Warm-up", "Main Event", "Full Send"},
            "de", new String[] {"Warm-up", "Hauptprogramm", "Volle Kanne"});
    private static final Map<String, String> DAY_LABEL = Map.of(ENGLISH, "Day", "de", "Tag");

    private PlaceholderTexts() {
    }

    public static String packageTitle(Tier tier, String locale) {
        return TITLES.get(key(locale))[tier.ordinal()];
    }

    public static String dayTitle(int dayNumber, String locale) {
        return DAY_LABEL.get(key(locale)) + " " + dayNumber;
    }

    private static String key(String locale) {
        String normalized = Translations.normalize(locale);
        return normalized != null && TITLES.containsKey(normalized) ? normalized : ENGLISH;
    }
}
