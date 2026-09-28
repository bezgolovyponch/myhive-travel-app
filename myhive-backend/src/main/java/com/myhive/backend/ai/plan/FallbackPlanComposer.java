package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.util.Translations;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Greedy composer used when the model fails twice. Reserves one exclusive "signature" activity per tier,
 * ranked by the actual billed line ({@link PlanPricer#lineTotal}, which floors on {@code minPrice}) so
 * signature prices are strictly ascending BASIC &lt; MEDIUM &lt; PREMIUM and each tier owns at least one
 * activity the other two cannot reach (satisfies TIER_NOT_DISTINCT). A signature is only ever handed to a
 * tier whose {@code maxMinutesPerDay} it fits, so it can never be silently dropped while filling. The
 * remaining catalog (brief-category matches preferred, then cheapest billed line first) is shared by every
 * tier and filled in two passes per package: first give every day one item so no middle day is left empty,
 * then top up each day toward the tier's cap. Rejected candidates are never discarded — they stay available
 * for a later day or a later pass. Deterministic: no randomness, ties break on activity name.
 */
@Component
public class FallbackPlanComposer {

    private static final Map<String, String[]> TITLES = Map.of(
            "en", new String[] {"Warm-up", "Main Event", "Full Send"},
            "de", new String[] {"Warm-up", "Hauptprogramm", "Volle Kanne"});
    private static final Map<String, String> DAY_LABEL = Map.of("en", "Day", "de", "Tag");
    private static final int DEFAULT_TRAVELERS = 1;

    public PlanDraft compose(Brief brief, List<CatalogActivity> catalog, String locale) {
        if (brief.days() == null) {
            throw new IllegalArgumentException("brief.days must be set before composing a fallback plan");
        }
        int travelers = brief.groupSize() == null ? DEFAULT_TRAVELERS : brief.groupSize();
        Set<String> wanted = new HashSet<>(brief.categorySlugs());

        Tier[] tiers = Tier.values();
        CatalogActivity[] signatures = reserveSignatures(catalog, wanted, travelers, tiers);
        List<CatalogActivity> shared = sharedPool(catalog, wanted, travelers, signatures);

        String normalizedLocale = Translations.normalize(locale);
        String titleKey = normalizedLocale == null ? "en" : normalizedLocale;
        String[] titles = TITLES.getOrDefault(titleKey, TITLES.get("en"));
        String dayLabel = DAY_LABEL.getOrDefault(titleKey, DAY_LABEL.get("en"));

        List<PlanDraft.PackageDraft> packages = new ArrayList<>();
        for (int t = 0; t < tiers.length; t++) {
            List<CatalogActivity> pool = new ArrayList<>();
            if (signatures[t] != null) {
                pool.add(signatures[t]);
            }
            pool.addAll(shared);
            packages.add(fill(tiers[t], titles[t], dayLabel, brief, pool));
        }
        return new PlanDraft(packages);
    }

    /**
     * Gives the priciest tier the priciest exclusive activity that still fits its own minute cap, working
     * down: PREMIUM picks first, then MEDIUM, then BASIC, each taking the priciest not-yet-reserved
     * candidate whose duration fits {@code tier.maxMinutesPerDay()}. Ranking is purely by billed line total
     * ({@link PlanPricer#lineTotal}, which accounts for {@code minPrice} floors); brief-category match is
     * only a tie-break, never the primary key, so a cheap activity with a large group minimum can still be
     * recognised as the most expensive line. A tier whose cap no candidate fits is left without a signature
     * rather than throwing.
     */
    private static CatalogActivity[] reserveSignatures(List<CatalogActivity> catalog, Set<String> wanted,
            int travelers, Tier[] tiers) {
        List<CatalogActivity> byBilledLineDescending = new ArrayList<>(catalog);
        byBilledLineDescending.sort(Comparator
                .comparing((CatalogActivity a) -> billedLine(a, travelers))
                .reversed()
                .thenComparing((CatalogActivity a) -> matches(a, wanted) ? 0 : 1)
                .thenComparing(CatalogActivity::name));

        CatalogActivity[] signatures = new CatalogActivity[tiers.length];
        Set<UUID> reserved = new HashSet<>();
        for (int t = tiers.length - 1; t >= 0; t--) {
            Tier tier = tiers[t];
            for (CatalogActivity candidate : byBilledLineDescending) {
                if (!reserved.contains(candidate.id()) && candidate.durationMinutes() <= tier.maxMinutesPerDay()) {
                    signatures[t] = candidate;
                    reserved.add(candidate.id());
                    break;
                }
            }
        }
        return signatures;
    }

    /**
     * Everything not reserved as a signature: brief-category matches first (as a filling preference only),
     * then cheapest billed line within each group, shared by every tier.
     */
    private static List<CatalogActivity> sharedPool(List<CatalogActivity> catalog, Set<String> wanted, int travelers,
            CatalogActivity[] signatures) {
        Set<UUID> signatureIds = new HashSet<>();
        for (CatalogActivity signature : signatures) {
            if (signature != null) {
                signatureIds.add(signature.id());
            }
        }
        List<CatalogActivity> matched = new ArrayList<>();
        List<CatalogActivity> unmatched = new ArrayList<>();
        for (CatalogActivity activity : catalog) {
            if (signatureIds.contains(activity.id())) {
                continue;
            }
            (matches(activity, wanted) ? matched : unmatched).add(activity);
        }
        Comparator<CatalogActivity> byBilledLineThenName = Comparator
                .comparing((CatalogActivity a) -> billedLine(a, travelers))
                .thenComparing(CatalogActivity::name);
        matched.sort(byBilledLineThenName);
        unmatched.sort(byBilledLineThenName);

        List<CatalogActivity> shared = new ArrayList<>(matched);
        shared.addAll(unmatched);
        return shared;
    }

    private static BigDecimal billedLine(CatalogActivity activity, int travelers) {
        return PlanPricer.lineTotal(activity.price(), activity.minPrice(), travelers);
    }

    /**
     * Two passes per package: first walk every day once so no day is skipped entirely (pass 1), then walk
     * the days again topping each one up toward the tier's item/minute caps (pass 2). A rejected candidate
     * is left in {@code remaining} rather than discarded, so an over-long activity can no longer drain the
     * pool and starve later days.
     */
    private static PlanDraft.PackageDraft fill(Tier tier, String title, String dayLabel, Brief brief,
            List<CatalogActivity> pool) {
        int dayCount = brief.days();
        List<List<PlanDraft.ItemDraft>> itemsPerDay = new ArrayList<>();
        List<Set<Slot>> usedSlotsPerDay = new ArrayList<>();
        List<Set<Slot>> allowedPerDay = new ArrayList<>();
        int[] minutesPerDay = new int[dayCount];
        for (int day = 1; day <= dayCount; day++) {
            itemsPerDay.add(new ArrayList<>());
            usedSlotsPerDay.add(EnumSet.noneOf(Slot.class));
            allowedPerDay.add(PlanValidator.allowedSlots(day, brief));
        }
        List<CatalogActivity> remaining = new ArrayList<>(pool);

        for (int d = 0; d < dayCount; d++) {
            placeOne(tier, allowedPerDay.get(d), itemsPerDay.get(d), usedSlotsPerDay.get(d), minutesPerDay, d, remaining);
        }
        for (int d = 0; d < dayCount; d++) {
            while (itemsPerDay.get(d).size() < tier.maxItemsPerDay()
                    && placeOne(tier, allowedPerDay.get(d), itemsPerDay.get(d), usedSlotsPerDay.get(d), minutesPerDay, d, remaining)) {
                // keep topping this day up until the cap, the minute budget, or the pool is exhausted
            }
        }

        List<PlanDraft.DayDraft> days = new ArrayList<>();
        for (int day = 1; day <= dayCount; day++) {
            days.add(new PlanDraft.DayDraft(day, dayLabel + " " + day, null, itemsPerDay.get(day - 1)));
        }
        return new PlanDraft.PackageDraft(tier, title, null, null, days);
    }

    /** Places at most one activity into the next open slot of one day; returns false without mutating anything if it can't. */
    private static boolean placeOne(Tier tier, Set<Slot> allowedSlots, List<PlanDraft.ItemDraft> items,
            Set<Slot> usedSlots, int[] minutesPerDay, int dayIndex, List<CatalogActivity> remaining) {
        if (items.size() >= tier.maxItemsPerDay()) {
            return false;
        }
        Slot openSlot = null;
        for (Slot slot : allowedSlots) {
            if (!usedSlots.contains(slot)) {
                openSlot = slot;
                break;
            }
        }
        if (openSlot == null) {
            return false;
        }
        int buffer = items.isEmpty() ? 0 : PlanValidator.BUFFER_MINUTES;
        int budget = tier.maxMinutesPerDay() - minutesPerDay[dayIndex] - buffer;
        if (budget < 0) {
            return false;
        }
        for (int i = 0; i < remaining.size(); i++) {
            CatalogActivity candidate = remaining.get(i);
            if (candidate.durationMinutes() <= budget) {
                remaining.remove(i);
                usedSlots.add(openSlot);
                minutesPerDay[dayIndex] += candidate.durationMinutes() + buffer;
                items.add(new PlanDraft.ItemDraft(openSlot, null, candidate.id(), null));
                return true;
            }
        }
        return false;
    }

    private static boolean matches(CatalogActivity activity, Set<String> wanted) {
        if (wanted.isEmpty()) {
            return true;
        }
        for (String slug : activity.categorySlugs()) {
            if (wanted.contains(slug)) {
                return true;
            }
        }
        return false;
    }
}
