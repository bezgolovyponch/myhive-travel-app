package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Greedy composer used when the model fails twice. Ranks the catalog by brief-category match then price,
 * reserves one exclusive "signature" activity per tier (pricier tiers get pricier signatures) so every
 * tier stays distinct, then fills each day slot by slot from the shared, cheapest-first remainder using
 * that tier's item/minute caps. Deterministic: no randomness, ties break on activity name.
 */
@Component
public class FallbackPlanComposer {

    private static final Map<String, String[]> TITLES = Map.of(
            "en", new String[] {"Warm-up", "Main Event", "Full Send"},
            "de", new String[] {"Warm-up", "Hauptprogramm", "Volle Kanne"});

    public PlanDraft compose(Brief brief, List<CatalogActivity> catalog, String locale) {
        Set<String> wanted = new HashSet<>(brief.categorySlugs());
        List<CatalogActivity> matched = new ArrayList<>();
        List<CatalogActivity> unmatched = new ArrayList<>();
        for (CatalogActivity activity : catalog) {
            if (matches(activity, wanted)) {
                matched.add(activity);
            } else {
                unmatched.add(activity);
            }
        }
        Comparator<CatalogActivity> byPriceThenName = Comparator.comparing(CatalogActivity::price)
                .thenComparing(CatalogActivity::name);
        matched.sort(byPriceThenName);
        unmatched.sort(byPriceThenName);

        Tier[] tiers = Tier.values();
        CatalogActivity[] signatures = reserveSignatures(matched, unmatched, tiers.length);
        List<CatalogActivity> shared = sharedPool(matched, unmatched, signatures);

        String[] titles = TITLES.getOrDefault(locale == null ? "en" : locale, TITLES.get("en"));
        List<PlanDraft.PackageDraft> packages = new ArrayList<>();
        for (int t = 0; t < tiers.length; t++) {
            List<CatalogActivity> pool = new ArrayList<>();
            if (signatures[t] != null) {
                pool.add(signatures[t]);
            }
            pool.addAll(shared);
            packages.add(fill(tiers[t], titles[t], brief, pool));
        }
        return new PlanDraft(packages);
    }

    /**
     * Gives the priciest tier the priciest exclusive activity, working down: PREMIUM's signature is the
     * priciest brief-category match (falling back to the priciest non-matching activity once category
     * matches run out), MEDIUM the next, BASIC the next after that. This alone satisfies TIER_NOT_DISTINCT
     * and keeps signature prices strictly ascending BASIC < MEDIUM < PREMIUM, anchoring the per-person price
     * ordering PlanAssembler requires.
     */
    private static CatalogActivity[] reserveSignatures(List<CatalogActivity> matched, List<CatalogActivity> unmatched,
            int tierCount) {
        List<CatalogActivity> byPreferenceDescending = new ArrayList<>(matched);
        Collections.reverse(byPreferenceDescending);
        List<CatalogActivity> unmatchedDescending = new ArrayList<>(unmatched);
        Collections.reverse(unmatchedDescending);
        byPreferenceDescending.addAll(unmatchedDescending);

        CatalogActivity[] signatures = new CatalogActivity[tierCount];
        int taken = 0;
        for (int t = tierCount - 1; t >= 0 && taken < byPreferenceDescending.size(); t--) {
            signatures[t] = byPreferenceDescending.get(taken);
            taken++;
        }
        return signatures;
    }

    /** Everything not reserved as a signature, still ordered category-preferred-then-cheapest-first, shared by every tier. */
    private static List<CatalogActivity> sharedPool(List<CatalogActivity> matched, List<CatalogActivity> unmatched,
            CatalogActivity[] signatures) {
        Set<UUID> signatureIds = new HashSet<>();
        for (CatalogActivity signature : signatures) {
            if (signature != null) {
                signatureIds.add(signature.id());
            }
        }
        List<CatalogActivity> shared = new ArrayList<>();
        addUnlessSignature(matched, signatureIds, shared);
        addUnlessSignature(unmatched, signatureIds, shared);
        return shared;
    }

    private static void addUnlessSignature(List<CatalogActivity> source, Set<UUID> signatureIds,
            List<CatalogActivity> target) {
        for (CatalogActivity activity : source) {
            if (!signatureIds.contains(activity.id())) {
                target.add(activity);
            }
        }
    }

    private static PlanDraft.PackageDraft fill(Tier tier, String title, Brief brief, List<CatalogActivity> pool) {
        List<PlanDraft.DayDraft> days = new ArrayList<>();
        Set<UUID> used = new HashSet<>();
        int cursor = 0;
        for (int day = 1; day <= brief.days(); day++) {
            List<PlanDraft.ItemDraft> items = new ArrayList<>();
            int minutes = 0;
            for (Slot slot : PlanValidator.allowedSlots(day, brief)) {
                if (items.size() >= tier.maxItemsPerDay()) {
                    break;
                }
                CatalogActivity next = null;
                while (cursor < pool.size()) {
                    CatalogActivity candidate = pool.get(cursor++);
                    int projected = minutes + candidate.durationMinutes() + (items.isEmpty() ? 0 : PlanValidator.BUFFER_MINUTES);
                    if (!used.contains(candidate.id()) && projected <= tier.maxMinutesPerDay()) {
                        next = candidate;
                        minutes = projected;
                        break;
                    }
                }
                if (next == null) {
                    break;
                }
                used.add(next.id());
                items.add(new PlanDraft.ItemDraft(slot, null, next.id(), null));
            }
            days.add(new PlanDraft.DayDraft(day, "Day " + day, null, items));
        }
        return new PlanDraft.PackageDraft(tier, title, null, null, days);
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
