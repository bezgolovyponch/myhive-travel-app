package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.edit.ActivityNameResolver;
import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.edit.EditOutcome;
import com.myhive.backend.ai.edit.EditRequest;
import com.myhive.backend.ai.edit.PackageEditor;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.model.Wish;
import com.myhive.backend.util.FromPrice;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Holds freshly built packages to the organizer's budget in euros. The planner is told the limit, and a
 * model that is told a number still misses it now and then; the deterministic fallback does not read it at
 * all. Here every package whose "from" price per person is over the limit is brought under it with the same
 * edits a chat turn uses, one at a time, so the schedule rules and the prices stay the editor's and the
 * assembler's: a chosen extra is dropped first, then an activity is swapped for a cheaper one of the same kind,
 * and only then is one taken out. What was asked for by name is never touched, and a package keeps at least
 * one activity. A package that cannot get under the limit that way is left as close to it as it got.
 *
 * <p>Of the edits that bring a package under the limit, the one that leaves it dearest is made - the least
 * is given up. When none does on its own, the gentlest kind that is left is made (an extra before a swap,
 * a swap before a removal) and the next round goes on. The tiers are fitted from the bottom up, an edit that
 * keeps a package dearer than the one below is preferred, and a second pass from the top down then lowers a
 * tier that is still as dear as the one above it, so the three read as three sizes of the trip.
 */
@Slf4j
public final class BudgetFitter {

    /** Every round makes one edit; a package has at most a dozen activities and their extras. */
    private static final int MAX_ROUNDS = 16;

    /** Swaps tried per activity: the dearest few that are still cheaper than it. */
    private static final int SWAPS_PER_ACTIVITY = 3;

    private BudgetFitter() {
    }

    /** The plan with every package within {@code brief}'s budget where it can be; the plan itself without one. */
    public static ComposedPlan fit(PackageEditor editor, ComposedPlan plan, Brief brief, List<CatalogActivity> catalog) {
        BigDecimal limit = brief == null ? null : brief.budgetPerPerson();
        if (editor == null || plan == null || limit == null || catalog == null || catalog.isEmpty()
                || brief.groupSize() == null) {
            return plan;
        }
        Set<UUID> wished = wishedIds(brief, catalog);
        List<Tier> bottomUp = plan.packages().stream().map(ComposedPlan.PackageResult::key).sorted().toList();
        int[] edits = {0};
        ComposedPlan working = plan;
        // Every package under the budget, from the bottom up...
        for (Tier tier : bottomUp) {
            working = lower(editor, working, tier, limit, brief, catalog, wished, edits);
        }
        // ...then the order back: a tier that ended up dearer than the one above it gives up what it must.
        for (int i = bottomUp.size() - 1; i > 0; i--) {
            ComposedPlan.PackageResult above = packageOf(working, bottomUp.get(i)).orElse(null);
            if (above != null) {
                BigDecimal under = fromPerPerson(above, brief).subtract(BigDecimal.ONE);
                working = lower(editor, working, bottomUp.get(i - 1), under, brief, catalog, wished, edits);
            }
        }
        if (edits[0] > 0) {
            log.info("planner budget fitted edits={} limit={} fromPerPerson={}", edits[0], limit,
                    working.packages().stream().map(pkg -> pkg.key() + ":" + fromPerPerson(pkg, brief)).toList());
        }
        return working;
    }

