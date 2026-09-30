package com.myhive.backend.ai.catalog;

import com.myhive.backend.util.Translations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The tap-to-send chips on a fresh chat, before the model has said anything. Each one is a bundle that
 * ready-made stag packages repeat most often (see docs/concepts/2026-09-29-ai-planner-package-concept.md:
 * night out 29/33 packages, shooting 13/33, dinner with a show 11/33, prank 10/33, karting 7/33), in that
 * order.
 *
 * <p>A chip must never promise something the catalog cannot plan, so each is offered only when the
 * snapshot holds an activity for it. The test is on the catalog itself - the words of an activity's name
 * and of its category slugs - not on the categories assigned to the destination: those are an admin
 * nicety that is empty for Prague in production, and category slugs differ between environments
 * ({@code shooting} in the seed data, {@code guns-and-bullets} in production).
 */
public final class OpeningReplies {

    public static final int MAX = 4;

    private static final Pattern NOT_A_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** One chip: the words that have to turn up in the catalog for it to be deliverable, and what it says. */
    private record Hook(Set<String> words, Map<String, String> text) {
    }

    private static final List<Hook> HOOKS = List.of(
            new Hook(Set.of("nightlife", "crawl", "club", "clubs", "nightclub", "kneipentour"),
                    Map.of("en", "Bar crawl + club night", "de", "Kneipentour + Club")),
            new Hook(Set.of("shooting", "shoot", "gun", "guns", "schießen", "schiessen", "schießstand"),
                    Map.of("en", "Shooting range + night out", "de", "Schießstand + Nachtleben")),
            new Hook(Set.of("show", "shows", "cabaret", "striptease"),
                    Map.of("en", "Steak dinner with a show", "de", "Steak-Dinner mit Show")),
            new Hook(Set.of("prank", "pranks", "streich"),
                    Map.of("en", "A prank on the groom", "de", "Ein Streich für den Bräutigam")),
            new Hook(Set.of("karting", "kart", "karts", "gokart", "kartfahren"),
                    Map.of("en", "Karting by day, club by night", "de", "Kart tagsüber, Club nachts")));

    private OpeningReplies() {
    }

    /** {@code lc} is the session's resolved locale ("en", "de", ...); an unknown one falls back to English. */
    public static List<String> forCatalog(List<CatalogActivity> catalog, String lc) {
        Set<String> words = wordsOf(catalog);
        List<String> replies = new ArrayList<>();
        for (Hook hook : HOOKS) {
            if (replies.size() == MAX) {
                break;
            }
            if (hook.words().stream().anyMatch(words::contains)) {
                replies.add(hook.text().getOrDefault(lc, hook.text().get(Translations.DEFAULT_LOCALE)));
            }
        }
        return replies;
    }

    /**
     * Whole words only: "Burgundy" must not read as a gun, nor "shower" as a show. Names and category
     * slugs are both split on anything that is not a letter or a digit, which takes a slug's hyphens too.
     */
    private static Set<String> wordsOf(List<CatalogActivity> catalog) {
        Set<String> words = new HashSet<>();
        for (CatalogActivity activity : catalog) {
            addWords(words, activity.name());
            for (String slug : activity.categorySlugs()) {
                addWords(words, slug);
            }
        }
        return words;
    }

    private static void addWords(Set<String> words, String text) {
        if (text == null) {
            return;
        }
        for (String word : NOT_A_WORD.split(text.toLowerCase(Locale.ROOT))) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
    }
}
