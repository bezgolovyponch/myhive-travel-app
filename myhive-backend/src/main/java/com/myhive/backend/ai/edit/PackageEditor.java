package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.plan.PlanDrafts;
import com.myhive.backend.ai.plan.PlanValidator;
import com.myhive.backend.ai.plan.Violation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Applies add/remove/replace edits to an already composed plan, deterministically and without the model:
 * the plan goes back to its {@link PlanDraft} form, every edit is applied to one package at a time, the
 * touched package is re-checked with {@link PlanValidator} (scheduling rules only) and the whole thing is
 * re-priced by {@link PlanAssembler}, so prices, line totals and totals are never hand-edited.
 *
 * <p>Edits are independent: a rejection on one package or one edit leaves the rest of the batch intact,
 * and a rejected edit leaves the plan exactly as it was before it. {@code TIER_ORDER} from the assembler
 * is ignored here — once an organizer edits a package by hand, the tiers may legitimately cross.
 */
@Component
public class PackageEditor {

    /** Activities of these categories read as evening plans, so they are offered the late slots first. */
    private static final Set<String> EVENING_CATEGORY_SLUGS = Set.of("nightlife", "adult", "dining", "food");

    private static final List<Slot> EVENING_FIRST = List.of(Slot.EVENING, Slot.NIGHT, Slot.AFTERNOON, Slot.MORNING);
    private static final List<Slot> DAYTIME_FIRST = List.of(Slot.MORNING, Slot.AFTERNOON, Slot.EVENING, Slot.NIGHT);

    private final PlanValidator validator;
    private final PlanAssembler assembler;

    public PackageEditor(PlanValidator validator, PlanAssembler assembler) {
        this.validator = validator;
        this.assembler = assembler;
    }

    public EditOutcome apply(ComposedPlan plan, Brief brief, List<CatalogActivity> catalog, List<EditRequest> edits) {
        Map<UUID, CatalogActivity> catalogById = indexById(catalog);
        PlanDraft working = PlanDrafts.fromComposed(plan);
        List<AppliedEdit> applied = new ArrayList<>();
        List<RejectedEdit> rejected = new ArrayList<>();
        for (EditRequest edit : edits == null ? List.<EditRequest>of() : edits) {
            working = applyEdit(working, edit, brief, catalog, catalogById, applied, rejected);
        }
        ComposedPlan edited = assembler.assemble(working, brief, catalogById, plan.degraded()).plan();
        return new EditOutcome(edited, applied, rejected);
    }

    private PlanDraft applyEdit(PlanDraft working, EditRequest edit, Brief brief, List<CatalogActivity> catalog,
            Map<UUID, CatalogActivity> catalogById, List<AppliedEdit> applied, List<RejectedEdit> rejected) {
        int appliedBefore = applied.size();
        int rejectedBefore = rejected.size();
        try {
            return applyResolved(working, edit, brief, catalog, catalogById, applied, rejected);
        } catch (RuntimeException e) {
            // A bug in the editor must not cost the organizer the rest of the batch: this edit alone is
            // reported as INTERNAL, its half-finished per-package entries are dropped, the draft stays
            // exactly as it was before it, and the loop carries on with the next edit.
            truncate(applied, appliedBefore);
            truncate(rejected, rejectedBefore);
            rejected.add(new RejectedEdit(edit.op(), edit.activity(), edit.packageKey(), EditRejectionReason.INTERNAL,
                    e.getClass().getSimpleName()));
            return working;
        }
    }

    private PlanDraft applyResolved(PlanDraft working, EditRequest edit, Brief brief, List<CatalogActivity> catalog,
            Map<UUID, CatalogActivity> catalogById, List<AppliedEdit> applied, List<RejectedEdit> rejected) {
        CatalogActivity activity = resolveOrReject(edit, edit.activity(), catalog, rejected);
        if (activity == null) {
            return working;
        }
        CatalogActivity replacement = null;
        if (edit.op() == EditOp.REPLACE) {
            replacement = resolveOrReject(edit, edit.replacement(), catalog, rejected);
            if (replacement == null) {
                return working;
            }
        }
        List<Tier> targets = targets(working, edit, activity);
        if (targets.isEmpty() && edit.op() != EditOp.ADD) {
            rejected.add(new RejectedEdit(edit.op(), activity.name(), null, EditRejectionReason.NOT_IN_PACKAGE,
                    activity.name() + " is not in any package"));
            return working;
        }
        PlanDraft draft = working;
        for (Tier target : targets) {
            draft = applyToPackage(draft, edit, activity, replacement, target, brief, catalogById, applied, rejected);
        }
        return draft;
    }

