package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogPreset;
import com.myhive.backend.ai.model.Brief;

import java.util.List;

/** {@code presets} are the ready-made packages the tiers start from; empty when the destination has none. */
public record PlanRequest(String locale, String destinationName, Brief brief, List<CatalogActivity> catalog,
                          List<CatalogPreset> presets, List<ChatMessage> recentHistory) {

    public PlanRequest(String locale, String destinationName, Brief brief, List<CatalogActivity> catalog,
                       List<ChatMessage> recentHistory) {
        this(locale, destinationName, brief, catalog, List.of(), recentHistory);
    }
}
