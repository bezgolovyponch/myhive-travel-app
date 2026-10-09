package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogPreset;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PresetThemes;

import java.util.List;
import java.util.TreeSet;

/**
 * The two token-cheap views the chat model needs before it may propose edits: what the packages hold
 * right now, and which activity names exist. Names are the only handle the model gets — ids would only
 * invite it to invent them — so {@link ActivityNameResolver} can map its answer back onto the catalog.
 */
public final class PackagesView {

    private PackagesView() {
    }

    /** One line per package: {@code BASIC: day 1 [EVENING Beer Bike, NIGHT Club Crawl]; day 2 [MORNING Karting]}. */
    public static String render(ComposedPlan plan) {
        return render(plan, null);
    }

    /**
     * The organizer's trip draft alone when {@code only} is set - the chat must not see, and so cannot edit,
     * the trims the organizer is not working on - every package otherwise.
     */
    public static String render(ComposedPlan plan, Tier only) {
        return render(plan, only, null);
    }

    /** With the head-count, each line also carries the "from" price per person the organizer sees. */
    public static String render(ComposedPlan plan, Tier only, Integer travelers) {
        if (plan == null) {
            return "";
        }
        StringBuilder lines = new StringBuilder();
        for (ComposedPlan.PackageResult pkg : plan.packages()) {
            if (only != null && pkg.key() != only) {
                continue;
            }
            if (!lines.isEmpty()) {
                lines.append('\n');
            }
            lines.append(pkg.key()).append(": ").append(days(pkg));
            java.math.BigDecimal perPerson = com.myhive.backend.util.FromPrice.perPerson(pkg.totalPrice(), travelers);
            if (perPerson != null) {
                lines.append(" - from EUR ").append(perPerson.toPlainString()).append(" per person");
            }
        }
        return lines.toString();
    }

    /**
     * The other ready-made weekends, one line the chat rules refer to:
     * {@code Other ready-made weekends: Adrenaline [extreme, guns-and-bullets]; Beer & Food [czech-beer]}.
     * Empty when there are none, so the rule about them has nothing to act on.
     */
    public static String themes(ComposedPlan plan, List<CatalogActivity> catalog, List<CatalogPreset> presets) {
        List<String> themes = PresetThemes.of(plan, catalog, presets);
        if (themes.isEmpty()) {
            return "";
        }
        StringBuilder line = new StringBuilder("Other ready-made weekends: ");
        for (int i = 0; i < themes.size(); i++) {
            line.append(i == 0 ? "" : "; ").append(themes.get(i)).append(' ')
                    .append(PresetThemes.categories(themes.get(i), presets));
        }
        return line.toString();
    }

    /** The names the model may use in an edit, sorted so the list reads the same on every turn. */
    public static List<String> catalogNames(List<CatalogActivity> catalog) {
        if (catalog == null) {
            return List.of();
        }
        TreeSet<String> names = new TreeSet<>();
        for (CatalogActivity activity : catalog) {
            if (activity.name() != null && !activity.name().isBlank()) {
                names.add(activity.name().strip());
            }
        }
        return List.copyOf(names);
    }

    private static String days(ComposedPlan.PackageResult pkg) {
        StringBuilder days = new StringBuilder();
        for (ComposedPlan.DayResult day : pkg.days()) {
            if (!days.isEmpty()) {
                days.append("; ");
            }
            days.append("day ").append(day.dayNumber()).append(" [").append(items(day)).append(']');
        }
        return days.toString();
    }

    private static String items(ComposedPlan.DayResult day) {
        StringBuilder items = new StringBuilder();
        for (ComposedPlan.ItemResult item : day.items()) {
            if (!items.isEmpty()) {
                items.append(", ");
            }
            items.append(item.slot()).append(' ').append(item.name());
        }
        return items.toString();
    }
}
