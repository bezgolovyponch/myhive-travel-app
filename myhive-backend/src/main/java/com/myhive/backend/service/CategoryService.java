package com.myhive.backend.service;

import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.dto.CategoryUsageDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Package;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.PackageRepository;
import com.myhive.backend.repository.QuizAnswerWeightRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.util.Translations;
import lombok.RequiredArgsConstructor;
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
    private final ActivityRepository activityRepository;
    private final PackageRepository packageRepository;
    private final QuizAnswerWeightRepository quizAnswerWeightRepository;
    private final DestinationRepository destinationRepository;
    private final VoteSessionRepository voteSessionRepository;

    // Without a locale: admin/raw view (base name + translations map). With
    // one: public view, name resolved for that locale. See ActivityService.

    public List<CategoryDTO> getAllCategories() {
        return getAllCategories(null);
    }

    public List<CategoryDTO> getAllCategories(String locale) {
        return categoryRepository.findAll().stream()
                .map(c -> convertToDTO(c, locale))
                .sorted(Comparator.comparing(CategoryDTO::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Page<CategoryDTO> getCategoriesPaged(Pageable pageable) {
        return categoryRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    public CategoryDTO getCategoryById(UUID id) {
        return getCategoryById(id, null);
    }

    public CategoryDTO getCategoryById(UUID id, String locale) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        return convertToDTO(category, locale);
    }

    public CategoryDTO getCategoryBySlug(String slug) {
        return getCategoryBySlug(slug, null);
    }

    public CategoryDTO getCategoryBySlug(String slug, String locale) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        return convertToDTO(category, locale);
    }

    public CategoryUsageDTO getCategoryUsage(UUID id) {
        categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        List<String> activityNames = activityRepository.findByCategoriesId(id).stream()
                .map(Activity::getName)
                .toList();
        List<String> packageNames = packageRepository.findByCategoriesId(id).stream()
                .map(Package::getName)
                .toList();
        return new CategoryUsageDTO(activityNames, packageNames);
    }

    @Transactional
    public CategoryDTO createCategory(CategoryDTO dto) {
        if (categoryRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("Category with name '" + dto.getName() + "' already exists");
        }
        Category category = new Category();
        category.setName(dto.getName());
        SlugAssigner.assignOnCreate(category, dto.getSlug(), dto.getName(), categoryRepository);
        return convertToDTO(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDTO updateCategory(UUID id, CategoryDTO dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        if (!category.getName().equalsIgnoreCase(dto.getName())
                && categoryRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("Category with name '" + dto.getName() + "' already exists");
        }

        SlugAssigner.assignOnUpdate(category, dto.getSlug(), dto.getName(), category.getName(), categoryRepository);
        category.setName(dto.getName());
        // null = "unchanged" (see ActivityService.applyDtoToEntity).
        if (dto.getTranslations() != null) {
            category.setTranslations(dto.getTranslations());
        }
        return convertToDTO(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(UUID id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category", id);
        }
        activityRepository.deleteCategoryLinks(id);
        packageRepository.deleteCategoryLinks(id);
        quizAnswerWeightRepository.deleteAllByCategoryId(id);
        destinationRepository.deleteCategoryLinks(id);
        voteSessionRepository.deleteLikedCategoryLinks(id);
        categoryRepository.deleteById(id);
    }

    private CategoryDTO convertToDTO(Category category) {
        return convertToDTO(category, null);
    }

    private CategoryDTO convertToDTO(Category category, String locale) {
        String lc = Translations.normalize(locale);
        CategoryDTO dto = new CategoryDTO();
        dto.setId(category.getId());
        dto.setName(Translations.pick(category.getTranslations(), lc, "name", category.getName()));
        dto.setSlug(category.getSlug());
        if (locale == null) {
            dto.setTranslations(category.getTranslations());
        }
        return dto;
    }
}
