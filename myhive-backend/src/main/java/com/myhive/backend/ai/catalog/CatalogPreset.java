package com.myhive.backend.ai.catalog;

import com.myhive.backend.ai.model.Tier;

import java.util.List;
import java.util.UUID;

/**
 * A ready-made package the planner starts one tier from. {@code activityIds} are in the package's own
 * order - most important first - and only hold activities that are in the catalog snapshot.
 */
public record CatalogPreset(UUID id, String name, Tier tier, List<UUID> activityIds, List<String> categorySlugs) {
}
