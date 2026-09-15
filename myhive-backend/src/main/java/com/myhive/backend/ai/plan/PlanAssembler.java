package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Turns a validated draft into priced packages; prices and names always come from the catalog, never the model. */
@Component
public class PlanAssembler {

    private static final Pattern SCRIPT_OR_STYLE_BLOCK = Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1>");
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    public record AssemblyResult(ComposedPlan plan, List<Violation> violations) {
    }

    public AssemblyResult assemble(PlanDraft draft, Brief brief, Map<UUID, CatalogActivity> catalog, boolean degraded) {
        int travelers = brief.groupSize();
        List<ComposedPlan.PackageResult> packages = new ArrayList<>();
        for (PlanDraft.PackageDraft p : draft.packages()) {
            packages.add(assemblePackage(p, travelers, catalog));
        }
        packages.sort(Comparator.comparing(ComposedPlan.PackageResult::key));
        List<Violation> violations = new ArrayList<>();
        for (int i = 1; i < packages.size(); i++) {
            ComposedPlan.PackageResult lower = packages.get(i - 1);
            ComposedPlan.PackageResult higher = packages.get(i);
            if (higher.pricePerPerson().compareTo(lower.pricePerPerson()) <= 0) {
                violations.add(Violation.of(ViolationCode.TIER_ORDER, higher.key(), null,
                        higher.key() + " per-person price " + higher.pricePerPerson() + " is not above " + lower.key()
                                + " " + lower.pricePerPerson()));
            }
        }
        return new AssemblyResult(new ComposedPlan(packages, degraded), violations);
    }

    private static ComposedPlan.PackageResult assemblePackage(PlanDraft.PackageDraft p, int travelers,
            Map<UUID, CatalogActivity> catalog) {
        List<ComposedPlan.DayResult> days = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int minutes = 0;
        for (PlanDraft.DayDraft day : p.days()) {
            List<ComposedPlan.ItemResult> items = new ArrayList<>();
            for (PlanDraft.ItemDraft item : day.items()) {
                CatalogActivity a = catalog.get(item.activityId());
                if (a == null) {
                    continue; // validator already rejected unknown ids; assembly is defensive
                }
                BigDecimal line = PlanPricer.lineTotal(a.price(), a.minPrice(), travelers);
                boolean floored = line.compareTo(a.price().multiply(BigDecimal.valueOf(travelers))) > 0;
                items.add(new ComposedPlan.ItemResult(item.slot(), clean(item.startHint()), a.id(), a.slug(), a.name(),
                        a.imageUrl(), a.durationMinutes(), a.price(), a.minPrice(), line, floored, clean(item.why())));
                ids.add(a.id());
                total = total.add(line);
                minutes += a.durationMinutes();
            }
            days.add(new ComposedPlan.DayResult(day.dayNumber(), clean(day.title()), clean(day.summary()), items));
        }
        return new ComposedPlan.PackageResult(p.key(), clean(p.title()), clean(p.tagline()), clean(p.description()),
                PlanPricer.perPerson(total, travelers), total, ComposedPlan.CURRENCY, minutes, ids, days);
    }

    /** Model text is displayed as plain text only: drop script/style blocks whole, unwrap other tags, collapse whitespace. */
    static String clean(String text) {
        if (text == null) {
            return null;
        }
        String withoutScripts = SCRIPT_OR_STYLE_BLOCK.matcher(text).replaceAll("");
        String withoutTags = ANY_TAG.matcher(withoutScripts).replaceAll("");
        return WHITESPACE_RUN.matcher(withoutTags).replaceAll(" ").strip();
    }
}
