package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Tier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a package could take next, by kind: the "+ Add shooting", "+ Add strippers" tags under the plan. A
 * kind is a catalog category; behind each one are the activities of that category the package does not
 * hold yet, which the chat offers when the tag is tapped. Pure Java, no model call.
 *
 * <p>Order is the catalog's own - for this brief, then by the popularity index, which already counts
 * being part of a ready-made package - so the kind with the most wanted activity comes first, and inside
 * a kind the most wanted activity does. An activity filed under several categories leads only the first
 * kind it appears in: two tags never open on the same answer.
 */
public final class DraftGaps {

    /** Two rows of tags on a phone. */
    public static final int MAX_GAPS = 6;

    /** One question's worth of answers: more would not fit over the input. */
    public static final int MAX_OPTIONS = 4;

    private DraftGaps() {
    }

    /** One tag: the category and what it offers, most wanted first. Never empty. */
    public record Kind(String categorySlug, List<CatalogActivity> options) {
    }

    /** Kinds per package, most wanted first; a package that holds the whole catalog has none. */
    public static Map<Tier, List<Kind>> of(ComposedPlan plan, List<CatalogActivity> catalog) {
        Map<Tier, List<Kind>> gaps = new EnumMap<>(Tier.class);
        if (plan == null || catalog == null || catalog.isEmpty()) {
            return gaps;
        }
        for (ComposedPlan.PackageResult pkg : plan.packages()) {
            gaps.put(pkg.key(), forPackage(pkg, catalog));
        }
        return gaps;
    }

    private static List<Kind> forPackage(ComposedPlan.PackageResult pkg, List<CatalogActivity> catalog) {
        Set<UUID> held = new HashSet<>(pkg.activityIds());
        pkg.days().forEach(day -> day.items().forEach(item -> held.add(item.activityId())));
        // Insertion order is the order each category's best activity turns up in the catalog.
        Map<String, List<CatalogActivity>> byCategory = new LinkedHashMap<>();
        Set<UUID> leads = new HashSet<>();
        for (CatalogActivity activity : catalog) {
            if (held.contains(activity.id()) || activity.categorySlugs() == null) {
                continue;
            }
            for (String slug : activity.categorySlugs()) {
                List<CatalogActivity> options = byCategory.get(slug);
                if (options == null) {
                    if (leads.contains(activity.id())) {
                        // Already the first answer of an earlier kind: this one starts with its next activity.
                        continue;
                    }
                    options = new ArrayList<>();
                    byCategory.put(slug, options);
                    leads.add(activity.id());
                }
                if (options.size() < MAX_OPTIONS) {
                    options.add(activity);
                }
            }
        }
        List<Kind> kinds = new ArrayList<>();
        for (Map.Entry<String, List<CatalogActivity>> entry : byCategory.entrySet()) {
            if (kinds.size() == MAX_GAPS) {
                break;
            }
            kinds.add(new Kind(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return List.copyOf(kinds);
    }
}
