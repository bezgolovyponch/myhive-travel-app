package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.llm.LlmGateway;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.llm.PackageTexts;
import com.myhive.backend.ai.llm.TextRefreshRequest;
import com.myhive.backend.ai.llm.TextRefreshResult;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Rewrites the copy of the packages an edit just touched. Best effort by design: the edit has already
 * been applied and priced when this runs, so a model outage or a garbled answer only leaves the previous
 * texts in place. Nothing but description, why and day summary is ever written.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TextRefresher {

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
        List<ComposedPlan.PackageResult> edited = plan.packages().stream()
                .filter(p -> touchedDays.containsKey(p.key()))
                .toList();
        TextRefreshResult result;
        try {
            result = gateway.refreshTexts(new TextRefreshRequest(locale, destinationName, edited, newActivityIds,
                    touchedDays));
        } catch (RuntimeException e) {
            // The edit already succeeded and the texts are only cosmetic, so a failed refresh must never
            // fail the edit: the plan keeps the copy it had.
            log.warn("text refresh failed cause={}", e.getClass().getSimpleName());
            return notRefreshed(plan);
        }
        return new Refreshed(rebuild(plan, result.texts(), newActivityIds, touchedDays), true, result.usage());
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
            String summary = touchedDays.contains(day.dayNumber())
                    ? accepted(texts.summaryByDay().get(day.dayNumber()), PlanValidator.DAY_SUMMARY_MAX, day.summary())
                    : day.summary();
            List<ComposedPlan.ItemResult> items = new ArrayList<>(day.items().size());
            for (ComposedPlan.ItemResult item : day.items()) {
                String why = newActivityIds.contains(item.activityId())
                        ? accepted(texts.whyByActivityId().get(item.activityId()), PlanValidator.WHY_MAX, item.why())
                        : item.why();
                items.add(withWhy(item, why));
            }
            days.add(new ComposedPlan.DayResult(day.dayNumber(), day.title(), summary, items));
        }
        return new ComposedPlan.PackageResult(p.key(), p.title(), p.tagline(),
                accepted(texts.description(), PlanValidator.DESCRIPTION_MAX, p.description()), p.pricePerPerson(),
                p.totalPrice(), p.currency(), p.totalDurationMinutes(), p.activityIds(), days);
    }

    /** Model copy is taken only when it survives cleaning; it is then cut to the cap the validator enforces. */
    private static String accepted(String candidate, int max, String previous) {
        if (candidate == null) {
            return previous;
        }
        String cleaned = PlanAssembler.clean(candidate);
        if (cleaned == null || cleaned.isBlank()) {
            return previous;
        }
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max).strip();
    }

    private static ComposedPlan.ItemResult withWhy(ComposedPlan.ItemResult item, String why) {
        if (Objects.equals(why, item.why())) {
            return item;
        }
        return new ComposedPlan.ItemResult(item.slot(), item.startHint(), item.activityId(), item.slug(), item.name(),
                item.imageUrl(), item.durationMinutes(), item.price(), item.minPrice(), item.lineTotal(),
                item.groupMinApplied(), why);
    }
}
