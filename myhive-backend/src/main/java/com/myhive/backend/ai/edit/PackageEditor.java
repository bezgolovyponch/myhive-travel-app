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
import com.myhive.backend.ai.plan.ViolationCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
@Slf4j
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
        List<EditRequest> batch = edits == null ? List.of() : edits;
        Map<UUID, CatalogActivity> catalogById = indexById(catalog);
        List<UUID> unusable = new ArrayList<>();
        Set<UUID> restored = restoreMissingPlanActivities(plan, catalogById, unusable);
        if (!unusable.isEmpty()) {
            // The closing re-assembly rebuilds every package from this map and silently drops items it
            // cannot price, so an item that is neither in the snapshot nor complete enough to rebuild
            // would be deleted from packages nobody touched. Nothing is worth that: the batch is refused.
            log.warn("planner edit batch refused: {} plan activities are missing from the catalog snapshot "
                    + "and too thin to rebuild {}", unusable.size(), unusable);
            return new EditOutcome(plan, List.of(), allRejected(batch,
                    unusable.size() + " activities in the plan are not in the catalog snapshot"));
        }
        if (!restored.isEmpty()) {
            log.warn("planner edit kept {} plan activities the catalog snapshot no longer lists {}",
                    restored.size(), restored);
        }
        WorkingCatalog working = new WorkingCatalog(List.copyOf(catalogById.values()), catalogById, restored);
        PlanDraft draft = PlanDrafts.fromComposed(plan);
        List<AppliedEdit> applied = new ArrayList<>();
        List<RejectedEdit> rejected = new ArrayList<>();
        for (EditRequest edit : batch) {
            draft = applyEdit(draft, edit, brief, working, applied, rejected);
        }
        if (applied.isEmpty()) {
            // Nothing changed, so there is nothing to re-price: hand back the very plan that came in
            // rather than a round-trip through the assembler.
            return new EditOutcome(plan, List.of(), rejected);
        }
        ComposedPlan edited = assembler.assemble(draft, brief, catalogById, plan.degraded()).plan();
        return new EditOutcome(edited, applied, rejected);
    }

    /** Every op refused for the same editor-side reason, before any of them was looked at. */
    private static List<RejectedEdit> allRejected(List<EditRequest> edits, String detail) {
        List<RejectedEdit> rejected = new ArrayList<>();
        for (EditRequest edit : edits) {
            rejected.add(new RejectedEdit(edit.op(), PlanAssembler.clean(edit.activity()), edit.packageKey(),
                    EditRejectionReason.INTERNAL, detail));
        }
        return rejected;
    }

    /**
     * Puts every activity the plan uses back into the snapshot map, rebuilding the ones it no longer
     * lists from what the plan itself stores about them.
     *
     * <p>This is routine rather than exotic: the snapshot is brief-dependent — sorted by how well an
     * activity matches the brief's categories and cut at
     * {@link com.myhive.backend.ai.catalog.CatalogSnapshotter#MAX_ACTIVITIES} — so undoing back to a
     * generation built from a different brief lands on a plan whose items the current snapshot may
     * simply not contain. Refusing those edits would have been a permanent "try again" on a chat where
     * trying again can never work.
     *
     * <p>Returns the ids it rebuilt, and collects into {@code unusable} the ones it could not.
     */
    private static Set<UUID> restoreMissingPlanActivities(ComposedPlan plan, Map<UUID, CatalogActivity> catalogById,
            List<UUID> unusable) {
        Set<UUID> restored = new LinkedHashSet<>();
        for (ComposedPlan.PackageResult p : plan.packages()) {
            for (ComposedPlan.DayResult day : p.days()) {
                for (ComposedPlan.ItemResult item : day.items()) {
                    if (item.activityId() == null || catalogById.containsKey(item.activityId())) {
                        continue;
                    }
                    CatalogActivity rebuilt = fromPlanItem(item);
                    if (rebuilt == null) {
                        unusable.add(item.activityId());
                    } else {
                        catalogById.put(item.activityId(), rebuilt);
                        restored.add(item.activityId());
                    }
                }
            }
        }
        return restored;
    }

    /**
     * A catalog row rebuilt from a plan item. Everything pricing and scheduling need is stored on the
     * item — id, name, duration, price, group minimum — which is why the re-assembly comes out with the
     * same line totals it went in with. The description and the category slugs are not, and that is
     * exactly why such an entry is never allowed to be placed somewhere new: its slot preference would
     * be a guess. Null when the item is too thin to price or schedule at all.
     */
    private static CatalogActivity fromPlanItem(ComposedPlan.ItemResult item) {
        if (item.durationMinutes() <= 0 || item.price() == null) {
            return null;
        }
        return new CatalogActivity(item.activityId(), item.slug(), item.name(), "", item.durationMinutes(), true,
                item.price(), item.minPrice(), item.imageUrl(), List.of());
    }

    private PlanDraft applyEdit(PlanDraft working, EditRequest edit, Brief brief, WorkingCatalog catalog,
            List<AppliedEdit> applied, List<RejectedEdit> rejected) {
        int appliedBefore = applied.size();
        int rejectedBefore = rejected.size();
        try {
            return applyResolved(working, edit, brief, catalog, applied, rejected);
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

    private PlanDraft applyResolved(PlanDraft working, EditRequest edit, Brief brief, WorkingCatalog catalog,
            List<AppliedEdit> applied, List<RejectedEdit> rejected) {
        CatalogActivity activity = resolveOrReject(edit, edit.activity(), catalog.resolvable(), rejected);
        if (activity == null) {
            return working;
        }
        // An ADD places its activity; REMOVE and REPLACE only name one the plan already holds.
        if (edit.op() == EditOp.ADD && rejectUnplaceable(edit, activity, catalog, rejected)) {
            return working;
        }
        CatalogActivity replacement = null;
        if (edit.op() == EditOp.REPLACE) {
            replacement = resolveOrReject(edit, edit.replacement(), catalog.resolvable(), rejected);
            if (replacement == null || rejectUnplaceable(edit, replacement, catalog, rejected)) {
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
            draft = applyToPackage(draft, edit, activity, replacement, target, brief, catalog.byId(), applied,
                    rejected);
        }
        return draft;
    }

    /**
     * A rebuilt entry stands in for an activity the plan still holds, not for a catalog row: it has no
     * categories to choose a slot by and the activity may have left the catalog for good. It can be named
     * to be dropped or swapped out, never placed somewhere new - reported as unknown, because from the
     * catalog's point of view that is what it is.
     */
    private static boolean rejectUnplaceable(EditRequest edit, CatalogActivity activity, WorkingCatalog catalog,
            List<RejectedEdit> rejected) {
        if (!catalog.isRestored(activity)) {
            return false;
        }
        rejected.add(new RejectedEdit(edit.op(), activity.name(), edit.packageKey(),
                EditRejectionReason.UNKNOWN_ACTIVITY, activity.name() + " is no longer in the catalog"));
        return true;
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
        Violation introduced = violationIntroducedBy(current.get(), outcome.pkg(), brief, catalogById);
        if (introduced != null) {
            rejected.add(new RejectedEdit(edit.op(), activity.name(), target, EditRejectionReason.WOULD_BREAK_SCHEDULE,
                    introduced.code() + ": " + introduced.detail()));
            return working;
        }
        applied.add(new AppliedEdit(edit.op(), activity.name(), replacement == null ? null : replacement.name(), target,
                outcome.dayNumber(), outcome.slot(), outcome.placedActivityId()));
        return PlanDrafts.replacePackage(working, outcome.pkg());
    }

    /**
     * The first rule the op broke that was not already broken, or null when it broke nothing new. The
     * package before the op has to be checked too: a degraded or fallback plan routinely ships with
     * violations of its own (an empty middle day, a tier that is not distinct), and refusing on any
     * violation present afterwards made every such package permanently un-editable — with a
     * {@code WOULD_BREAK_SCHEDULE} blaming the organizer's edit for a rule the plan already broke.
     */
    private Violation violationIntroducedBy(PlanDraft.PackageDraft before, PlanDraft.PackageDraft after, Brief brief,
            Map<UUID, CatalogActivity> catalogById) {
        Set<ViolationKey> existing = new HashSet<>();
        for (Violation violation : validator.validatePackage(before, brief, catalogById)) {
            if (violation.code() == ViolationCode.WRONG_DAY_COUNT) {
                // The validator returns on a wrong day count before it checks a single day, so neither
                // list says anything about the day rules and the diff would wave everything through.
                // The package is malformed to begin with: refuse the op rather than edit it blind.
                return violation;
            }
            existing.add(ViolationKey.of(violation));
        }
        for (Violation violation : validator.validatePackage(after, brief, catalogById)) {
            if (!existing.contains(ViolationKey.of(violation))) {
                return violation;
            }
        }
        return null;
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
            int minutes = PlanValidator.dayMinutes(day, catalogById) + activity.durationMinutes()
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

    /**
     * A name that resolves is replaced by the catalog's own spelling; one that does not is reported as the
     * model wrote it, so it is cleaned first — this is the one string in a report that never passed through
     * {@link PlanAssembler}, and it is both stored and rendered into the chat.
     */
    private static CatalogActivity resolveOrReject(EditRequest edit, String name, List<CatalogActivity> catalog,
            List<RejectedEdit> rejected) {
        return switch (ActivityNameResolver.resolve(name, catalog)) {
            case ActivityNameResolver.Found found -> found.activity();
            case ActivityNameResolver.Ambiguous ambiguous -> {
                rejected.add(new RejectedEdit(edit.op(), PlanAssembler.clean(name), null,
                        EditRejectionReason.AMBIGUOUS_ACTIVITY, String.join(", ", ambiguous.candidates())));
                yield null;
            }
            case ActivityNameResolver.NotFound ignored -> {
                String cleaned = PlanAssembler.clean(name);
                rejected.add(new RejectedEdit(edit.op(), cleaned, null, EditRejectionReason.UNKNOWN_ACTIVITY, cleaned));
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

    /**
     * What makes two violations "the same rule broken in the same place". {@code Violation} carries no
     * slot, so a day is as fine-grained as this gets: a second {@code SLOT_TAKEN} on a day that already
     * had one reads as pre-existing, which is the safe way round — the alternative refuses edits to
     * packages the planner itself produced.
     *
     * <p>Safe because this diff is a backstop, not the gate: {@link #placement} has already refused to
     * put anything in a taken slot, outside the day's window, or over the tier's item and minute caps.
     * Finer granularity here would only start rejecting edits on already-broken packages again.
     */
    private record ViolationKey(ViolationCode code, Tier packageKey, Integer dayNumber) {

        static ViolationKey of(Violation violation) {
            return new ViolationKey(violation.code(), violation.packageKey(), violation.dayNumber());
        }
    }

    /**
     * The catalog one batch works against: the snapshot from state, plus the plan's own items where that
     * snapshot no longer lists them. {@code resolvable} is what a name is matched against — the rebuilt
     * entries are in it, so an activity the plan holds can still be named to be dropped or swapped —
     * while {@code restored} marks the ones that must never be placed somewhere new.
     */
    private record WorkingCatalog(List<CatalogActivity> resolvable, Map<UUID, CatalogActivity> byId,
                                  Set<UUID> restored) {

        boolean isRestored(CatalogActivity activity) {
            return restored.contains(activity.id());
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
