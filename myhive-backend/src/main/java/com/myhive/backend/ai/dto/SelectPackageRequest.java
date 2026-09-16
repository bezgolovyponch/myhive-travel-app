package com.myhive.backend.ai.dto;

import com.myhive.backend.ai.model.Tier;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /ai/generations/&#123;id&#125;/select}. Typed as {@link Tier} on purpose: an
 * unknown key is rejected by the message converter as a 400 instead of reaching the graph as a string
 * its {@code Tier.valueOf} would choke on.
 */
public record SelectPackageRequest(@NotNull Tier packageKey) {
}
