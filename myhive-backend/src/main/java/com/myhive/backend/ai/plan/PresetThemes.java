package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogPreset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The other ready-made weekends on offer while the three options are on screen. Ready-made packages come
 * in families, one per price level, named "Family: Level" ("Adrenaline: Starter", "Adrenaline: All Out");
 * the family the options were built from is the one they share the most activities with, and every other
 * family is a theme the chat offers as a tag ("Adrenaline", "Beer & Food"). Pure Java, no model call.
 *
 * <p>Themes come most popular first: the catalog is already ordered by the popularity index for this
 * brief, so a family whose activities sit higher in it is offered earlier.
 */
public final class PresetThemes {

    /** Two rows of tags on a phone hold about this many. */
    public static final int MAX_THEMES = 4;

    private static final char FAMILY_SEPARATOR = ':';

    private PresetThemes() {
    }

    /** Family names not behind the plan, most popular first; empty without a plan or with one family only. */
    public static List<String> of(ComposedPlan plan, List<CatalogActivity> catalog, List<CatalogPreset> presets) {
        if (plan == null || presets == null || presets.isEmpty()) {
            return List.of();
        }
        Map<String, Set<UUID>> families = new LinkedHashMap<>();
        for (CatalogPreset preset : presets) {
            families.computeIfAbsent(family(preset.name()), name -> new HashSet<>()).addAll(preset.activityIds());
        }
        Set<UUID> planned = new HashSet<>();
        plan.packages().forEach(pkg -> planned.addAll(pkg.activityIds()));
        String shown = null;
        int most = 0;
        for (Map.Entry<String, Set<UUID>> entry : families.entrySet()) {
            int shared = (int) entry.getValue().stream().filter(planned::contains).count();
            if (shared > most) {
                most = shared;
                shown = entry.getKey();
            }
        }
        Map<UUID, Integer> position = new HashMap<>();
        List<CatalogActivity> ordered = catalog == null ? List.of() : catalog;
        for (int i = 0; i < ordered.size(); i++) {
            position.putIfAbsent(ordered.get(i).id(), i);
        }
        List<String> themes = new ArrayList<>(families.keySet());
        themes.remove(shown);
        Map<String, Double> rank = new HashMap<>();
        for (String theme : themes) {
            rank.put(theme, families.get(theme).stream()
                    .mapToInt(id -> position.getOrDefault(id, ordered.size())).average().orElse(ordered.size()));
        }
        // Stable: equally ranked families keep the order the presets list them in.
        themes.sort((a, b) -> Double.compare(rank.get(a), rank.get(b)));
        return List.copyOf(themes.subList(0, Math.min(MAX_THEMES, themes.size())));
    }

    /**
     * What the chat is told about a theme: the catalog categories its ready-made packages are built
     * around, in the order they first appear. Empty for a name that is not a family.
     */
    public static List<String> categories(String theme, List<CatalogPreset> presets) {
        Set<String> slugs = new java.util.LinkedHashSet<>();
        for (CatalogPreset preset : presets == null ? List.<CatalogPreset>of() : presets) {
            if (family(preset.name()).equals(theme) && preset.categorySlugs() != null) {
                slugs.addAll(preset.categorySlugs());
            }
        }
        return List.copyOf(slugs);
    }

    /** "Adrenaline: Starter" is the family "Adrenaline"; a name without a level is its own family. */
    static String family(String presetName) {
        if (presetName == null) {
            return "";
        }
        int cut = presetName.indexOf(FAMILY_SEPARATOR);
        return (cut < 0 ? presetName : presetName.substring(0, cut)).strip();
    }
}
