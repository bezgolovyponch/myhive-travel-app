package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Checks a draft against the scheduling rules. Prices are not needed here; TIER_ORDER lives in PlanAssembler. */
@Component
public class PlanValidator {

    public static final int BUFFER_MINUTES = 30;
    public static final int TITLE_MAX = 60;
    public static final int TAGLINE_MAX = 120;
    public static final int DESCRIPTION_MAX = 600;
    public static final int WHY_MAX = 160;
    public static final int DAY_TITLE_MAX = 60;
    public static final int DAY_SUMMARY_MAX = 300;

    public List<Violation> validate(PlanDraft draft, Brief brief, Map<UUID, CatalogActivity> catalog) {
        List<Violation> violations = new ArrayList<>();
        Map<Tier, PlanDraft.PackageDraft> byTier = new HashMap<>();
        for (PlanDraft.PackageDraft p : draft.packages()) {
            if (p.key() != null) {
                byTier.put(p.key(), p);
            }
        }
        for (Tier tier : Tier.values()) {
            if (!byTier.containsKey(tier)) {
                violations.add(Violation.of(ViolationCode.MISSING_TIER, tier, null, "package " + tier + " is missing"));
            }
        }
        for (PlanDraft.PackageDraft p : byTier.values()) {
            violations.addAll(validatePackage(p, brief, catalog));
        }
        checkDistinct(byTier, violations);
        return violations;
    }

    /**
     * The per-package scheduling checks only (texts, day count, per-day slots/caps/duplicates, empty
     * day/package) — no {@code MISSING_TIER} and no cross-tier {@code TIER_NOT_DISTINCT}, so it is safe
     * to call on a single edited package without the rest of the plan. {@code validate} delegates here
     * per package to keep one implementation.
     */
    public List<Violation> validatePackage(PlanDraft.PackageDraft pkg, Brief brief, Map<UUID, CatalogActivity> catalog) {
        List<Violation> violations = new ArrayList<>();
        validatePackage(pkg, brief, catalog, violations);
        return violations;
    }

    private void validatePackage(PlanDraft.PackageDraft p, Brief brief, Map<UUID, CatalogActivity> catalog,
            List<Violation> out) {
        Tier tier = p.key();
        int days = brief.days();
        checkText(out, tier, null, "title", p.title(), TITLE_MAX);
        checkText(out, tier, null, "tagline", p.tagline(), TAGLINE_MAX);
        checkText(out, tier, null, "description", p.description(), DESCRIPTION_MAX);
        if (p.days().size() != days) {
            out.add(Violation.of(ViolationCode.WRONG_DAY_COUNT, tier, null,
                    "expected " + days + " days, got " + p.days().size()));
            return;
        }
        if (p.days().stream().allMatch(day -> day.items().isEmpty())) {
            // Every single day may legitimately be empty on its own (the edge days are exempt from
            // EMPTY_DAY), so a package with nothing in it at all has to be caught here or a EUR 0
            // itinerary ships as a finished package.
            out.add(Violation.of(ViolationCode.EMPTY_PACKAGE, tier, null, "package " + tier + " has no activities"));
        }
        Set<UUID> seen = new HashSet<>();
        for (PlanDraft.DayDraft day : p.days()) {
            validateDay(p, day, brief, catalog, seen, out);
        }
    }

