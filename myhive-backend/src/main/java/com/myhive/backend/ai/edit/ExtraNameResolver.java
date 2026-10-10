package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.catalog.CatalogExtra;

import java.util.List;
import java.util.Optional;

/**
 * Maps the extra the chat model named onto one of the extras an activity offers. The same two steps as
 * {@link ActivityNameResolver}: an exact name (case and spacing aside) first, then a name that contains, or
 * is contained in, exactly one offered extra. Several matches are no match: an extra is billed, so it is
 * never guessed.
 */
public final class ExtraNameResolver {

    /** Below this a partial match is noise ("a" is in most names); only an exact name counts. */
    private static final int MIN_FUZZY_LENGTH = 3;

    private ExtraNameResolver() {
    }

    public static Optional<CatalogExtra> resolve(String name, List<CatalogExtra> offered) {
        String query = ActivityNameResolver.normalise(name);
        if (query.isEmpty() || offered == null || offered.isEmpty()) {
            return Optional.empty();
        }
        List<CatalogExtra> exact = offered.stream()
                .filter(extra -> ActivityNameResolver.normalise(extra.name()).equals(query))
                .toList();
        if (exact.size() == 1) {
            return Optional.of(exact.get(0));
        }
        if (!exact.isEmpty() || query.length() < MIN_FUZZY_LENGTH) {
            return Optional.empty();
        }
        List<CatalogExtra> partial = offered.stream()
                .filter(extra -> {
                    String candidate = ActivityNameResolver.normalise(extra.name());
                    return !candidate.isEmpty() && (candidate.contains(query) || query.contains(candidate));
                })
                .toList();
        return partial.size() == 1 ? Optional.of(partial.get(0)) : Optional.empty();
    }
}
