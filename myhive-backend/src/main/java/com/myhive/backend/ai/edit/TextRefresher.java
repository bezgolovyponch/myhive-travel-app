package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.llm.LlmGateway;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.llm.PackageTexts;
import com.myhive.backend.ai.llm.TextRefreshRequest;
import com.myhive.backend.ai.llm.TextRefreshResult;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlaceholderTexts;
import com.myhive.backend.ai.plan.PlanTextWriter;
import com.myhive.backend.ai.plan.PlanValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rewrites the copy of the packages an edit just touched. Best effort by design: the edit has already
 * been applied and priced when this runs, so a model outage or a garbled answer only leaves the previous
 * texts in place. Nothing but text is ever written: the package title, tagline and description, and the
 * title and summary of a touched day, plus the "why" of what was placed.
 *
 * <p>The one thing that is not left to the model: a name that still mentions an activity the edit took
 * out. Titles are written from the activities ("Karting · VIP Club", "VIP night"), so after a removal or a
 * swap they advertise what is no longer there; whatever the model answered, or if it did not answer at
 * all, such a title falls back to its stock name ({@link PlaceholderTexts}) and such a tagline is dropped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TextRefresher {

    /**
     * Words too generic to tie a title to one activity: "Night" in "Nightclub VIP Experience" says nothing
     * about "Evening · Night out". Lower case, English and German.
     */
    private static final Set<String> GENERIC_WORDS = Set.of("the", "and", "with", "for", "experience", "tour",
            "night", "nights", "day", "days", "party", "package", "trip", "und", "mit", "der", "die", "das",
            "für", "nacht", "tag", "paket");
    /** Below this, a word only counts as the same word when it is equal ("VIP"), never as a part of one. */
    private static final int MIN_PARTIAL_WORD = 4;
    private static final int MIN_WORD = 3;

    private final LlmGateway gateway;

    /** The plan to serve plus whether its texts actually came back rewritten, for the caller's report. */
    public record Refreshed(ComposedPlan plan, boolean refreshed, LlmUsage usage) {
    }

    public Refreshed refresh(ComposedPlan plan, EditOutcome outcome, String locale, String destinationName) {
        if (!outcome.anyApplied()) {
            return notRefreshed(plan);
        }
        Map<Tier, Set<UUID>> newActivityIds = new EnumMap<>(Tier.class);
        Map<Tier, Set<Integer>> touchedDays = new EnumMap<>(Tier.class);
        for (AppliedEdit applied : outcome.applied()) {
            touchedDays.computeIfAbsent(applied.packageKey(), key -> new LinkedHashSet<>()).add(applied.dayNumber());
            Set<UUID> ids = newActivityIds.computeIfAbsent(applied.packageKey(), key -> new LinkedHashSet<>());
            if (applied.placedActivityId() != null) {
                // REMOVE places nothing: the day summary still goes stale, but no "why" has to be written.
                ids.add(applied.placedActivityId());
            }
        }
        Map<Tier, Set<String>> removedNames = removedNames(outcome);
        List<ComposedPlan.PackageResult> edited = plan.packages().stream()
                .filter(p -> touchedDays.containsKey(p.key()))
                .toList();
        TextRefreshResult result;
        try {
            result = gateway.refreshTexts(new TextRefreshRequest(locale, destinationName, edited, newActivityIds,
                    touchedDays));
        } catch (RuntimeException e) {
            // The edit already succeeded and the texts are only cosmetic, so a failed refresh must never
            // fail the edit: the plan keeps the copy it had - minus the names that are now wrong.
            log.warn("text refresh failed cause={}", e.getClass().getSimpleName());
            return new Refreshed(withoutStaleNames(plan, removedNames, locale), false, LlmUsage.none());
        }
        ComposedPlan rebuilt = rebuild(plan, result.texts(), newActivityIds, touchedDays);
        return new Refreshed(withoutStaleNames(rebuilt, removedNames, locale), true, result.usage());
    }

    /** Per package, the catalog names the batch took out of it: removed ones and the swapped-out side of a swap. */
    private static Map<Tier, Set<String>> removedNames(EditOutcome outcome) {
        Map<Tier, Set<String>> removed = new EnumMap<>(Tier.class);
        for (AppliedEdit applied : outcome.applied()) {
            if ((applied.op() == EditOp.REMOVE || applied.op() == EditOp.REPLACE) && applied.activityName() != null) {
                removed.computeIfAbsent(applied.packageKey(), key -> new LinkedHashSet<>()).add(applied.activityName());
            }
        }
        return removed;
    }

    /**
     * Every package title, tagline and day title that still names an activity the batch removed falls back:
     * the title to the tier's stock name, a day title to "Day N", a tagline to none. Returns the plan itself
     * when nothing was stale, so an edit that removed nothing serves exactly what it was given.
     */
    private static ComposedPlan withoutStaleNames(ComposedPlan plan, Map<Tier, Set<String>> removedNames,
                                                  String locale) {
        if (removedNames.isEmpty()) {
            return plan;
        }
        boolean changed = false;
        List<ComposedPlan.PackageResult> packages = new ArrayList<>(plan.packages().size());
        for (ComposedPlan.PackageResult p : plan.packages()) {
            Set<String> staleWords = staleWords(removedNames.getOrDefault(p.key(), Set.of()), p);
            if (staleWords.isEmpty()) {
                packages.add(p);
                continue;
            }
            List<ComposedPlan.DayResult> days = new ArrayList<>(p.days().size());
            for (ComposedPlan.DayResult day : p.days()) {
                days.add(mentionsAny(day.title(), staleWords)
                        ? new ComposedPlan.DayResult(day.dayNumber(), PlaceholderTexts.dayTitle(day.dayNumber(), locale),
                                day.summary(), day.items())
                        : day);
            }
            String title = mentionsAny(p.title(), staleWords) ? PlaceholderTexts.packageTitle(p.key(), locale) : p.title();
            String tagline = mentionsAny(p.tagline(), staleWords) ? null : p.tagline();
            ComposedPlan.PackageResult cleaned = PlanTextWriter.withTexts(p, title, tagline, p.description(), days);
            if (!cleaned.equals(p)) {
                log.info("text refresh dropped stale names package={}", p.key());
                changed = true;
            }
            packages.add(cleaned);
        }
        return changed ? new ComposedPlan(packages, plan.degraded()) : plan;
    }

    /**
     * The words of the removed names that no activity still in the package carries: removing "Beer Bike"
     * next to "Beer Spa" makes "Bike" stale, never "Beer".
     */
    private static Set<String> staleWords(Set<String> removedNames, ComposedPlan.PackageResult p) {
        Set<String> stale = new LinkedHashSet<>();
        for (String name : removedNames) {
            stale.addAll(words(name));
        }
        if (stale.isEmpty()) {
            return stale;
        }
        Set<String> kept = new HashSet<>();
        for (ComposedPlan.DayResult day : p.days()) {
            for (ComposedPlan.ItemResult item : day.items()) {
                kept.addAll(words(item.name()));
            }
        }
        stale.removeAll(kept);
        return stale;
    }

    /** Whether a text names one of the words: equal, or - for words long enough - one inside the other ("Club"/"Nightclub"). */
    private static boolean mentionsAny(String text, Set<String> staleWords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String word : words(text)) {
            for (String stale : staleWords) {
                if (word.equals(stale) || (word.length() >= MIN_PARTIAL_WORD && stale.length() >= MIN_PARTIAL_WORD
                        && (word.contains(stale) || stale.contains(word)))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> words(String text) {
        Set<String> words = new LinkedHashSet<>();
        if (text == null) {
            return words;
        }
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= MIN_WORD && !GENERIC_WORDS.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    private static Refreshed notRefreshed(ComposedPlan plan) {
        return new Refreshed(plan, false, LlmUsage.none());
    }

    private static ComposedPlan rebuild(ComposedPlan plan, Map<Tier, PackageTexts> texts,
                                        Map<Tier, Set<UUID>> newActivityIds, Map<Tier, Set<Integer>> touchedDays) {
        List<ComposedPlan.PackageResult> packages = new ArrayList<>(plan.packages().size());
        for (ComposedPlan.PackageResult p : plan.packages()) {
            PackageTexts packageTexts = texts.get(p.key());
            if (packageTexts == null || !touchedDays.containsKey(p.key())) {
                // Untouched by the edit, or unanswered by the model: keep the package exactly as it is.
                packages.add(p);
                continue;
            }
            packages.add(rebuildPackage(p, packageTexts, newActivityIds.getOrDefault(p.key(), Set.of()),
                    touchedDays.getOrDefault(p.key(), Set.of())));
        }
        return new ComposedPlan(packages, plan.degraded());
    }

    private static ComposedPlan.PackageResult rebuildPackage(ComposedPlan.PackageResult p, PackageTexts texts,
                                                             Set<UUID> newActivityIds, Set<Integer> touchedDays) {
        List<ComposedPlan.DayResult> days = new ArrayList<>(p.days().size());
        for (ComposedPlan.DayResult day : p.days()) {
            boolean touched = touchedDays.contains(day.dayNumber());
            String title = touched
                    ? PlanTextWriter.accepted(texts.titleByDay().get(day.dayNumber()), PlanValidator.DAY_TITLE_MAX,
                            day.title())
                    : day.title();
            String summary = touched
                    ? PlanTextWriter.accepted(texts.summaryByDay().get(day.dayNumber()), PlanValidator.DAY_SUMMARY_MAX,
                            day.summary())
                    : day.summary();
            List<ComposedPlan.ItemResult> items = new ArrayList<>(day.items().size());
            for (ComposedPlan.ItemResult item : day.items()) {
                String why = newActivityIds.contains(item.activityId())
                        ? PlanTextWriter.accepted(texts.whyByActivityId().get(item.activityId()), PlanValidator.WHY_MAX,
                                item.why())
                        : item.why();
                items.add(PlanTextWriter.withWhy(item, why));
            }
            days.add(new ComposedPlan.DayResult(day.dayNumber(), title, summary, items));
        }
        return PlanTextWriter.withTexts(p, PlanTextWriter.accepted(texts.title(), PlanValidator.TITLE_MAX, p.title()),
                PlanTextWriter.accepted(texts.tagline(), PlanValidator.TAGLINE_MAX, p.tagline()),
                PlanTextWriter.accepted(texts.description(), PlanValidator.DESCRIPTION_MAX, p.description()), days);
    }
}
