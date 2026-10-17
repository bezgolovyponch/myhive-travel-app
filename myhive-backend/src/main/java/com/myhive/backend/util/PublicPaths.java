package com.myhive.backend.util;

import java.util.Map;
import java.util.Objects;

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
        Objects.requireNonNull(destinationSlug, "destinationSlug");
        String locale = Translations.normalize(localeCode);
        String prefix = locale == null ? "" : "/" + locale;
        String hubKey = locale == null ? DEFAULT_LOCALE : locale;
        Map<String, String> hub = HUBS.get(destinationSlug);
        if (hub != null) {
            return prefix + hub.getOrDefault(hubKey, hub.get(DEFAULT_LOCALE));
        }
        return prefix + "/destination/" + destinationSlug;
    }
}
