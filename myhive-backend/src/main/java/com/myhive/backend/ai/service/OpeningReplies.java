package com.myhive.backend.ai.service;

import com.myhive.backend.util.Translations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The tap-to-send chips on a fresh chat, before the model has said anything. Each one is a bundle that
 * ready-made stag packages repeat most often (see docs/concepts/2026-09-29-ai-planner-package-concept.md:
 * night out 29/33 packages, shooting 13/33, dinner with a show 11/33, prank 10/33, karting 7/33), in that
 * order, and each is offered only when the destination has the categories to deliver it - a chip must never
 * promise something the catalog cannot plan.
 */
final class OpeningReplies {

    private record Hook(Predicate<Set<String>> available, Map<String, String> text) {
    }

    private static final List<Hook> HOOKS = List.of(
            new Hook(c -> c.contains("nightlife"),
                    Map.of("en", "Bar crawl + club night", "de", "Kneipentour + Club")),
            new Hook(c -> c.contains("shooting"),
                    Map.of("en", "Shooting range + night out", "de", "Schießstand + Nachtleben")),
            new Hook(c -> c.contains("dining") && (c.contains("show") || c.contains("adult")),
                    Map.of("en", "Steak dinner with a show", "de", "Steak-Dinner mit Show")),
            new Hook(c -> c.contains("prank"),
                    Map.of("en", "A prank on the groom", "de", "Ein Streich für den Bräutigam")),
            new Hook(c -> c.contains("driving"),
                    Map.of("en", "Karting by day, club by night", "de", "Kart tagsüber, Club nachts")));

    static final int MAX = 4;

    private OpeningReplies() {
    }

    /** {@code lc} is the session's resolved locale ("en", "de", ...); an unknown one falls back to English. */
    static List<String> forCategories(List<String> categorySlugs, String lc) {
        Set<String> categories = Set.copyOf(categorySlugs);
        List<String> replies = new ArrayList<>();
        for (Hook hook : HOOKS) {
            if (replies.size() == MAX) {
                break;
            }
            if (hook.available().test(categories)) {
                replies.add(hook.text().getOrDefault(lc, hook.text().get(Translations.DEFAULT_LOCALE)));
            }
        }
        return replies;
    }
}
