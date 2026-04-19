package com.myhive.backend.service;

import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.entity.Category;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryDTO> getAllCategories() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::convertToDTO)
                .toList();
    }

    public Page<CategoryDTO> getCategoriesPaged(Pageable pageable) {
        return categoryRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    public CategoryDTO getCategoryById(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        return convertToDTO(category);
    }

    public CategoryDTO getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        return convertToDTO(category);
    }

    @Transactional
    public CategoryDTO createCategory(CategoryDTO dto) {
        if (categoryRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("Category with name '" + dto.getName() + "' already exists");
        }
        Category category = new Category();
        category.setName(dto.getName());
        category.setSlug(SlugUtils.resolveSlug(dto.getSlug(), dto.getName(), categoryRepository::existsBySlug));
        try {
            return convertToDTO(categoryRepository.save(category));
        } catch (DataIntegrityViolationException e) {
            category.setSlug(SlugUtils.resolveSlug(dto.getSlug(), dto.getName(), categoryRepository::existsBySlug));
            return convertToDTO(categoryRepository.save(category));
        }
    }

    @Transactional
    public CategoryDTO updateCategory(UUID id, CategoryDTO dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        if (!category.getName().equalsIgnoreCase(dto.getName())
                && categoryRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("Category with name '" + dto.getName() + "' already exists");
        }

        boolean updateSlug = SlugUtils.needsUpdate(dto.getSlug(), category.getSlug(), dto.getName(), category.getName());
        category.setName(dto.getName());
        if (updateSlug) {
            category.setSlug(SlugUtils.resolveForUpdate(dto.getSlug(), dto.getName(), category.getSlug(),
                    slug -> categoryRepository.findBySlug(slug)
                            .filter(c -> !c.getId().equals(id))
                            .isPresent()));
        }
        return convertToDTO(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        if (category.getActivities() != null && !category.getActivities().isEmpty()) {
            throw new BadRequestException(
                    "Cannot delete category with " + category.getActivities().size() + " associated activities. Remove them first.");
        }
        categoryRepository.deleteById(id);
    }

    private CategoryDTO convertToDTO(Category category) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setSlug(category.getSlug());
        return dto;
    }
}
