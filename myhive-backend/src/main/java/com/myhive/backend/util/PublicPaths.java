package com.myhive.backend.util;

import java.util.Map;

/**
 * Public (frontend) URL paths the backend has to build itself, i.e. in emails.
 * Hand-kept mirror of DESTINATION_HUBS / LOCALIZED_PATHNAMES in
 * myhive-react-app/src/i18n/routes.js — change both together.
 */
public final class PublicPaths {

    private static final String DEFAULT_LOCALE = "en";

    /** destination slug -> (locale -> hub path). Cities absent here stay on /destination/{slug}. */
    private static final Map<String, Map<String, String>> HUBS = Map.of(
            "prague", Map.of("en", "/prague-stag-do", "de", "/jga-prag"));

    private PublicPaths() {}

    /** Locale-prefixed catalog path: "/prague-stag-do", "/de/jga-prag", "/de/destination/krakow". */
    public static String destinationPath(String destinationSlug, String localeCode) {
        String locale = localeCode == null || localeCode.isBlank() ? DEFAULT_LOCALE : localeCode;
        String prefix = DEFAULT_LOCALE.equals(locale) ? "" : "/" + locale;
        Map<String, String> hub = destinationSlug == null ? null : HUBS.get(destinationSlug);
        if (hub != null) {
            return prefix + hub.getOrDefault(locale, hub.get(DEFAULT_LOCALE));
        }
        return prefix + "/destination/" + destinationSlug;
    }
}
