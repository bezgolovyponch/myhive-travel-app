package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.edit.ActivityNameResolver;
import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.edit.EditOutcome;
import com.myhive.backend.ai.edit.EditRequest;
import com.myhive.backend.ai.edit.PackageEditor;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Wish;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Holds freshly built packages to what the organizer asked for by name. The planner model picks a
 * ready-made package per tier and is only told the wishes as free text, so "shooting on Saturday" came
 * back with the shooting on Friday in one tier, on Sunday in another and missing from a third. Here each
 * wish is checked against each package and made true with the same edits a chat turn uses: the activity
 * is moved to the day that was named, or added (on that day) when the package lacks it. Of a choice
 * ("tank or shooting") one is enough; what the package already has wins over adding another.
 */
@Slf4j
public final class WishApplier {

    private WishApplier() {
    }

    public static ComposedPlan apply(PackageEditor editor, ComposedPlan plan, Brief brief, List<CatalogActivity> catalog) {
        if (editor == null || plan == null || brief == null || brief.wishes().isEmpty() || catalog.isEmpty()) {
            return plan;
        }
        List<EditRequest> edits = new ArrayList<>();
        for (ComposedPlan.PackageResult pkg : plan.packages()) {
            for (Wish wish : brief.wishes()) {
                edit(pkg, wish, catalog).ifPresent(edits::add);
            }
        }
        if (edits.isEmpty()) {
            return plan;
        }
        EditOutcome outcome = editor.apply(plan, brief, catalog, edits);
        log.info("planner wishes applied={} rejected={}", outcome.applied().size(), outcome.rejected().size());
        return outcome.plan();
    }

    /** The one edit that makes the wish true in this package; empty when it is true already or cannot be. */
    private static java.util.Optional<EditRequest> edit(ComposedPlan.PackageResult pkg, Wish wish,
            List<CatalogActivity> catalog) {
        List<CatalogActivity> wanted = new ArrayList<>();
        for (String name : wish.activities()) {
            if (ActivityNameResolver.resolve(name, catalog) instanceof ActivityNameResolver.Found found) {
                wanted.add(found.activity());
            }
        }
        if (wanted.isEmpty()) {
            return java.util.Optional.empty();
        }
        boolean dayExists = wish.dayNumber() != null
                && pkg.days().stream().anyMatch(day -> day.dayNumber() == wish.dayNumber());
        Integer day = dayExists ? wish.dayNumber() : null;
        CatalogActivity held = null;
        for (CatalogActivity activity : wanted) {
            Integer on = dayOf(pkg, activity.id());
            if (on == null) {
                continue;
            }
            if (day == null || on.equals(day)) {
                return java.util.Optional.empty();
            }
            held = held == null ? activity : held;
        }
        if (held != null) {
            return java.util.Optional.of(new EditRequest(EditOp.MOVE, held.name(), null, pkg.key(), day, null));
        }
        return java.util.Optional.of(new EditRequest(EditOp.ADD, wanted.get(0).name(), null, pkg.key(), day, null));
    }

    private static Integer dayOf(ComposedPlan.PackageResult pkg, UUID activityId) {
        for (ComposedPlan.DayResult day : pkg.days()) {
            if (day.items().stream().anyMatch(item -> activityId.equals(item.activityId()))) {
                return day.dayNumber();
            }
        }
        return null;
    }
}