    private PlanDraft applyToPackage(PlanDraft working, EditRequest edit, CatalogActivity activity,
            CatalogActivity replacement, Tier target, Brief brief, Map<UUID, CatalogActivity> catalogById,
            List<AppliedEdit> applied, List<RejectedEdit> rejected) {
        Optional<PlanDraft.PackageDraft> current = PlanDrafts.packageOf(working, target);
        if (current.isEmpty()) {
            rejected.add(new RejectedEdit(edit.op(), activity.name(), target, EditRejectionReason.NOT_IN_PACKAGE,
                    "package " + target + " is not in the plan"));
            return working;
        }
        Outcome outcome = switch (edit.op()) {
            case REMOVE -> applyRemove(current.get(), activity);
            case ADD -> applyAdd(current.get(), activity, edit.dayNumber(), edit.slot(), brief, catalogById);
            case REPLACE -> applyReplace(current.get(), activity, replacement, brief, catalogById);
        };
        if (outcome.reason() != null) {
            rejected.add(new RejectedEdit(edit.op(), activity.name(), target, outcome.reason(), outcome.detail()));
            return working;
        }
        List<Violation> violations = validator.validatePackage(outcome.pkg(), brief, catalogById);
        if (!violations.isEmpty()) {
            Violation first = violations.get(0);
            rejected.add(new RejectedEdit(edit.op(), activity.name(), target, EditRejectionReason.WOULD_BREAK_SCHEDULE,
                    first.code() + ": " + first.detail()));
            return working;
        }
        applied.add(new AppliedEdit(edit.op(), activity.name(), replacement == null ? null : replacement.name(), target,
                outcome.dayNumber(), outcome.slot(), outcome.placedActivityId()));
        return PlanDrafts.replacePackage(working, outcome.pkg());
    }

    private static Outcome applyRemove(PlanDraft.PackageDraft pkg, CatalogActivity activity) {
        List<PlanDraft.DayDraft> days = new ArrayList<>();
        Integer removedDay = null;
        Slot removedSlot = null;
        int remaining = 0;
        for (PlanDraft.DayDraft day : pkg.days()) {
            List<PlanDraft.ItemDraft> items = new ArrayList<>();
            for (PlanDraft.ItemDraft item : day.items()) {
                if (activity.id().equals(item.activityId())) {
                    if (removedDay == null) {
                        removedDay = day.dayNumber();
                        removedSlot = item.slot();
                    }
                } else {
                    items.add(item);
                }
            }
            remaining += items.size();
            days.add(new PlanDraft.DayDraft(day.dayNumber(), day.title(), day.summary(), items));
        }
        if (removedDay == null) {
            return Outcome.rejected(EditRejectionReason.NOT_IN_PACKAGE, activity.name() + " is not in " + pkg.key());
        }
        if (remaining == 0) {
            return Outcome.rejected(EditRejectionReason.WOULD_EMPTY_PACKAGE,
                    "removing " + activity.name() + " would leave " + pkg.key() + " with no activities");
        }
        return Outcome.ok(withDays(pkg, days), removedDay, removedSlot, null);
    }

    private static Outcome applyAdd(PlanDraft.PackageDraft pkg, CatalogActivity activity, Integer dayNumber, Slot slot,
            Brief brief, Map<UUID, CatalogActivity> catalogById) {
        if (contains(pkg, activity.id())) {
            return Outcome.rejected(EditRejectionReason.ALREADY_IN_PACKAGE,
                    activity.name() + " is already in " + pkg.key());
        }
        return placement(pkg, activity, dayNumber, slot, brief, catalogById)
                .orElseGet(() -> Outcome.rejected(EditRejectionReason.NO_FREE_SLOT, noFreeSlot(pkg, activity)));
    }

