package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogPreset;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanValidator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a trip draft could take next, as activities the organizer adds with one tap: what the ready-made
 * packages hold that the draft does not, and only what would actually go in - each one is tried against the
 * draft with the very editor a tap uses, so a suggestion is never answered with "no free slot".
 *
 * <p>The ready-made packages of the draft's own tier come first, then the tier above, then the rest: a
 * Basic draft is offered what the other Basic packages are made of before a tank ride. Within a package
 * its own order holds, most important first. Pure Java, no model call.
 */
public final class DraftSuggestions {

    /** A handful reads as an offer; more would crowd the chat bar. */
    public static final int MAX_SUGGESTIONS = 4;

    /** Every candidate is a dry run of the editor; a destination with many presets is cut off here. */
    private static final int MAX_TRIED = 16;

    /** Both are stateless; the editor is the one a tap in the draft goes through. */
    private static final PackageEditor EDITOR = new PackageEditor(new PlanValidator(), new PlanAssembler());

    private DraftSuggestions() {
    }

    /** Activities per package, best first; a package with nothing that fits has an empty list. */
    public static Map<Tier, List<CatalogActivity>> of(ComposedPlan plan, Brief brief, List<CatalogActivity> catalog,
                                                      List<CatalogPreset> presets) {
        Map<Tier, List<CatalogActivity>> suggestions = new EnumMap<>(Tier.class);
        if (plan == null || brief == null || brief.groupSize() == null || presets == null || presets.isEmpty()) {
            return suggestions;
        }
        Map<UUID, CatalogActivity> byId = new HashMap<>();
        for (CatalogActivity activity : catalog == null ? List.<CatalogActivity>of() : catalog) {
            byId.putIfAbsent(activity.id(), activity);
        }
        for (ComposedPlan.PackageResult pkg : plan.packages()) {
            suggestions.put(pkg.key(), forPackage(plan, pkg, brief, catalog, presets, byId));
        }
        return suggestions;
    }

    private static List<CatalogActivity> forPackage(ComposedPlan plan, ComposedPlan.PackageResult pkg, Brief brief,
            List<CatalogActivity> catalog, List<CatalogPreset> presets, Map<UUID, CatalogActivity> byId) {
        Set<UUID> inDraft = new HashSet<>(pkg.activityIds());
        Set<UUID> candidates = new LinkedHashSet<>();
        presets.stream()
                .sorted(Comparator.comparingInt((CatalogPreset preset) -> distance(preset.tier(), pkg.key())))
                .forEach(preset -> candidates.addAll(preset.activityIds()));
        List<CatalogActivity> fitting = new ArrayList<>();
        int tried = 0;
        for (UUID id : candidates) {
            CatalogActivity activity = byId.get(id);
            if (activity == null || inDraft.contains(id)) {
                continue;
            }
            if (fitting.size() == MAX_SUGGESTIONS || tried++ == MAX_TRIED) {
                break;
            }
            EditRequest add = new EditRequest(EditOp.ADD, activity.name(), null, pkg.key(), null, null);
            if (EDITOR.apply(plan, brief, catalog, List.of(add)).anyApplied()) {
                fitting.add(activity);
            }
        }
        return List.copyOf(fitting);
    }

    /** Own tier 0, the tier above 1, two above 2; the tiers below come after all of those. */
    private static int distance(Tier preset, Tier draft) {
        int steps = preset.ordinal() - draft.ordinal();
        return steps >= 0 ? steps : Tier.values().length - steps;
    }
}
