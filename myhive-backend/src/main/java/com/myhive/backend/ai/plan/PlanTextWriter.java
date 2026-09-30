package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.llm.LlmGateway;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.llm.PackageTexts;
import com.myhive.backend.ai.llm.PlanTextsRequest;
import com.myhive.backend.ai.llm.PlanTextsResult;
import com.myhive.backend.ai.model.Brief;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Writes the copy of a freshly composed plan - package titles, taglines and descriptions, day titles and
 * summaries, item whys - after the structure is validated and priced: one chat-model call per package,
 * the three in parallel. Best effort by design: the packages are already good when this runs, so a model
 * outage or a garbled answer only leaves that package's placeholders in place. Nothing but text is ever
 * written; ids, slots and prices are untouched.
 */
@Component
@Slf4j
public class PlanTextWriter {

    private final LlmGateway gateway;
    private final Executor executor;

    @Autowired
    public PlanTextWriter(LlmGateway gateway, @Qualifier("planTextsExecutor") Executor executor) {
        this.gateway = gateway;
        this.executor = executor;
    }

    /** Sequential, for callers that do not manage an executor - tests above all. */
    public PlanTextWriter(LlmGateway gateway) {
        this(gateway, Runnable::run);
    }

    /** The plan to serve plus whether any copy came from the model, and the accounting for the calls. */
    public record Written(ComposedPlan plan, boolean written, LlmUsage usage) {
    }

    /** What one call came back with: its package, the copy (null when the call failed or named another package), the cost. */
    private record PackageCopy(ComposedPlan.PackageResult pkg, PackageTexts texts, LlmUsage usage) {
    }

    /**
     * Titles nobody wrote yet - the tier's stock name and "Day N" - filled in where they are missing and
     * left alone where they exist. This is what the skeleton is published with, and what the model's
     * answer is merged onto, so a package the model skipped still has a name.
     */
    public static ComposedPlan placeholders(ComposedPlan plan, String locale) {
        List<ComposedPlan.PackageResult> packages = new ArrayList<>(plan.packages().size());
        for (ComposedPlan.PackageResult p : plan.packages()) {
            List<ComposedPlan.DayResult> days = new ArrayList<>(p.days().size());
            for (ComposedPlan.DayResult day : p.days()) {
                days.add(isBlank(day.title())
                        ? new ComposedPlan.DayResult(day.dayNumber(), PlaceholderTexts.dayTitle(day.dayNumber(), locale),
                                day.summary(), day.items())
                        : day);
            }
            String title = isBlank(p.title()) ? PlaceholderTexts.packageTitle(p.key(), locale) : p.title();
            packages.add(withTexts(p, title, p.tagline(), p.description(), days));
        }
        return new ComposedPlan(packages, plan.degraded());
    }

    /**
     * One call per package, in parallel: three packages of copy in one answer took longer than the whole
     * generation used to, one package is a third of that. Each package also fails on its own - the one
     * the model could not write keeps its placeholders while the others get their copy.
     */
    public Written write(ComposedPlan plan, Brief brief, String locale, String destinationName,
                         List<CatalogActivity> catalog) {
        ComposedPlan base = placeholders(plan, locale);
        List<CompletableFuture<PackageCopy>> pending = new ArrayList<>(base.packages().size());
        for (ComposedPlan.PackageResult p : base.packages()) {
            pending.add(CompletableFuture.supplyAsync(
                    () -> writeOne(p, base.degraded(), brief, locale, destinationName, catalog), executor));
        }
        List<ComposedPlan.PackageResult> packages = new ArrayList<>(base.packages().size());
        boolean written = false;
        LlmUsage usage = LlmUsage.none();
        for (CompletableFuture<PackageCopy> future : pending) {
            PackageCopy copy = future.join();
            packages.add(copy.texts() == null ? copy.pkg() : mergePackage(copy.pkg(), copy.texts()));
            written |= copy.texts() != null;
            usage = usage.plus(copy.usage());
        }
        return new Written(new ComposedPlan(packages, base.degraded()), written, usage);
    }

    private PackageCopy writeOne(ComposedPlan.PackageResult p, boolean degraded, Brief brief, String locale,
                                 String destinationName, List<CatalogActivity> catalog) {
        PlanTextsResult result;
        try {
            result = gateway.writeTexts(new PlanTextsRequest(locale, destinationName, brief,
                    new ComposedPlan(List.of(p), degraded), catalog));
        } catch (RuntimeException e) {
            // The package is valid and priced already; copy is cosmetic, so a failed call ships its
            // placeholders rather than costing the generation.
            log.warn("plan texts failed package={} cause={}", p.key(), e.getClass().getSimpleName());
            return new PackageCopy(p, null, LlmUsage.none());
        }
        PackageTexts texts = result.texts().get(p.key());
        if (texts == null) {
            log.warn("plan texts answer did not name package={}; keeping its placeholders", p.key());
        }
        return new PackageCopy(p, texts, result.usage());
    }

    private static ComposedPlan.PackageResult mergePackage(ComposedPlan.PackageResult p, PackageTexts t) {
        List<ComposedPlan.DayResult> days = new ArrayList<>(p.days().size());
        for (ComposedPlan.DayResult day : p.days()) {
            List<ComposedPlan.ItemResult> items = new ArrayList<>(day.items().size());
            for (ComposedPlan.ItemResult item : day.items()) {
                items.add(withWhy(item,
                        accepted(t.whyByActivityId().get(item.activityId()), PlanValidator.WHY_MAX, item.why())));
            }
            days.add(new ComposedPlan.DayResult(day.dayNumber(),
                    accepted(t.titleByDay().get(day.dayNumber()), PlanValidator.DAY_TITLE_MAX, day.title()),
                    accepted(t.summaryByDay().get(day.dayNumber()), PlanValidator.DAY_SUMMARY_MAX, day.summary()),
                    items));
        }
        return withTexts(p, accepted(t.title(), PlanValidator.TITLE_MAX, p.title()),
                accepted(t.tagline(), PlanValidator.TAGLINE_MAX, p.tagline()),
                accepted(t.description(), PlanValidator.DESCRIPTION_MAX, p.description()), days);
    }

    /** Model copy is taken only when it survives cleaning; it is then cut to the cap the validator enforces. */
    public static String accepted(String candidate, int max, String previous) {
        if (candidate == null) {
            return previous;
        }
        String cleaned = PlanAssembler.clean(candidate);
        if (cleaned == null || cleaned.isBlank()) {
            return previous;
        }
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max).strip();
    }

    public static ComposedPlan.ItemResult withWhy(ComposedPlan.ItemResult item, String why) {
        if (Objects.equals(why, item.why())) {
            return item;
        }
        return new ComposedPlan.ItemResult(item.slot(), item.startHint(), item.activityId(), item.slug(), item.name(),
                item.imageUrl(), item.durationMinutes(), item.price(), item.minPrice(), item.lineTotal(),
                item.groupMinApplied(), why, item.includes());
    }

    /** The package with its texts and days replaced; prices, ids and duration are copied through untouched. */
    public static ComposedPlan.PackageResult withTexts(ComposedPlan.PackageResult p, String title, String tagline,
            String description, List<ComposedPlan.DayResult> days) {
        return new ComposedPlan.PackageResult(p.key(), title, tagline, description, p.pricePerPerson(), p.totalPrice(),
                p.currency(), p.totalDurationMinutes(), p.activityIds(), days, p.nights());
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