    private static Outcome applyReplace(PlanDraft.PackageDraft pkg, CatalogActivity activity,
            CatalogActivity replacement, Brief brief, Map<UUID, CatalogActivity> catalogById) {
        Optional<Outcome> freed = removeFirstOccurrence(pkg, activity.id());
        if (freed.isEmpty()) {
            return Outcome.rejected(EditRejectionReason.NOT_IN_PACKAGE, activity.name() + " is not in " + pkg.key());
        }
        if (contains(pkg, replacement.id())) {
            return Outcome.rejected(EditRejectionReason.ALREADY_IN_PACKAGE,
                    replacement.name() + " is already in " + pkg.key());
        }
        PlanDraft.PackageDraft without = freed.get().pkg();
        Optional<Outcome> placed =
                placement(without, replacement, freed.get().dayNumber(), freed.get().slot(), brief, catalogById);
        if (placed.isEmpty()) {
            placed = placement(without, replacement, null, null, brief, catalogById);
        }
        return placed.orElseGet(() -> Outcome.rejected(EditRejectionReason.NO_FREE_SLOT, noFreeSlot(pkg, replacement)));
    }

    /** First day (in order) and slot where the activity fits the tier's item and minute caps and the day's window. */
    private static Optional<Outcome> placement(PlanDraft.PackageDraft pkg, CatalogActivity activity, Integer dayNumber,
            Slot slot, Brief brief, Map<UUID, CatalogActivity> catalogById) {
        for (PlanDraft.DayDraft day : pkg.days()) {
            if (dayNumber != null && day.dayNumber() != dayNumber) {
                continue;
            }
            if (day.items().size() + 1 > pkg.key().maxItemsPerDay()) {
                continue;
            }
            int minutes = minutesOf(day, catalogById) + activity.durationMinutes()
                    + (day.items().isEmpty() ? 0 : PlanValidator.BUFFER_MINUTES);
            if (minutes > pkg.key().maxMinutesPerDay()) {
                continue;
            }
            Set<Slot> allowed = PlanValidator.allowedSlots(day.dayNumber(), brief);
            Set<Slot> used = usedSlots(day);
            for (Slot candidate : slot == null ? slotPreference(activity) : List.of(slot)) {
                if (allowed.contains(candidate) && !used.contains(candidate)) {
                    PlanDraft.DayDraft withItem = insert(day, activity, candidate);
                    return Optional.of(Outcome.ok(withDay(pkg, withItem), day.dayNumber(), candidate, activity.id()));
                }
            }
        }
        return Optional.empty();
    }

    /** Drops the first occurrence only and reports the cell it freed; a validated package holds no duplicates. */
    private static Optional<Outcome> removeFirstOccurrence(PlanDraft.PackageDraft pkg, UUID activityId) {
        List<PlanDraft.DayDraft> days = new ArrayList<>();
        Integer freedDay = null;
        Slot freedSlot = null;
        for (PlanDraft.DayDraft day : pkg.days()) {
            List<PlanDraft.ItemDraft> items = new ArrayList<>();
            for (PlanDraft.ItemDraft item : day.items()) {
                if (freedDay == null && activityId.equals(item.activityId())) {
                    freedDay = day.dayNumber();
                    freedSlot = item.slot();
                } else {
                    items.add(item);
                }
            }
            days.add(new PlanDraft.DayDraft(day.dayNumber(), day.title(), day.summary(), items));
        }
        if (freedDay == null) {
            return Optional.empty();
        }
        return Optional.of(Outcome.ok(withDays(pkg, days), freedDay, freedSlot, null));
    }

    /** The day's catalog durations plus the same inter-activity buffer {@link PlanValidator} charges. */
    private static int minutesOf(PlanDraft.DayDraft day, Map<UUID, CatalogActivity> catalogById) {
        int minutes = 0;
        for (PlanDraft.ItemDraft item : day.items()) {
            CatalogActivity activity = catalogById.get(item.activityId());
            if (activity != null) {
                minutes += activity.durationMinutes();
            }
        }
        return minutes + PlanValidator.BUFFER_MINUTES * Math.max(0, day.items().size() - 1);
    }

    private static List<Slot> slotPreference(CatalogActivity activity) {
        for (String slug : activity.categorySlugs()) {
            if (slug != null && EVENING_CATEGORY_SLUGS.contains(slug.toLowerCase(Locale.ROOT))) {
                return EVENING_FIRST;
            }
        }
        return DAYTIME_FIRST;
    }

