package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.catalog.CatalogActivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Maps an activity name the chat model wrote onto a row of the catalog snapshot. The model is told to use
 * catalog names verbatim, but it paraphrases anyway ("the karting track please"), so an exact match is
 * tried first and a substring match second — in either direction, because the organizer's phrasing can be
 * either shorter or longer than the catalog name. Anything that matches more than one row is reported as
 * ambiguous rather than guessed: picking the wrong activity silently is worse than asking again.
 */
public final class ActivityNameResolver {

    /** Below this, a substring match is noise ("Be" would hit every "Beer …" row), so only an exact name counts. */
    private static final int MIN_FUZZY_LENGTH = 3;

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private ActivityNameResolver() {
    }

    public sealed interface Resolution permits Found, NotFound, Ambiguous {
    }

    public record Found(CatalogActivity activity) implements Resolution {
    }

    public record NotFound() implements Resolution {
    }

    public record Ambiguous(List<String> candidates) implements Resolution {
        public Ambiguous {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public static Resolution resolve(String name, List<CatalogActivity> catalog) {
        String query = normalise(name);
        if (query.isEmpty() || catalog == null || catalog.isEmpty()) {
            return new NotFound();
        }
        List<CatalogActivity> exact = new ArrayList<>();
        for (CatalogActivity activity : catalog) {
            if (normalise(activity.name()).equals(query)) {
                exact.add(activity);
            }
        }
        if (!exact.isEmpty()) {
            return exact.size() == 1 ? new Found(exact.get(0)) : ambiguous(exact);
        }
        if (query.length() < MIN_FUZZY_LENGTH) {
            return new NotFound();
        }
        List<CatalogActivity> partial = new ArrayList<>();
        for (CatalogActivity activity : catalog) {
            String candidate = normalise(activity.name());
            if (!candidate.isEmpty() && (candidate.contains(query) || query.contains(candidate))) {
                partial.add(activity);
            }
        }
        if (partial.isEmpty()) {
            return new NotFound();
        }
        return partial.size() == 1 ? new Found(partial.get(0)) : ambiguous(partial);
    }

    private static Ambiguous ambiguous(List<CatalogActivity> matches) {
        List<String> names = new ArrayList<>();
        for (CatalogActivity activity : matches) {
            names.add(activity.name());
        }
        names.sort(Comparator.naturalOrder());
        return new Ambiguous(names);
    }

    private static String normalise(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE_RUN.matcher(text.toLowerCase(Locale.ROOT).strip()).replaceAll(" ");
    }
}
