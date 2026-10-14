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
import java.util.function.Predicate;

/**
 * Brings freshly built packages to the budget the organizer named in the chat: BASIC is the budget, MEDIUM and
 * PREMIUM cost more as they always do (owner's rule, 2026-10-14). The planner is told it, and a model that is
 * told a number still misses it now and then; the deterministic fallback does not read it at all. Here BASIC's
 * "from" price per person is moved to within {@link #TOLERANCE} of the budget with the same edits a chat turn
 * uses, one at a time, so the schedule rules and the prices stay the editor's and the assembler's:
 * <ul>
 *   <li>too dear: a chosen extra is dropped, an activity is swapped for a cheaper one of the same kind, or one is
 *       taken out;</li>
 *   <li>too cheap: an activity is swapped for a dearer one of the same kind.</li>
 * </ul>
 * Each round makes the edit that lands nearest the budget. What was asked for by name is never touched, and a
 * package keeps at least one activity. Then MEDIUM and PREMIUM are made at least {@link #STEP_UP} times as dear as
 * the tier below where they are not (dearer swaps), and as a last resort a tier below is made cheaper than the
 * one above it.
 */
@Slf4j
public final class BudgetFitter {

    /** "About the budget": BASIC within 10 % of it either way is left as it is. */
    static final BigDecimal TOLERANCE = new BigDecimal("0.10");

    /**
     * A tier that has to be raised over the one below goes at least this far over it: one euro more reads as the
     * same package. The live plans the planner builds on its own rise by about half from tier to tier.
     */
    static final BigDecimal STEP_UP = new BigDecimal("1.20");

    /** Every round makes one edit; a package has at most a dozen activities and their extras. */
    private static final int MAX_ROUNDS = 16;

    /** Swaps tried per activity: the nearest few in price on the side that is needed. */
    private static final int SWAPS_PER_ACTIVITY = 3;

    private static final int DROP_EXTRA = 0;
    private static final int SWAP = 1;
    private static final int REMOVE = 2;

    private BudgetFitter() {
    }

    /** The plan with BASIC at about {@code brief}'s budget and the tiers rising; the plan itself without one. */
    public static ComposedPlan fit(PackageEditor editor, ComposedPlan plan, Brief brief, List<CatalogActivity> catalog) {
        BigDecimal budget = brief == null ? null : brief.budgetPerPerson();
        if (editor == null || plan == null || budget == null || catalog == null || catalog.isEmpty()
                || brief.groupSize() == null || packageOf(plan, Tier.BASIC).isEmpty()) {
            return plan;
        }
        Context ctx = new Context(editor, brief, catalog, wishedIds(brief, catalog));
        ComposedPlan working = towardBudget(ctx, plan, budget);
        List<Tier> bottomUp = working.packages().stream().map(ComposedPlan.PackageResult::key).sorted().toList();
        // A tier that is not clearly dearer than the one below it is made so...
        for (int i = 1; i < bottomUp.size(); i++) {
            BigDecimal floor = priceOf(working, bottomUp.get(i - 1), brief).multiply(STEP_UP);
            working = move(ctx, working, bottomUp.get(i), false, price -> price.compareTo(floor) >= 0);
        }
        // ...and where it could not be, the tier below gives way, from the top down.
        for (int i = bottomUp.size() - 1; i > 0; i--) {
            BigDecimal ceiling = priceOf(working, bottomUp.get(i), brief);
            working = move(ctx, working, bottomUp.get(i - 1), true, price -> price.compareTo(ceiling) < 0);
        }
        if (ctx.edits > 0) {
            log.info("planner budget fitted edits={} budget={} fromPerPerson={}", ctx.edits, budget,
                    working.packages().stream().map(pkg -> pkg.key() + ":" + fromPerPerson(pkg, brief)).toList());
        }
        return working;
    }

    /** One traveller's "from" price for the package: what the plan shows and the budget is held to. */
    public static BigDecimal fromPerPerson(ComposedPlan.PackageResult pkg, Brief brief) {
        BigDecimal from = FromPrice.perPerson(pkg.totalPrice(), pkg.extrasTotal(), brief.groupSize());
        return from == null ? BigDecimal.ZERO : from;
    }

    private static final class Context {
        final PackageEditor editor;
        final Brief brief;
        final List<CatalogActivity> catalog;
        final Set<UUID> wished;
        int edits;

        Context(PackageEditor editor, Brief brief, List<CatalogActivity> catalog, Set<UUID> wished) {
            this.editor = editor;
            this.brief = brief;
            this.catalog = catalog;
            this.wished = wished;
        }
    }

    private record Step(int kind, ComposedPlan plan, BigDecimal price) {
    }

