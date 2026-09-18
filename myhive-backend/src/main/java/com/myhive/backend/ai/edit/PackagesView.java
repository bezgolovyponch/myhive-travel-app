package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.plan.ComposedPlan;

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
        if (plan == null) {
            return "";
        }
        StringBuilder lines = new StringBuilder();
        for (ComposedPlan.PackageResult pkg : plan.packages()) {
            if (!lines.isEmpty()) {
                lines.append('\n');
            }
            lines.append(pkg.key()).append(": ").append(days(pkg));
        }
        return lines.toString();
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
