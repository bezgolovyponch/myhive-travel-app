package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.plan.ComposedPlan;

import java.util.List;

/**
 * What the model needs to write the copy of one freshly composed package: {@code plan} holds just that
 * package (structure final, titles still placeholders), the brief it was built for and the catalog
 * snapshot behind the items - in snapshot order, because that order is what the activity codes
 * ({@link ActivityAliases}) are derived from - so a "why" can say what an activity is instead of
 * repeating its name.
 */
public record PlanTextsRequest(String locale, String destinationName, Brief brief, ComposedPlan plan,
                               List<CatalogActivity> catalog) {

    public PlanTextsRequest {
        catalog = catalog == null ? List.of() : List.copyOf(catalog);
    }
}
