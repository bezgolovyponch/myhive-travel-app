package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogPreset;
import com.myhive.backend.ai.model.Tier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a trip draft lacks next to the ready-made packages of its tier: the themes (catalog categories) those
 * packages are built around that none of the draft's activities belongs to - a draft of shooting and a
 * dinner, set against presets that also carry a night out, lacks nightlife. The chat offers each as a
 * "what next" tag. Pure Java, no model call; the most common theme across the tier's presets comes first.
 */
public final class DraftGaps {

    /** Three tags read as a nudge; more would crowd the chat bar. */
    public static final int MAX_GAPS = 3;

    private DraftGaps() {
    }

    /** Category slugs per package, most wanted first; a package with no presets of its tier has none. */
    public static Map<Tier, List<String>> of(ComposedPlan plan, List<CatalogActivity> catalog,
                                             List<CatalogPreset> presets) {
        Map<Tier, List<String>> gaps = new EnumMap<>(Tier.class);
        if (plan == null || presets == null || presets.isEmpty()) {
            return gaps;
        }
        Map<UUID, List<String>> categoriesById = new HashMap<>();
        for (CatalogActivity activity : catalog == null ? List.<CatalogActivity>of() : catalog) {
            categoriesById.put(activity.id(), activity.categorySlugs() == null ? List.of() : activity.categorySlugs());
        }
        for (ComposedPlan.PackageResult pkg : plan.packages()) {
            Set<String> covered = new HashSet<>();
            for (ComposedPlan.DayResult day : pkg.days()) {
                for (ComposedPlan.ItemResult item : day.items()) {
                    covered.addAll(categoriesById.getOrDefault(item.activityId(), List.of()));
                }
            }
            Map<String, Integer> wanted = new LinkedHashMap<>();
            for (CatalogPreset preset : presets) {
                if (preset.tier() != pkg.key()) {
                    continue;
                }
                for (String slug : themesOf(preset, categoriesById)) {
                    if (!covered.contains(slug)) {
                        wanted.merge(slug, 1, Integer::sum);
                    }
                }
            }
            List<String> ordered = new ArrayList<>(wanted.keySet());
            // Stable: equally common themes keep the order the presets list them in.
            ordered.sort((a, b) -> wanted.get(b) - wanted.get(a));
            gaps.put(pkg.key(), List.copyOf(ordered.subList(0, Math.min(MAX_GAPS, ordered.size()))));
        }
        return gaps;
    }

    /** A preset's own themes; one without any is read through its activities' categories. */
    private static List<String> themesOf(CatalogPreset preset, Map<UUID, List<String>> categoriesById) {
        if (preset.categorySlugs() != null && !preset.categorySlugs().isEmpty()) {
            return preset.categorySlugs();
        }
        Set<String> slugs = new java.util.LinkedHashSet<>();
        for (UUID id : preset.activityIds()) {
            slugs.addAll(categoriesById.getOrDefault(id, List.of()));
        }
        return List.copyOf(slugs);
    }
}