    private void validateDay(PlanDraft.PackageDraft p, PlanDraft.DayDraft day, Brief brief,
            Map<UUID, CatalogActivity> catalog, Set<UUID> seen, List<Violation> out) {
        Tier tier = p.key();
        int n = day.dayNumber();
        checkText(out, tier, n, "dayTitle", day.title(), DAY_TITLE_MAX);
        checkText(out, tier, n, "daySummary", day.summary(), DAY_SUMMARY_MAX);
        boolean edgeDay = n == 1 || n == brief.days();
        if (day.items().isEmpty()) {
            if (!edgeDay) {
                out.add(Violation.of(ViolationCode.EMPTY_DAY, tier, n, "day " + n + " has no activities"));
            }
            return;
        }
        Set<Slot> allowed = allowedSlots(n, brief);
        Set<Slot> used = EnumSet.noneOf(Slot.class);
        int minutes = 0;
        for (PlanDraft.ItemDraft item : day.items()) {
            checkText(out, tier, n, "why", item.why(), WHY_MAX);
            CatalogActivity activity = item.activityId() == null ? null : catalog.get(item.activityId());
            if (activity == null) {
                out.add(Violation.of(ViolationCode.UNKNOWN_ACTIVITY, tier, n, "activityId " + item.activityId() + " is not in the catalog"));
                continue;
            }
            if (!seen.add(activity.id())) {
                out.add(Violation.of(ViolationCode.DUPLICATE_ACTIVITY, tier, n, activity.name() + " appears twice in " + tier));
            }
            if (item.slot() == null || !allowed.contains(item.slot())) {
                out.add(Violation.of(ViolationCode.SLOT_OUTSIDE_WINDOW, tier, n,
                        "slot " + item.slot() + " is outside the arrival/departure window on day " + n));
            } else if (!used.add(item.slot())) {
                out.add(Violation.of(ViolationCode.SLOT_TAKEN, tier, n, "two activities in slot " + item.slot() + " on day " + n));
            }
            minutes += activity.durationMinutes();
        }
        minutes += BUFFER_MINUTES * Math.max(0, day.items().size() - 1);
        if (day.items().size() > tier.maxItemsPerDay()) {
            out.add(Violation.of(ViolationCode.DAY_OVER_ITEMS, tier, n,
                    day.items().size() + " activities on day " + n + ", max " + tier.maxItemsPerDay() + " for " + tier));
        }
        if (minutes > tier.maxMinutesPerDay()) {
            out.add(Violation.of(ViolationCode.DAY_OVER_MINUTES, tier, n,
                    minutes + " minutes incl. buffers on day " + n + ", max " + tier.maxMinutesPerDay() + " for " + tier));
        }
    }

    /**
     * Day 1 opens at the arrival edge; the last day closes at the departure edge; a one-day trip uses
     * both — and that is where the two edges can cross. The defaults alone do it: a 1-day brief with no
     * stated edges arrives AFTERNOON and departs MORNING, which as a literal window is empty, puts every
     * activity of the day {@link ViolationCode#SLOT_OUTSIDE_WINDOW} and leaves three empty packages
     * (day 1 is an edge day, so {@link ViolationCode#EMPTY_DAY} never fires either). A crossed window is
     * read as "the group is here all day": it runs from the arrival edge to NIGHT.
     *
     * <p>Public because {@code ai.edit}'s package editor relies on it too, to keep an edited day's
     * slots inside the same arrival/departure window this validator enforces.
     */
    public static Set<Slot> allowedSlots(int dayNumber, Brief brief) {
        Slot first = dayNumber == 1 ? brief.arrivalOrDefault().slot() : Slot.MORNING;
        Slot last = dayNumber == brief.days() ? brief.departureOrDefault().slot() : Slot.NIGHT;
        if (last.ordinal() < first.ordinal()) {
            last = Slot.NIGHT;
        }
        Set<Slot> allowed = EnumSet.noneOf(Slot.class);
        for (Slot slot : Slot.values()) {
            if (slot.ordinal() >= first.ordinal() && slot.ordinal() <= last.ordinal()) {
                allowed.add(slot);
            }
        }
        return allowed;
    }

    private static void checkDistinct(Map<Tier, PlanDraft.PackageDraft> byTier, List<Violation> out) {
        if (byTier.size() < Tier.values().length) {
            return;
        }
        Map<Tier, Set<UUID>> ids = new HashMap<>();
        byTier.forEach((tier, p) -> ids.put(tier, activityIds(p)));
        for (Tier tier : Tier.values()) {
            Set<UUID> unique = new HashSet<>(ids.get(tier));
            for (Tier other : Tier.values()) {
                if (other != tier) {
                    unique.removeAll(ids.get(other));
                }
            }
            if (unique.isEmpty()) {
                out.add(Violation.of(ViolationCode.TIER_NOT_DISTINCT, tier, null,
                        tier + " has no activity that the other tiers lack"));
            }
        }
    }

    static Set<UUID> activityIds(PlanDraft.PackageDraft p) {
        Set<UUID> ids = new HashSet<>();
        for (PlanDraft.DayDraft day : p.days()) {
            for (PlanDraft.ItemDraft item : day.items()) {
                if (item.activityId() != null) {
                    ids.add(item.activityId());
                }
            }
        }
        return ids;
    }

    private static void checkText(List<Violation> out, Tier tier, Integer day, String field, String value, int max) {
        if (value != null && value.length() > max) {
            out.add(Violation.of(ViolationCode.TEXT_TOO_LONG, tier, day, field + " is " + value.length() + " chars, max " + max));
        }
    }
}
