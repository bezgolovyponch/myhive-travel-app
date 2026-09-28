package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.model.Tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Converts a stored {@link ComposedPlan} back into an editable {@link PlanDraft} and swaps one package
 * by tier, so {@code ai.edit}'s package editor can re-run a single package through the same
 * draft-shaped validation/assembly pipeline the initial generation used.
 */
public final class PlanDrafts {

    private PlanDrafts() {
    }

    /** Maps every package/day/item field the model can influence back onto its draft counterpart. */
    public static PlanDraft fromComposed(ComposedPlan plan) {
        List<PlanDraft.PackageDraft> packages = new ArrayList<>();
        for (ComposedPlan.PackageResult p : plan.packages()) {
            packages.add(toPackageDraft(p));
        }
        return new PlanDraft(packages);
    }

    private static PlanDraft.PackageDraft toPackageDraft(ComposedPlan.PackageResult p) {
        List<PlanDraft.DayDraft> days = new ArrayList<>();
        for (ComposedPlan.DayResult day : p.days()) {
            days.add(toDayDraft(day));
        }
        return new PlanDraft.PackageDraft(p.key(), p.title(), p.tagline(), p.description(), days);
    }

    private static PlanDraft.DayDraft toDayDraft(ComposedPlan.DayResult day) {
        List<PlanDraft.ItemDraft> items = new ArrayList<>();
        for (ComposedPlan.ItemResult item : day.items()) {
            items.add(new PlanDraft.ItemDraft(item.slot(), item.startHint(), item.activityId(), item.why()));
        }
        return new PlanDraft.DayDraft(day.dayNumber(), day.title(), day.summary(), items);
    }

    /** The package with the given tier, if the draft has one. */
    public static Optional<PlanDraft.PackageDraft> packageOf(PlanDraft draft, Tier key) {
        for (PlanDraft.PackageDraft p : draft.packages()) {
            if (p.key() == key) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** Swaps the package with {@code replacement}'s tier, keeping the original order; appends it if that tier is absent. */
    public static PlanDraft replacePackage(PlanDraft draft, PlanDraft.PackageDraft replacement) {
        List<PlanDraft.PackageDraft> packages = new ArrayList<>();
        boolean replaced = false;
        for (PlanDraft.PackageDraft p : draft.packages()) {
            if (p.key() == replacement.key()) {
                packages.add(replacement);
                replaced = true;
            } else {
                packages.add(p);
            }
        }
        if (!replaced) {
            packages.add(replacement);
        }
        return new PlanDraft(packages);
    }
}
