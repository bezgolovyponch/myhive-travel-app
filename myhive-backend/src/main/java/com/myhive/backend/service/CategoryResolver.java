package com.myhive.backend.service;

import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.entity.Category;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.util.Translations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

class CategoryResolver {

    private CategoryResolver() {}

    static Set<Category> resolve(List<UUID> categoryIds, CategoryRepository repository) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Category> found = repository.findAllById(categoryIds);
        if (found.size() != categoryIds.size()) {
            Set<UUID> foundIds = found.stream().map(Category::getId).collect(Collectors.toSet());
            UUID missing = categoryIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
            throw new ResourceNotFoundException("Category", missing);
        }
        return new HashSet<>(found);
    }

    static List<CategoryDTO> toDTOs(Collection<Category> categories) {
        return toDTOs(categories, null);
    }

    /** @param locale normalized locale (see {@link Translations#normalize}); null = base names. */
    static List<CategoryDTO> toDTOs(Collection<Category> categories, String locale) {
        if (categories == null) {
            return new ArrayList<>();
        }
        return categories.stream()
                .map(c -> new CategoryDTO(c.getId(),
                        Translations.pick(c.getTranslations(), locale, "name", c.getName()), c.getSlug()))
                // Sorted by the name the caller will show, so German lists read alphabetically too.
                .sorted(Comparator.comparing(CategoryDTO::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