    /** A new item carries no start hint and no reason; the text refresh fills the reason in afterwards. */
    private static PlanDraft.DayDraft insert(PlanDraft.DayDraft day, CatalogActivity activity, Slot slot) {
        List<PlanDraft.ItemDraft> items = new ArrayList<>(day.items());
        items.add(new PlanDraft.ItemDraft(slot, null, activity.id(), ""));
        items.sort(Comparator.comparingInt(item -> item.slot() == null ? Integer.MAX_VALUE : item.slot().ordinal()));
        return new PlanDraft.DayDraft(day.dayNumber(), day.title(), day.summary(), items);
    }

    private static CatalogActivity resolveOrReject(EditRequest edit, String name, List<CatalogActivity> catalog,
            List<RejectedEdit> rejected) {
        return switch (ActivityNameResolver.resolve(name, catalog)) {
            case ActivityNameResolver.Found found -> found.activity();
            case ActivityNameResolver.Ambiguous ambiguous -> {
                rejected.add(new RejectedEdit(edit.op(), name, null, EditRejectionReason.AMBIGUOUS_ACTIVITY,
                        String.join(", ", ambiguous.candidates())));
                yield null;
            }
            case ActivityNameResolver.NotFound ignored -> {
                rejected.add(new RejectedEdit(edit.op(), name, null, EditRejectionReason.UNKNOWN_ACTIVITY, name));
                yield null;
            }
        };
    }

    private static List<Tier> targets(PlanDraft working, EditRequest edit, CatalogActivity activity) {
        if (edit.packageKey() != null) {
            return List.of(edit.packageKey());
        }
        List<Tier> targets = new ArrayList<>();
        for (PlanDraft.PackageDraft pkg : working.packages()) {
            if (edit.op() == EditOp.ADD || contains(pkg, activity.id())) {
                targets.add(pkg.key());
            }
        }
        return targets;
    }

    private static boolean contains(PlanDraft.PackageDraft pkg, UUID activityId) {
        for (PlanDraft.DayDraft day : pkg.days()) {
            for (PlanDraft.ItemDraft item : day.items()) {
                if (activityId.equals(item.activityId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<Slot> usedSlots(PlanDraft.DayDraft day) {
        Set<Slot> used = EnumSet.noneOf(Slot.class);
        for (PlanDraft.ItemDraft item : day.items()) {
            if (item.slot() != null) {
                used.add(item.slot());
            }
        }
        return used;
    }

    private static PlanDraft.PackageDraft withDay(PlanDraft.PackageDraft pkg, PlanDraft.DayDraft replacement) {
        List<PlanDraft.DayDraft> days = new ArrayList<>();
        for (PlanDraft.DayDraft day : pkg.days()) {
            days.add(day.dayNumber() == replacement.dayNumber() ? replacement : day);
        }
        return withDays(pkg, days);
    }

    private static PlanDraft.PackageDraft withDays(PlanDraft.PackageDraft pkg, List<PlanDraft.DayDraft> days) {
        return new PlanDraft.PackageDraft(pkg.key(), pkg.title(), pkg.tagline(), pkg.description(), days);
    }

    private static String noFreeSlot(PlanDraft.PackageDraft pkg, CatalogActivity activity) {
        return "no free slot for " + activity.name() + " in " + pkg.key();
    }

    private static Map<UUID, CatalogActivity> indexById(List<CatalogActivity> catalog) {
        Map<UUID, CatalogActivity> index = new LinkedHashMap<>();
        if (catalog != null) {
            for (CatalogActivity activity : catalog) {
                index.put(activity.id(), activity);
            }
        }
        return index;
    }

    private static void truncate(List<?> list, int size) {
        if (list.size() > size) {
            list.subList(size, list.size()).clear();
        }
    }

    /** One package-level attempt: either a new package draft with the cell it touched, or a rejection. */
    private record Outcome(PlanDraft.PackageDraft pkg, int dayNumber, Slot slot, UUID placedActivityId,
                           EditRejectionReason reason, String detail) {

        static Outcome ok(PlanDraft.PackageDraft pkg, int dayNumber, Slot slot, UUID placedActivityId) {
            return new Outcome(pkg, dayNumber, slot, placedActivityId, null, null);
        }

        static Outcome rejected(EditRejectionReason reason, String detail) {
            return new Outcome(null, 0, null, null, reason, detail);
        }
    }
}
