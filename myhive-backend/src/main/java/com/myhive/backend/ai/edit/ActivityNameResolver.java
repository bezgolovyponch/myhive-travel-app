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
    /** From this length on a word may be two letters off and still be the same word. */
    private static final int LONG_WORD = 7;
    /** Words people put around a name that name nothing: they never have to match a catalog row. */
    private static final java.util.Set<String> FILLER_WORDS = java.util.Set.of("the", "and", "with", "for", "some",
            "experience", "tour", "session", "package", "activity", "one", "thing", "please", "trip",
            "der", "die", "das", "und", "mit", "eine", "einen", "bitte");

    /**
     * Anything that is neither a letter nor a digit separates words and nothing more: "ak 47" has to find
     * "AK-47 and Glock 17 Shooting", "steak and private show" the "Steak & Private Show".
     */
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");

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
            return misspelt(query, catalog);
        }
        return partial.size() == 1 ? new Found(partial.get(0)) : ambiguous(partial);
    }

    /**
     * The last try, for a name typed loosely: "river bot experience" is the "River Boat Cruise". Every word
     * of the query that says something has to be a word of the catalog name, give or take a typo, and
     * exactly one row may fit - two that do are not guessed between, and a row that only shares a filler
     * word ("experience", "tour") does not fit at all.
     */
    private static Resolution misspelt(String query, List<CatalogActivity> catalog) {
        List<String> words = telling(query);
        if (words.isEmpty()) {
            return new NotFound();
        }
        List<CatalogActivity> fits = new ArrayList<>();
        for (CatalogActivity activity : catalog) {
            List<String> nameWords = List.of(normalise(activity.name()).split(" "));
            if (words.stream().allMatch(word -> nameWords.stream().anyMatch(other -> sameWord(word, other)))) {
                fits.add(activity);
            }
        }
        if (fits.isEmpty()) {
            return new NotFound();
        }
        return fits.size() == 1 ? new Found(fits.get(0)) : ambiguous(fits);
    }

    /** The words of a query that name something: no fillers, nothing under three letters. */
    private static List<String> telling(String query) {
        List<String> words = new ArrayList<>();
        for (String word : query.split(" ")) {
            if (word.length() >= MIN_FUZZY_LENGTH && !FILLER_WORDS.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    /**
     * Equal, or a typo apart: one edit for a short word ("bot" - "boat", never across the first letter,
     * where "bar" would turn into "car"), two for a long one ("paintbal", "kalashnikow").
     */
    private static boolean sameWord(String typed, String word) {
        if (typed.equals(word)) {
            return true;
        }
        if (typed.charAt(0) != word.charAt(0)) {
            return false;
        }
        int allowed = Math.min(typed.length(), word.length()) >= LONG_WORD ? 2 : 1;
        return Math.abs(typed.length() - word.length()) <= allowed && distance(typed, word) <= allowed;
    }

    /** Levenshtein distance: inserts, deletes and swaps of one letter. */
    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int swap = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(swap, Math.min(previous[j], current[j - 1]) + 1);
            }
            previous = current;
        }
        return previous[b.length()];
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
        return SEPARATORS.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }
}
