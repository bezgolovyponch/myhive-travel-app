package com.myhive.backend.util;

import com.github.slugify.Slugify;
import com.myhive.backend.exception.BadRequestException;

import java.util.function.Predicate;

public final class SlugUtils {

    private static final int MAX_UNIQUE_ATTEMPTS = 1000;

    private static final Slugify SLUGIFY = Slugify.builder()
            .lowerCase(true)
            .transliterator(true)
            .build();

    private SlugUtils() {
    }

    public static String generateSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String slug = SLUGIFY.slugify(input);
        if (slug.isEmpty()) {
            throw new BadRequestException(
                    "Cannot generate a URL slug from the provided name. Use Latin, Cyrillic, or accented characters.");
        }
        return slug;
    }

    public static String ensureUnique(String baseSlug, Predicate<String> existsCheck) {
        if (!existsCheck.test(baseSlug)) {
            return baseSlug;
        }
        for (int suffix = 2; suffix <= MAX_UNIQUE_ATTEMPTS; suffix++) {
            String candidate = baseSlug + "-" + suffix;
            if (!existsCheck.test(candidate)) {
                return candidate;
            }
        }
        throw new BadRequestException("Unable to generate a unique slug for: " + baseSlug);
    }

    public static String generateUniqueSlug(String input, Predicate<String> existsCheck) {
        return ensureUnique(generateSlug(input), existsCheck);
    }

    public static String resolveSlug(String customSlug, String fallbackInput, Predicate<String> existsCheck) {
        if (customSlug != null && !customSlug.isBlank()) {
            String sanitized = generateSlug(customSlug);
            return ensureUnique(sanitized, existsCheck);
        }
        return generateUniqueSlug(fallbackInput, existsCheck);
    }

    public static boolean needsUpdate(String dtoSlug, String currentSlug, String dtoName, String currentName) {
        boolean slugProvided = dtoSlug != null && !dtoSlug.isBlank();
        boolean slugCleared = !slugProvided && dtoSlug != null;
        boolean slugChanged = slugProvided && !dtoSlug.equals(currentSlug);
        boolean nameChanged = !dtoName.equals(currentName);
        return slugChanged || slugCleared || nameChanged;
    }

    public static String resolveForUpdate(String dtoSlug, String dtoName, String currentSlug,
                                          Predicate<String> existsCheck) {
        boolean slugCleared = dtoSlug != null && dtoSlug.isBlank();
        return resolveSlug(slugCleared ? null : dtoSlug, dtoName, existsCheck);
    }
}
