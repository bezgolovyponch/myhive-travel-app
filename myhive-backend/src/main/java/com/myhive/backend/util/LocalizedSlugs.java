package com.myhive.backend.util;

import com.myhive.backend.exception.BadRequestException;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validation of the hand-written German URL slugs ({@code slugDe}) that admins type in. */
public final class LocalizedSlugs {

    private static final Pattern ALLOWED = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private LocalizedSlugs() {
    }

    /**
     * Trims and lower-cases a requested German slug; blank means "no German slug" and yields {@code null}.
     *
     * @throws BadRequestException when the value holds anything but lowercase letters and digits joined by single hyphens
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String slug = raw.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED.matcher(slug).matches()) {
            throw new BadRequestException("slugDe: use lowercase letters, digits and hyphens");
        }
        return slug;
    }
}