    /** BASIC moved, one edit a round, to within the tolerance of the budget; each edit the one landing nearest it. */
    private static ComposedPlan towardBudget(Context ctx, ComposedPlan plan, BigDecimal budget) {
        BigDecimal slack = budget.multiply(TOLERANCE);
        ComposedPlan working = plan;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            BigDecimal price = priceOf(working, Tier.BASIC, ctx.brief);
            BigDecimal off = price.subtract(budget).abs();
            if (off.compareTo(slack) <= 0) {
                break;
            }
            boolean tooDear = price.compareTo(budget) > 0;
            Optional<Step> next = steps(ctx, working, Tier.BASIC, tooDear).stream()
                    .filter(step -> step.price().subtract(budget).abs().compareTo(off) < 0)
                    .min(Comparator.comparing((Step step) -> step.price().subtract(budget).abs())
                            .thenComparingInt(Step::kind));
            if (next.isEmpty()) {
                log.info("planner budget not met fromPerPerson={} budget={}", price, budget);
                break;
            }
            working = next.get().plan();
            ctx.edits++;
        }
        return working;
    }

    /**
     * Edits on one tier, cheaper or dearer, until {@code done}: of the edits that get there, the gentlest kind
     * that changes the price least; when none does, the gentlest kind that gets closest.
     */
    private static ComposedPlan move(Context ctx, ComposedPlan plan, Tier tier, boolean cheaper,
            Predicate<BigDecimal> done) {
        ComposedPlan working = plan;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            Optional<ComposedPlan.PackageResult> pkg = packageOf(working, tier);
            if (pkg.isEmpty() || done.test(fromPerPerson(pkg.get(), ctx.brief))) {
                break;
            }
            List<Step> steps = steps(ctx, working, tier, cheaper);
            Comparator<Step> least = cheaper ? Comparator.comparing(Step::price, Comparator.reverseOrder())
                    : Comparator.comparing(Step::price);
            Optional<Step> next = steps.stream().filter(step -> done.test(step.price()))
                    .min(Comparator.comparingInt(Step::kind).thenComparing(least))
                    .or(() -> steps.stream().min(Comparator.comparingInt(Step::kind).thenComparing(least.reversed())));
            if (next.isEmpty()) {
                break;
            }
            working = next.get().plan();
            ctx.edits++;
        }
        return working;
    }

    /** Every single edit of the package that moves its price the way asked, applied and priced. */
    private static List<Step> steps(Context ctx, ComposedPlan plan, Tier tier, boolean cheaper) {
        ComposedPlan.PackageResult pkg = packageOf(plan, tier).orElseThrow();
        BigDecimal before = fromPerPerson(pkg, ctx.brief);
        List<Step> steps = new ArrayList<>();
        for (Candidate candidate : candidates(pkg, ctx, cheaper)) {
            EditOutcome outcome = ctx.editor.apply(plan, ctx.brief, ctx.catalog, List.of(candidate.edit()));
            if (!outcome.anyApplied()) {
                continue;
            }
            Optional<ComposedPlan.PackageResult> after = packageOf(outcome.plan(), tier);
            if (after.isEmpty() || after.get().activityIds().isEmpty()) {
                continue;
            }
            BigDecimal price = fromPerPerson(after.get(), ctx.brief);
            if (cheaper ? price.compareTo(before) < 0 : price.compareTo(before) > 0) {
                steps.add(new Step(candidate.kind(), outcome.plan(), price));
            }
        }
        return steps;
    }

    private record Candidate(int kind, EditRequest edit) {
    }

    /**
     * Cheaper: chosen extras to drop, cheaper activities of the same kind to swap in, activities to take out.
     * Dearer: dearer activities of the same kind to swap in. Never what was asked for by name.
     */
    private static List<Candidate> candidates(ComposedPlan.PackageResult pkg, Context ctx, boolean cheaper) {
        List<Candidate> candidates = new ArrayList<>();
        Set<UUID> inPackage = new HashSet<>(pkg.activityIds());
        for (ComposedPlan.DayResult day : pkg.days()) {
            for (ComposedPlan.ItemResult item : day.items()) {
                if (cheaper) {
                    for (ComposedPlan.ItemExtra extra : item.extras()) {
                        candidates.add(new Candidate(DROP_EXTRA, new EditRequest(EditOp.REMOVE_EXTRA, item.name(), null,
                                pkg.key(), null, null, List.of(), item.activityId(), extra.name())));
                    }
                }
                if (ctx.wished.contains(item.activityId())) {
                    continue;
                }
                CatalogActivity current = ctx.catalog.stream().filter(a -> a.id().equals(item.activityId()))
                        .findFirst().orElse(null);
                if (current != null) {
                    Comparator<CatalogActivity> nearest = Comparator.comparing(CatalogActivity::price);
                    ctx.catalog.stream()
                            .filter(a -> !inPackage.contains(a.id()))
                            .filter(a -> cheaper ? a.price().compareTo(current.price()) < 0
                                    : a.price().compareTo(current.price()) > 0)
                            .filter(a -> a.categorySlugs().stream().anyMatch(current.categorySlugs()::contains))
                            .sorted(cheaper ? nearest.reversed() : nearest)
                            .limit(SWAPS_PER_ACTIVITY)
                            .forEach(other -> candidates.add(new Candidate(SWAP, new EditRequest(EditOp.REPLACE,
                                    item.name(), other.name(), pkg.key(), day.dayNumber(), null, List.of(),
                                    item.activityId()))));
                }
                if (cheaper) {
                    candidates.add(new Candidate(REMOVE, new EditRequest(EditOp.REMOVE, item.name(), null, pkg.key(),
                            day.dayNumber(), null, List.of(), item.activityId())));
                }
            }
        }
        return candidates;
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

    private static BigDecimal priceOf(ComposedPlan plan, Tier tier, Brief brief) {
        return packageOf(plan, tier).map(pkg -> fromPerPerson(pkg, brief)).orElse(BigDecimal.ZERO);
    }

    private static Optional<ComposedPlan.PackageResult> packageOf(ComposedPlan plan, Tier tier) {
        return plan.packages().stream().filter(pkg -> pkg.key() == tier).findFirst();
    }
}
