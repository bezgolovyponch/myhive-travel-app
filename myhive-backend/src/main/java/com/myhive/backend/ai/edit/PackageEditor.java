package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogExtra;
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
import com.myhive.backend.entity.ExtraPricing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
 * Applies add/remove/replace/move edits, and the choice of an activity's extras, to an already composed plan,
 * deterministically and without the model:
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
        List<EditRequest> batch = placementsFirst(edits);
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

    /**
     * The batch with every extra op moved after the ops that place, drop or move activities, each group in the
     * order it was asked for. An extra can only be chosen on an activity a package holds, and the model writes
     * "a river cruise with a private show" in either order: run as written, an extra asked first was refused
     * and the cruise then landed without it.
     */
    private static List<EditRequest> placementsFirst(List<EditRequest> edits) {
        if (edits == null) {
            return List.of();
        }
        List<EditRequest> ordered = new ArrayList<>(edits);
        ordered.sort(Comparator.comparing((EditRequest edit) -> edit.op() != null && edit.op().isExtraOp()));
        return ordered;
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
     * item — id, name, duration, price, group minimum and the chosen extras with their prices — which is why
     * the re-assembly comes out with the same line totals it went in with. Only the chosen extras survive,
     * so nothing else can be added to such an entry. The description and the category slugs are not, and that is
     * exactly why such an entry is never allowed to be placed somewhere new: its slot preference would
     * be a guess. Null when the item is too thin to price or schedule at all.
     */
    private static CatalogActivity fromPlanItem(ComposedPlan.ItemResult item) {
        if (item.durationMinutes() <= 0 || item.price() == null) {
            return null;
        }
        return new CatalogActivity(item.activityId(), item.slug(), item.name(), "", item.durationMinutes(), true,
                item.price(), item.minPrice(), item.imageUrl(), List.of(), item.includes(),
                item.extras().stream()
                        .map(extra -> new CatalogExtra(extra.code(), extra.name(), extra.price(), extra.pricing()))
                        .toList());
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
        CatalogActivity activity = edit.activityId() == null
                ? resolveOrReject(edit, edit.activity(), catalog.resolvable(), rejected)
                : byIdOrReject(edit, catalog, rejected);
        if (activity == null) {
            return working;
        }
        // An extra is one of the activity's own, so it is resolved once, before any package is looked at.
        CatalogExtra extra = null;
        if (edit.op().isExtraOp()) {
            extra = resolveExtraOrReject(edit, activity, rejected);
            if (extra == null) {
                return working;
            }
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
        // "The bubble football with thirty more minutes": an extra asked for on an activity the plan does
        // not hold is a wish for both. The model sends only the extra about as often as not, and refusing
        // it ("is not in your trip plan, so there was nothing to change") answered a request with a
        // technicality. The activity goes in first, like any ADD; if it cannot, that refusal is the answer.
        if (edit.op() == EditOp.ADD_EXTRA && lacksActivity(working, edit, activity)) {
            int appliedBefore = applied.size();
            working = applyResolved(working, new EditRequest(EditOp.ADD, activity.name(), null, edit.packageKey(),
                    edit.dayNumber(), null, List.of(), activity.id()), brief, catalog, applied, rejected);
            if (applied.size() == appliedBefore) {
                return working;
            }
        }
        List<Tier> targets = targets(working, edit, activity);
        if (targets.isEmpty() && edit.op() != EditOp.ADD) {
            rejected.add(new RejectedEdit(edit.op(), activity.name(), null, EditRejectionReason.NOT_IN_PACKAGE,
                    activity.name() + " is not in any package", List.of(), extra == null ? null : extra.name()));
            return working;
        }
        PlanDraft draft = working;
        for (Tier target : targets) {
            draft = applyToPackage(draft, edit, new Subjects(activity, replacement, extra), target, brief,
                    catalog.byId(), applied, rejected);
        }
        return draft;
    }

    /**
     * The extra the edit names, among those the activity offers; null (and rejected as {@code UNKNOWN_EXTRA}
     * with the offered ones as its detail) when it offers none by that name. The rejection keeps the
     * organizer's own words for the extra, cleaned like every model string that is stored or served.
     */
    private static CatalogExtra resolveExtraOrReject(EditRequest edit, CatalogActivity activity,
            List<RejectedEdit> rejected) {
        Optional<CatalogExtra> extra = ExtraNameResolver.resolve(edit.extra(), activity.extras());
        if (extra.isEmpty()) {
            rejected.add(new RejectedEdit(edit.op(), activity.name(), null, EditRejectionReason.UNKNOWN_EXTRA,
                    offeredExtras(activity), List.of(), PlanAssembler.clean(edit.extra())));
        }
        return extra.orElse(null);
    }

    /** "Private show, Open bar": what the activity offers, in catalog order; empty when it offers nothing. */
    private static String offeredExtras(CatalogActivity activity) {
        return String.join(", ", activity.extras().stream().map(CatalogExtra::name).toList());
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
                EditRejectionReason.UNKNOWN_ACTIVITY, activity.name() + " is no longer in the catalog",
                offeredAlternatives(edit, catalog.resolvable())));
        return true;
    }

    private PlanDraft applyToPackage(PlanDraft working, EditRequest edit, Subjects subjects, Tier target,
            Brief brief, Map<UUID, CatalogActivity> catalogById, List<AppliedEdit> applied,
            List<RejectedEdit> rejected) {
        CatalogActivity activity = subjects.activity();
        Optional<PlanDraft.PackageDraft> current = PlanDrafts.packageOf(working, target);
        if (current.isEmpty()) {
            rejected.add(new RejectedEdit(edit.op(), activity.name(), target, EditRejectionReason.NOT_IN_PACKAGE,
                    "package " + target + " is not in the plan", List.of(), subjects.extraName()));
            return working;
        }
        Outcome outcome = switch (edit.op()) {
            case REMOVE -> applyRemove(current.get(), activity);
            case ADD -> applyAdd(current.get(), activity, edit.dayNumber(), edit.slot(), brief, catalogById);
            case REPLACE -> applyReplace(current.get(), activity, subjects.replacement(), brief, catalogById);
            case MOVE -> applyMove(current.get(), activity, edit.dayNumber());
            case ADD_EXTRA -> applyExtraChange(current.get(), activity, subjects.extra(), true);
            case REMOVE_EXTRA -> applyExtraChange(current.get(), activity, subjects.extra(), false);
        };
        if (outcome.reason() != null) {
            rejected.add(new RejectedEdit(edit.op(), activity.name(), target, outcome.reason(), outcome.detail(),
                    List.of(), subjects.extraName()));
            return working;
        }
        // An extra changes no cell of the schedule, so there is no rule it could break.
        Violation introduced = edit.op().isExtraOp() ? null
                : violationIntroducedBy(edit.op(), current.get(), outcome.pkg(), brief, catalogById);
        if (introduced != null) {
            rejected.add(new RejectedEdit(edit.op(), activity.name(), target, EditRejectionReason.WOULD_BREAK_SCHEDULE,
                    introduced.code() + ": " + introduced.detail()));
            return working;
        }
        applied.add(new AppliedEdit(edit.op(), activity.name(), subjects.replacementName(), target,
                outcome.dayNumber(), outcome.slot(), outcome.placedActivityId(), subjects.extraName(),
                subjects.extraPrice(), subjects.extraPricing()));
        return PlanDrafts.replacePackage(working, outcome.pkg());
    }

    /**
     * Chooses ({@code add}) or drops one extra on the activity's item in the package. The item keeps its cell
     * and nothing is placed, so the outcome names the activity's own day and slot and no placed activity -
     * the text refresh then has no "why" to rewrite. Refused when the package does not hold the activity,
     * when the extra is chosen already ({@code add}) or was never chosen (remove).
     */
    private static Outcome applyExtraChange(PlanDraft.PackageDraft pkg, CatalogActivity activity, CatalogExtra extra,
            boolean add) {
        List<PlanDraft.DayDraft> days = new ArrayList<>();
        Integer dayNumber = null;
        Slot slot = null;
        boolean alreadyThatWay = false;
        for (PlanDraft.DayDraft day : pkg.days()) {
            List<PlanDraft.ItemDraft> items = new ArrayList<>();
            for (PlanDraft.ItemDraft item : day.items()) {
                if (dayNumber != null || !activity.id().equals(item.activityId())) {
                    items.add(item);
                    continue;
                }
                dayNumber = day.dayNumber();
                slot = item.slot();
                alreadyThatWay = item.extraCodes().contains(extra.code()) == add;
                items.add(alreadyThatWay ? item : withExtraCodes(item, changed(item.extraCodes(), extra.code(), add)));
            }
            days.add(new PlanDraft.DayDraft(day.dayNumber(), day.title(), day.summary(), items));
        }
        if (dayNumber == null) {
            return Outcome.rejected(EditRejectionReason.NOT_IN_PACKAGE, activity.name() + " is not in " + pkg.key());
        }
        if (alreadyThatWay) {
            return add
                    ? Outcome.rejected(EditRejectionReason.ALREADY_HAS_EXTRA,
                            extra.name() + " is already chosen on " + activity.name() + " in " + pkg.key())
                    : Outcome.rejected(EditRejectionReason.EXTRA_NOT_SELECTED,
                            extra.name() + " is not chosen on " + activity.name() + " in " + pkg.key());
        }
        return Outcome.ok(withDays(pkg, days), dayNumber, slot, null);
    }

    private static List<String> changed(List<String> codes, String code, boolean add) {
        List<String> result = new ArrayList<>(codes);
        if (add) {
            result.add(code);
        } else {
            result.remove(code);
        }
        return result;
    }

    private static PlanDraft.ItemDraft withExtraCodes(PlanDraft.ItemDraft item, List<String> extraCodes) {
        return new PlanDraft.ItemDraft(item.slot(), item.startHint(), item.activityId(), item.why(), extraCodes);
    }

    /**
     * The first rule the op broke that was not already broken, or null when it broke nothing new. The
     * package before the op has to be checked too: a degraded or fallback plan routinely ships with
     * violations of its own (an empty middle day, a tier that is not distinct), and refusing on any
     * violation present afterwards made every such package permanently un-editable — with a
     * {@code WOULD_BREAK_SCHEDULE} blaming the organizer's edit for a rule the plan already broke.
     */
    private Violation violationIntroducedBy(EditOp op, PlanDraft.PackageDraft before, PlanDraft.PackageDraft after,
            Brief brief,
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
            // Dragging a day's only activity elsewhere leaves that day free on purpose.
            boolean freedByAMove = op == EditOp.MOVE && violation.code() == ViolationCode.EMPTY_DAY;
            if (!existing.contains(ViolationKey.of(violation)) && !ORGANIZERS_CALL.contains(violation.code())
                    && !freedByAMove) {
                return violation;
            }
        }
        return null;
    }

    /**
     * How full a day gets and which hours it uses are the organizer's to decide: an edit that overfills a
     * day or doubles up a slot is carried out, not refused. The planner sorts the timing after the vote.
     */
    private static final Set<ViolationCode> ORGANIZERS_CALL = Set.of(ViolationCode.DAY_OVER_ITEMS,
            ViolationCode.DAY_OVER_MINUTES, ViolationCode.SLOT_TAKEN, ViolationCode.SLOT_OUTSIDE_WINDOW);

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
        // An add is never turned away: with no free slot left it goes on the lightest day anyway - or,
        // when the organizer named the day, on that day, however full it is.
        return placement(pkg, activity, dayNumber, slot, brief, catalogById)
                .or(() -> onDay(pkg, activity, dayNumber))
                .orElseGet(() -> anywhere(pkg, activity, catalogById));
    }

    /** The named day, in the slot the activity prefers (shared when taken); empty when no such day. */
    private static Optional<Outcome> onDay(PlanDraft.PackageDraft pkg, CatalogActivity activity, Integer dayNumber) {
        return onDay(pkg, activity, dayNumber, List.of());
    }

    /** As above, the item carrying {@code extraCodes}: a moved activity takes its chosen extras along. */
    private static Optional<Outcome> onDay(PlanDraft.PackageDraft pkg, CatalogActivity activity, Integer dayNumber,
            List<String> extraCodes) {
        if (dayNumber == null) {
            return Optional.empty();
        }
        return pkg.days().stream().filter(day -> day.dayNumber() == dayNumber).findFirst().map(day -> {
            Set<Slot> used = usedSlots(day);
            List<Slot> preferred = slotPreference(activity);
            Slot slot = preferred.stream().filter(candidate -> !used.contains(candidate)).findFirst()
                    .orElse(preferred.get(0));
            return Outcome.ok(withDay(pkg, insert(day, activity, slot, extraCodes)), dayNumber, slot, activity.id());
        });
    }

    /**
     * Where an activity goes when no day has a free slot for it: the day with the least on it, in the
     * slot it would normally prefer, shared with whatever is there. The plan is the organizer's wish list
     * for the group - how a full day is actually timed is settled by the planner after the vote.
     */
    private static Outcome anywhere(PlanDraft.PackageDraft pkg, CatalogActivity activity,
            Map<UUID, CatalogActivity> catalogById) {
        PlanDraft.DayDraft lightest = pkg.days().get(0);
        for (PlanDraft.DayDraft day : pkg.days()) {
            if (PlanValidator.dayMinutes(day, catalogById) < PlanValidator.dayMinutes(lightest, catalogById)) {
                lightest = day;
            }
        }
        Set<Slot> used = usedSlots(lightest);
        List<Slot> preferred = slotPreference(activity);
        Slot slot = preferred.stream().filter(candidate -> !used.contains(candidate)).findFirst()
                .orElse(preferred.get(0));
        return Outcome.ok(withDay(pkg, insert(lightest, activity, slot)), lightest.dayNumber(), slot, activity.id());
    }

    /**
     * The activity leaves its day and goes on {@code dayNumber}, in the slot it would normally prefer
     * (shared when that one is taken). Never refused for lack of room; a day the trip does not have is.
     */
    private static Outcome applyMove(PlanDraft.PackageDraft pkg, CatalogActivity activity, Integer dayNumber) {
        List<String> extraCodes = extraCodesOf(pkg, activity.id());
        Optional<Outcome> freed = removeFirstOccurrence(pkg, activity.id());
        if (freed.isEmpty()) {
            return Outcome.rejected(EditRejectionReason.NOT_IN_PACKAGE, activity.name() + " is not in " + pkg.key());
        }
        if (dayNumber != null && freed.get().dayNumber() == dayNumber) {
            return Outcome.rejected(EditRejectionReason.ALREADY_ON_DAY, activity.name() + " is on day " + dayNumber);
        }
        PlanDraft.PackageDraft without = freed.get().pkg();
        Optional<PlanDraft.DayDraft> target = without.days().stream()
                .filter(day -> dayNumber != null && day.dayNumber() == dayNumber).findFirst();
        if (target.isEmpty()) {
            return Outcome.rejected(EditRejectionReason.WOULD_BREAK_SCHEDULE,
                    "day " + dayNumber + " is not part of " + pkg.key());
        }
        return onDay(without, activity, dayNumber, extraCodes).orElseThrow();
    }

    /** The extras chosen on the activity's first item in the package; empty when it has none or is not there. */
    private static List<String> extraCodesOf(PlanDraft.PackageDraft pkg, UUID activityId) {
        for (PlanDraft.DayDraft day : pkg.days()) {
            for (PlanDraft.ItemDraft item : day.items()) {
                if (activityId.equals(item.activityId())) {
                    return item.extraCodes();
                }
            }
        }
        return List.of();
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
        return placed.orElseGet(() -> anywhere(without, replacement, catalogById));
    }

    /**
     * How full an edited day may get. The tier's own day caps shape what the planner offers - a Basic
     * day is two activities - but what the organizer adds is theirs to decide: a Basic draft with a free
     * evening takes a third. So an edit is held to the roomiest day any tier has, not to the tier's.
     */
    private static final Tier DAY_LIMITS = Tier.PREMIUM;

    /** First day (in order) and slot where the activity fits the day's window and {@link #DAY_LIMITS}. */
    private static Optional<Outcome> placement(PlanDraft.PackageDraft pkg, CatalogActivity activity, Integer dayNumber,
            Slot slot, Brief brief, Map<UUID, CatalogActivity> catalogById) {
        for (PlanDraft.DayDraft day : pkg.days()) {
            if (dayNumber != null && day.dayNumber() != dayNumber) {
                continue;
            }
            if (day.items().size() + 1 > DAY_LIMITS.maxItemsPerDay()) {
                continue;
            }
            int minutes = PlanValidator.dayMinutes(day, catalogById) + activity.durationMinutes()
                    + (day.items().isEmpty() ? 0 : PlanValidator.BUFFER_MINUTES);
            if (minutes > DAY_LIMITS.maxMinutesPerDay()) {
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

    /** A new item carries no start hint, no reason and no extras; the text refresh fills the reason in afterwards. */
    private static PlanDraft.DayDraft insert(PlanDraft.DayDraft day, CatalogActivity activity, Slot slot) {
        return insert(day, activity, slot, List.of());
    }

    private static PlanDraft.DayDraft insert(PlanDraft.DayDraft day, CatalogActivity activity, Slot slot,
            List<String> extraCodes) {
        List<PlanDraft.ItemDraft> items = new ArrayList<>(day.items());
        items.add(new PlanDraft.ItemDraft(slot, null, activity.id(), "", extraCodes));
        items.sort(Comparator.comparingInt(item -> item.slot() == null ? Integer.MAX_VALUE : item.slot().ordinal()));
        return new PlanDraft.DayDraft(day.dayNumber(), day.title(), day.summary(), items);
    }

    /**
     * A tap on a card names its row by id, so two catalog rows with one name are never ambiguous to it. Only
     * the snapshot answers: an id it lacks is unknown, never guessed from the name.
     */
    private static CatalogActivity byIdOrReject(EditRequest edit, WorkingCatalog catalog, List<RejectedEdit> rejected) {
        CatalogActivity activity = catalog.byId().get(edit.activityId());
        if (activity == null) {
            String cleaned = PlanAssembler.clean(edit.activity());
            rejected.add(new RejectedEdit(edit.op(), cleaned, null, EditRejectionReason.UNKNOWN_ACTIVITY, cleaned));
        }
        return activity;
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
                rejected.add(new RejectedEdit(edit.op(), cleaned, null, EditRejectionReason.UNKNOWN_ACTIVITY, cleaned,
                        offeredAlternatives(edit, catalog)));
                yield null;
            }
        };
    }

    /**
     * The model's alternatives for a name the catalog lacks, kept only where they resolve to a catalog row
     * themselves - a suggestion the organizer could not then ask for by name would be one more dead end -
     * and in the catalog's spelling, so that the next "add X" resolves exactly. Ambiguous ones are dropped
     * too: an offer that answers with "which one?" is not an offer.
     */
    private static List<String> offeredAlternatives(EditRequest edit, List<CatalogActivity> catalog) {
        Set<String> offered = new LinkedHashSet<>();
        for (String alternative : edit.alternatives()) {
            if (ActivityNameResolver.resolve(alternative, catalog) instanceof ActivityNameResolver.Found found) {
                offered.add(found.activity().name());
            }
        }
        return List.copyOf(offered);
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

    /** True when the package the edit names - or, naming none, every package - is without the activity. */
    private static boolean lacksActivity(PlanDraft working, EditRequest edit, CatalogActivity activity) {
        for (PlanDraft.PackageDraft pkg : working.packages()) {
            if ((edit.packageKey() == null || pkg.key() == edit.packageKey()) && contains(pkg, activity.id())) {
                return false;
            }
        }
        return true;
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

    /**
     * What one edit is about, resolved against the catalog once for every package it is aimed at: the
     * activity, the replacement of a {@code REPLACE} and the extra of an {@code ADD_EXTRA}/{@code REMOVE_EXTRA}
     * (null where the op has none).
     */
    private record Subjects(CatalogActivity activity, CatalogActivity replacement, CatalogExtra extra) {

        String replacementName() {
            return replacement == null ? null : replacement.name();
        }

        String extraName() {
            return extra == null ? null : extra.name();
        }

        BigDecimal extraPrice() {
            return extra == null ? null : extra.price();
        }

        ExtraPricing extraPricing() {
            return extra == null ? null : extra.pricing();
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
