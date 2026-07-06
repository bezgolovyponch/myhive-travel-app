package com.myhive.backend.service;

import com.myhive.backend.entity.Slugged;
import com.myhive.backend.repository.SluggedRepository;
import com.myhive.backend.util.SlugUtils;

/**
 * Shared slug assignment for create/update flows of slugged entities.
 * Centralizes the uniqueness check and the exclude-self predicate that were
 * previously copy-pasted across every slugged CRUD service.
 */
public final class SlugAssigner {

    private SlugAssigner() {
    }

    public static <T extends Slugged> void assignOnCreate(
            T entity, String requestedSlug, String fallbackName, SluggedRepository<T> repository) {
        entity.setSlug(SlugUtils.resolveSlug(requestedSlug, fallbackName, repository::existsBySlug));
    }

    /**
     * Recomputes the slug if the requested slug or the name changed; otherwise leaves it untouched.
     * {@code currentName} must be captured before the DTO is applied to the entity.
     */
    public static <T extends Slugged> void assignOnUpdate(
            T entity, String requestedSlug, String newName, String currentName, SluggedRepository<T> repository) {
        if (!SlugUtils.needsUpdate(requestedSlug, entity.getSlug(), newName, currentName)) {
            return;
        }
        entity.setSlug(SlugUtils.resolveForUpdate(requestedSlug, newName, entity.getSlug(),
                slug -> repository.findBySlug(slug)
                        .filter(other -> !other.getId().equals(entity.getId()))
                        .isPresent()));
    }
}
