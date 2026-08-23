package com.myhive.backend.util;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves a translatable field for a requested locale. Base columns are
 * English; a translation wins only when present and non-blank, so a
 * half-translated record degrades field-by-field to English rather than
 * disappearing. {@code locale == null} means "no localization requested"
 * (the admin/raw view) and always yields the base value.
 */
public final class Translations {

    public static final String DEFAULT_LOCALE = "en";

    private Translations() {}

    /** Lower-cased two-letter locale, or null when none/default was requested. */
    public static String normalize(String locale) {
        if (locale == null || locale.isBlank()) {
            return null;
        }
        String lc = locale.trim().toLowerCase(Locale.ROOT);
        return DEFAULT_LOCALE.equals(lc) ? null : lc;
    }

    public static String pick(Map<String, Map<String, String>> translations,
                              String locale, String field, String base) {
        if (locale == null || translations == null) {
            return base;
        }
        Map<String, String> fields = translations.get(locale);
        if (fields == null) {
            return base;
        }
        String value = fields.get(field);
        return value == null || value.isBlank() ? base : value;
    }
}