    /** The package of {@code tier} brought to {@code target} or under, one edit a round, as far as it goes. */
    private static ComposedPlan lower(PackageEditor editor, ComposedPlan plan, Tier tier, BigDecimal target, Brief brief,
            List<CatalogActivity> catalog, Set<UUID> wished, int[] edits) {
        ComposedPlan working = plan;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            ComposedPlan.PackageResult pkg = packageOf(working, tier).orElse(null);
            if (pkg == null || fromPerPerson(pkg, brief).compareTo(target) <= 0) {
                break;
            }
            Optional<ComposedPlan> next = bestStep(editor, working, pkg, brief, catalog, wished, target);
            if (next.isEmpty()) {
                log.info("planner budget not met tier={} fromPerPerson={} target={}", tier,
                        fromPerPerson(pkg, brief), target);
                break;
            }
            working = next.get();
            edits[0]++;
        }
        return working;
    }

    /** One traveller's "from" price for the package: what the plan shows and the budget is held to. */
    public static BigDecimal fromPerPerson(ComposedPlan.PackageResult pkg, Brief brief) {
        BigDecimal from = FromPrice.perPerson(pkg.totalPrice(), pkg.extrasTotal(), brief.groupSize());
        return from == null ? BigDecimal.ZERO : from;
    }

    /** Whether the package's "from" price is over the brief's budget; false without a budget. */
    public static boolean overBudget(ComposedPlan.PackageResult pkg, Brief brief) {
        BigDecimal limit = brief == null ? null : brief.budgetPerPerson();
        return limit != null && brief.groupSize() != null && fromPerPerson(pkg, brief).compareTo(limit) > 0;
    }

    private record Step(int kind, ComposedPlan plan, BigDecimal price, boolean fits, boolean keepsOrder) {
    }

    private static Optional<ComposedPlan> bestStep(PackageEditor editor, ComposedPlan plan,
            ComposedPlan.PackageResult pkg, Brief brief, List<CatalogActivity> catalog, Set<UUID> wished,
            BigDecimal limit) {
        BigDecimal floor = packageBelow(plan, pkg.key()).map(below -> fromPerPerson(below, brief)).orElse(null);
        List<Step> steps = new ArrayList<>();
        List<List<EditRequest>> byKind = candidates(pkg, catalog, wished);
        for (int kind = 0; kind < byKind.size(); kind++) {
            for (EditRequest edit : byKind.get(kind)) {
                EditOutcome outcome = editor.apply(plan, brief, catalog, List.of(edit));
                if (!outcome.anyApplied()) {
                    continue;
                }
                ComposedPlan.PackageResult after = packageOf(outcome.plan(), pkg.key()).orElse(null);
                if (after == null || after.activityIds().isEmpty()) {
                    continue;
                }
                BigDecimal price = fromPerPerson(after, brief);
                if (price.compareTo(fromPerPerson(pkg, brief)) >= 0) {
                    continue;
                }
                steps.add(new Step(kind, outcome.plan(), price, price.compareTo(limit) <= 0,
                        floor == null || price.compareTo(floor) > 0));
            }
        }
        Comparator<Step> order = Comparator.comparing((Step step) -> !step.keepsOrder());
        Optional<Step> fitting = steps.stream().filter(Step::fits)
                .min(order.thenComparingInt(Step::kind).thenComparing(Step::price, Comparator.reverseOrder()));
        if (fitting.isPresent()) {
            return Optional.of(fitting.get().plan());
        }
        // Nothing gets there in one: the gentlest kind of edit goes first (an extra, then a swap, then a removal),
        // the one of that kind that saves most - so a package loses its dearest activity only when nothing else is left.
        return steps.stream().min(order.thenComparingInt(Step::kind).thenComparing(Step::price)).map(Step::plan);
    }

    /** Extras to drop, activities to swap for cheaper ones, activities to take out - each kind in its own list. */
    private static List<List<EditRequest>> candidates(ComposedPlan.PackageResult pkg, List<CatalogActivity> catalog,
            Set<UUID> wished) {
        List<EditRequest> dropExtras = new ArrayList<>();
        List<EditRequest> swaps = new ArrayList<>();
        List<EditRequest> removals = new ArrayList<>();
        Set<UUID> inPackage = new HashSet<>(pkg.activityIds());
        for (ComposedPlan.DayResult day : pkg.days()) {
            for (ComposedPlan.ItemResult item : day.items()) {
                for (ComposedPlan.ItemExtra extra : item.extras()) {
                    dropExtras.add(new EditRequest(EditOp.REMOVE_EXTRA, item.name(), null, pkg.key(), null, null,
                            List.of(), item.activityId(), extra.name()));
                }
                if (wished.contains(item.activityId())) {
                    continue;
                }
                CatalogActivity current = catalog.stream().filter(a -> a.id().equals(item.activityId()))
                        .findFirst().orElse(null);
                if (current != null) {
                    catalog.stream()
                            .filter(a -> !inPackage.contains(a.id()))
                            .filter(a -> a.price().compareTo(current.price()) < 0)
                            .filter(a -> a.categorySlugs().stream().anyMatch(current.categorySlugs()::contains))
                            .sorted(Comparator.comparing(CatalogActivity::price).reversed())
                            .limit(SWAPS_PER_ACTIVITY)
                            .forEach(cheaper -> swaps.add(new EditRequest(EditOp.REPLACE, item.name(), cheaper.name(),
                                    pkg.key(), day.dayNumber(), null, List.of(), item.activityId())));
                }
                removals.add(new EditRequest(EditOp.REMOVE, item.name(), null, pkg.key(), day.dayNumber(), null,
                        List.of(), item.activityId()));
            }
        }
        return List.of(dropExtras, swaps, removals);
    }

    private static Set<UUID> wishedIds(Brief brief, List<CatalogActivity> catalog) {
        Set<UUID> ids = new HashSet<>();
        for (Wish wish : brief.wishes()) {
            for (String name : wish.activities()) {
                if (ActivityNameResolver.resolve(name, catalog) instanceof ActivityNameResolver.Found found) {
                    ids.add(found.activity().id());
                }
            }
        }
        return ids;
    }

    private static Optional<ComposedPlan.PackageResult> packageOf(ComposedPlan plan, Tier tier) {
        return plan.packages().stream().filter(pkg -> pkg.key() == tier).findFirst();
    }

    private static Optional<ComposedPlan.PackageResult> packageBelow(ComposedPlan plan, Tier tier) {
        return plan.packages().stream()
                .filter(pkg -> pkg.key().compareTo(tier) < 0)
                .max(Comparator.comparing(ComposedPlan.PackageResult::key));
    }
}
