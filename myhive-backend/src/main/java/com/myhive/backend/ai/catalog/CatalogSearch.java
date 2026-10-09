package com.myhive.backend.ai.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Which catalog activities answer what the organizer wrote. The chat model only ever saw the catalog's
 * names, so what it offered for "a boat with strippers" was a guess from the names, filled up with
 * whatever shared a category with its first pick - a beer spa under a river cruise. Here the organizer's
 * own words are read against each activity's name, its one-liner and its categories:
 *
 * <ul>
 *   <li>a wish names one or more <em>kinds</em> ("boat", "strippers", "dinner"); an activity answers a kind
 *       when its name, one-liner or category says so;</li>
 *   <li>activities that answer every kind asked for come first and alone; when none does - there is no boat
 *       with a show - the best of each kind are offered side by side and the caller says so;</li>
 *   <li>within that, a word of the wish in the activity's name counts, then the model's own pick, then the
 *       catalog's order (most popular first).</li>
 * </ul>
 *
 * Nothing is filled up: a short list of what fits beats a full one that does not.
 */
public final class CatalogSearch {

    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int MAX_OFFERED = 6;
    private static final int DEFAULT_OFFERED = 5;
    /** "three ideas", "one more", "2 options": how many were asked for. */
    private static final Map<String, Integer> COUNT_WORDS = Map.ofEntries(Map.entry("one", 1), Map.entry("two", 2),
            Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6),
            Map.entry("1", 1), Map.entry("2", 2), Map.entry("3", 3), Map.entry("4", 4), Map.entry("5", 5),
            Map.entry("6", 6), Map.entry("eine", 1), Map.entry("zwei", 2), Map.entry("drei", 3),
            Map.entry("vier", 4), Map.entry("fünf", 5), Map.entry("sechs", 6));
    private static final int PER_KIND_WHEN_SPLIT = 2;
    private static final int MIN_WORD = 3;
    /** Below this a trigger has to be the whole word ("car" is not "card"); from it on, its beginning. */
    private static final int PREFIX_FROM = 5;

    /** Words of a wish that name nothing. */
    private static final Set<String> STOP_WORDS = Set.of("the", "and", "with", "for", "some", "something", "any",
            "want", "would", "like", "have", "has", "what", "you", "your", "our", "can", "could", "ideas", "idea",
            "please", "more", "another", "one", "add", "put", "there", "are", "that", "this", "show", "shows",
            "activity", "activities", "experience", "tour", "options", "option", "got", "get", "need", "how",
            "about", "into", "its", "night", "day", "und", "mit", "für", "der", "die", "das", "ein", "eine",
            "einen", "was", "gibt", "habt", "ihr", "wir", "bitte", "noch", "etwas");

    /** A message that orders an add rather than asks what there is. */
    private static final Set<String> ADD_WORDS = Set.of("add", "put", "include", "book", "take", "throw", "plus",
            "adding", "hinzu", "hinzufügen", "füg", "fueg", "nimm", "nehmen", "pack", "buch", "buche", "rein", "dazu");

    /**
     * A kind of activity: the words that ask for it, and what shows that an activity is one - words of its
     * name or one-liner, or a category it is filed under.
     */
    private record Kind(String key, List<String> asks, List<String> shows, List<String> categories) {
    }

    private static final List<Kind> KINDS = List.of(
            new Kind("water", List.of("boat", "boats", "river", "cruise", "water", "ship", "vltava", "sail", "sailing",
                    "rafting", "raft", "jet ski", "boot", "schiff", "fluss", "wasser"),
                    List.of("boat", "river", "cruise", "vltava", "rafting", "jet ski", "catamaran", "swimming"),
                    List.of()),
            new Kind("strip", List.of("strip", "stripper", "strippers", "striptease", "private show", "lap dance",
                    "girls", "babes", "topless", "nude", "naked", "gentlemen", "stripperin", "stripperinnen",
                    "mädels"),
                    List.of("private show", "cabaret", "gentlemen", "strip", "jelly wrestling", "nyotaimori",
                            "hotel show"),
                    List.of()),
            new Kind("show", List.of("show", "shows", "cabaret", "performance"),
                    List.of("show", "cabaret", "live music"), List.of()),
            new Kind("dinner", List.of("dinner", "food", "eat", "eating", "steak", "burger", "restaurant", "lunch",
                    "meal", "ribs", "brunch", "breakfast", "essen", "abendessen"),
                    List.of("dinner", "steak", "burger", "ribs", "suckling pig", "lunch", "brunch", "breakfast",
                            "sushi", "bbq"),
                    List.of()),
            new Kind("beer", List.of("beer", "beers", "pub", "pubs", "bier", "brewery"),
                    List.of("beer", "pub", "beering"), List.of("czech-beer")),
            new Kind("shooting", List.of("shoot", "shooting", "gun", "guns", "kalashnikov", "rifle", "pistol",
                    "paintball", "schießen", "schiessen"),
                    List.of(), List.of("guns-and-bullets")),
            new Kind("driving", List.of("kart", "karting", "cart", "carting", "quad", "quads", "drive", "driving",
                    "car", "cars", "tank", "lamborghini", "racing", "race", "limo", "fahren"),
                    List.of("kart", "quad", "tank", "lamborghini", "car football", "car demolition", "pitbike",
                            "limo"),
                    List.of()),
            new Kind("nightlife", List.of("club", "clubs", "clubbing", "nightlife", "night out", "party", "bar crawl",
                    "pub crawl", "dance", "dancing", "feiern"),
                    List.of(), List.of("nightlife")),
            new Kind("chill", List.of("spa", "relax", "relaxing", "wellness", "chill", "sauna", "massage"),
                    List.of("spa", "wellness"), List.of()),
            new Kind("adrenaline", List.of("adrenaline", "extreme", "action", "adventure", "adrenalin"),
                    List.of(), List.of("extreme", "adventure")),
            new Kind("tasting", List.of("tasting", "whiskey", "whisky", "wine", "gin", "slivovice", "cocktail",
                    "cocktails", "drinks"),
                    List.of("tasting", "cocktail"), List.of()));

    private CatalogSearch() {
    }

    /**
     * What to offer, best first, and whether the wish had to be split: it named several kinds and no single
     * activity is all of them, so the best of each kind stand side by side.
     */
    public record Result(List<String> names, boolean split) {
    }

    /** Words that ask what there is, as opposed to asking about an activity or ordering a change. */
    private static final Set<String> ASKING_WORDS = Set.of("what", "which", "any", "ideas", "idea", "options",
            "something", "suggest", "recommend", "got", "welche", "welches", "ideen", "etwas", "vorschläge",
            "empfiehl");

    /**
     * True when the message asks what there is of a kind ("what boats do you have?", "any ideas for beer?"):
     * it names a kind and asks for options. "Is the beer spa far?" and "swap the beer bike" do not.
     */
    public static boolean asksForAKind(String asked) {
        return words(asked).stream().anyMatch(ASKING_WORDS::contains) && !wantedKinds(normalise(asked)).isEmpty();
    }

    /** True when the message orders an add ("add the boat", "put karting in") rather than asks what there is. */
    public static boolean asksToAdd(String asked) {
        return words(asked).stream().anyMatch(ADD_WORDS::contains);
    }

    /**
     * @param asked      the organizer's message
     * @param modelPicks catalog names the chat model put forward for it, in its order
     * @param inPlan     activities the plan already holds: never offered again
     */
    public static Result find(String asked, List<String> modelPicks, List<CatalogActivity> catalog, Set<UUID> inPlan) {
        String query = normalise(asked);
        List<Kind> wanted = wantedKinds(query);
        List<String> tellingWords = words(asked).stream()
                .filter(word -> word.length() >= MIN_WORD && !STOP_WORDS.contains(word) && !ADD_WORDS.contains(word))
                .toList();
        Set<String> picked = new LinkedHashSet<>();
        modelPicks.forEach(name -> picked.add(normalise(name)));

        List<Scored> scored = new ArrayList<>();
        for (int index = 0; index < catalog.size(); index++) {
            CatalogActivity activity = catalog.get(index);
            if (activity.name() == null || inPlan.contains(activity.id())) {
                continue;
            }
            List<String> kinds = wanted.stream().filter(kind -> is(activity, kind)).map(Kind::key).toList();
            int nameHits = nameHits(tellingWords, activity);
            // With a kind asked for, a word in common is not enough: a water gun battle is not "on the water".
            if (!kinds.isEmpty() || (wanted.isEmpty() && nameHits > 0)) {
                scored.add(new Scored(activity, kinds, nameHits, picked.contains(normalise(activity.name())), index));
            }
        }
        if (scored.isEmpty()) {
            // Nothing in the words to go by ("something for the groom"): the model's picks stand as they are.
            List<String> names = new ArrayList<>();
            for (CatalogActivity activity : catalog) {
                if (activity.name() != null && picked.contains(normalise(activity.name()))
                        && !inPlan.contains(activity.id())) {
                    names.add(activity.name());
                }
            }
            return new Result(cap(names, MAX_OFFERED), false);
        }
        scored.sort(Comparator.comparingInt((Scored s) -> -s.kinds().size())
                .thenComparingInt(s -> -s.nameHits())
                .thenComparing(s -> !s.picked())
                .thenComparingInt(Scored::index));
        int limit = words(asked).stream().map(COUNT_WORDS::get).filter(java.util.Objects::nonNull).findFirst()
                .orElse(DEFAULT_OFFERED);
        if (wanted.size() < 2) {
            return new Result(cap(scored.stream().map(s -> s.activity().name()).toList(), limit), false);
        }
        List<String> everyKind = scored.stream().filter(s -> s.kinds().size() == wanted.size())
                .map(s -> s.activity().name()).toList();
        if (!everyKind.isEmpty()) {
            return new Result(cap(everyKind, limit), false);
        }
        // No activity is all of it: the best of each kind, in the order the kinds were asked for.
        Map<String, List<String>> byKind = new LinkedHashMap<>();
        wanted.forEach(kind -> byKind.put(kind.key(), new ArrayList<>()));
        for (Scored s : scored) {
            for (String kind : s.kinds()) {
                List<String> names = byKind.get(kind);
                if (names.size() < PER_KIND_WHEN_SPLIT) {
                    names.add(s.activity().name());
                }
            }
        }
        Set<String> sideBySide = new LinkedHashSet<>();
        byKind.values().forEach(sideBySide::addAll);
        boolean eachKindAnswered = byKind.values().stream().noneMatch(List::isEmpty);
        return new Result(cap(List.copyOf(sideBySide), MAX_OFFERED), eachKindAnswered);
    }

    private record Scored(CatalogActivity activity, List<String> kinds, int nameHits, boolean picked, int index) {
    }

    /** The kinds the wish names, in the order it names them; an adult show is not also "a show". */
    private static List<Kind> wantedKinds(String query) {
        List<String> queryWords = List.of(query.split(" "));
        Map<Integer, Kind> byPosition = new java.util.TreeMap<>();
        for (Kind kind : KINDS) {
            int at = firstMention(query, queryWords, kind.asks());
            if (at >= 0) {
                byPosition.putIfAbsent(at * KINDS.size() + KINDS.indexOf(kind), kind);
            }
        }
        List<Kind> wanted = new ArrayList<>(byPosition.values());
        if (wanted.stream().anyMatch(kind -> kind.key().equals("strip"))) {
            wanted.removeIf(kind -> kind.key().equals("show"));
        }
        return wanted;
    }

    private static int firstMention(String query, List<String> queryWords, List<String> asks) {
        int first = -1;
        for (String ask : asks) {
            int at = ask.contains(" ") ? (" " + query + " ").indexOf(" " + ask) : mentionedAt(query, queryWords, ask);
            if (at >= 0 && (first < 0 || at < first)) {
                first = at;
            }
        }
        return first;
    }

    private static int mentionedAt(String query, List<String> queryWords, String ask) {
        for (String word : queryWords) {
            boolean same = ask.length() >= PREFIX_FROM ? word.startsWith(ask) : word.equals(ask) || word.equals(ask + "s");
            if (same) {
                return (" " + query + " ").indexOf(" " + word);
            }
        }
        return -1;
    }

    private static boolean is(CatalogActivity activity, Kind kind) {
        if (activity.categorySlugs() != null && activity.categorySlugs().stream().anyMatch(kind.categories()::contains)) {
            return true;
        }
        String text = " " + normalise(activity.name()) + " " + normalise(activity.oneLine()) + " ";
        return kind.shows().stream().anyMatch(shown -> text.contains(" " + shown));
    }

    /** How many telling words of the wish are words of the activity's name ("tiki" in "Tiki Boat"). */
    private static int nameHits(List<String> tellingWords, CatalogActivity activity) {
        List<String> nameWords = List.of(normalise(activity.name()).split(" "));
        int hits = 0;
        for (String word : tellingWords) {
            if (nameWords.stream().anyMatch(nameWord -> nameWord.equals(word)
                    || (word.length() >= PREFIX_FROM && nameWord.startsWith(word))
                    || (nameWord.length() >= PREFIX_FROM && word.startsWith(nameWord)))) {
                hits++;
            }
        }
        return hits;
    }

    private static List<String> cap(List<String> names, int limit) {
        return List.copyOf(names.subList(0, Math.min(limit, names.size())));
    }

    private static List<String> words(String text) {
        String normalised = normalise(text);
        return normalised.isEmpty() ? List.of() : List.of(normalised.split(" "));
    }

    private static String normalise(String text) {
        if (text == null) {
            return "";
        }
        return SEPARATORS.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }
}
