package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.dto.CategoryUsageDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.Package;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.PackageRepository;
import com.myhive.backend.repository.QuizAnswerWeightRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private PackageRepository packageRepository;

    @Mock
    private QuizAnswerWeightRepository quizAnswerWeightRepository;

    @Mock
    private DestinationRepository destinationRepository;

    @Mock
    private VoteSessionRepository voteSessionRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void getAllCategories_returnsSortedDTOs() {
        Category c1 = TestDataFactory.category("Zebra");
        Category c2 = TestDataFactory.category("Adventure");
        when(categoryRepository.findAll()).thenReturn(List.of(c1, c2));

        List<CategoryDTO> result = categoryService.getAllCategories();

        assertThat(result).extracting(CategoryDTO::getName)
                .containsExactly("Adventure", "Zebra");
    }

    @Test
    void getCategoryById_found_returnsDTO() {
        Category category = TestDataFactory.category();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        CategoryDTO result = categoryService.getCategoryById(category.getId());

        assertThat(result.getId()).isEqualTo(category.getId());
        assertThat(result.getName()).isEqualTo(category.getName());
    }

    @Test
    void getCategoryById_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
    }

    @Test
    void getCategoryBySlug_found_returnsDTO() {
        Category category = TestDataFactory.category();
        when(categoryRepository.findBySlug(category.getSlug())).thenReturn(Optional.of(category));

        CategoryDTO result = categoryService.getCategoryBySlug(category.getSlug());

        assertThat(result.getSlug()).isEqualTo(category.getSlug());
    }

    @Test
    void getCategoryBySlug_notFound_throwsResourceNotFound() {
        when(categoryRepository.findBySlug("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryBySlug("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createCategory_validDTO_savesAndReturns() {
        String expectedName = "Nightlife";
        CategoryDTO dto = new CategoryDTO();
        dto.setName(expectedName);

        when(categoryRepository.existsByNameIgnoreCase(expectedName)).thenReturn(false);
        when(categoryRepository.existsBySlug("nightlife")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CategoryDTO result = categoryService.createCategory(dto);

        assertThat(result.getName()).isEqualTo(expectedName);
        assertThat(result.getSlug()).isEqualTo("nightlife");
    }

    @Test
    void createCategory_duplicateName_throwsBadRequest() {
        CategoryDTO dto = new CategoryDTO();
        dto.setName("Adventure");
        when(categoryRepository.existsByNameIgnoreCase("Adventure")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createCategory_slugCollision_appendsSuffix() {
        CategoryDTO dto = new CategoryDTO();
        dto.setName("Adventure");

        when(categoryRepository.existsByNameIgnoreCase("Adventure")).thenReturn(false);
        when(categoryRepository.existsBySlug("adventure")).thenReturn(true);
        when(categoryRepository.existsBySlug("adventure-2")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryDTO result = categoryService.createCategory(dto);

        assertThat(result.getSlug()).isEqualTo("adventure-2");
    }

    @Test
    void createCategory_customSlug_usesProvidedSlug() {
        CategoryDTO dto = new CategoryDTO();
        dto.setName("Adventure");
        dto.setSlug("extreme-adventure");

        when(categoryRepository.existsByNameIgnoreCase("Adventure")).thenReturn(false);
        when(categoryRepository.existsBySlug("extreme-adventure")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryDTO result = categoryService.createCategory(dto);

        assertThat(result.getSlug()).isEqualTo("extreme-adventure");
    }

    @Test
    void updateCategory_renaming_updatesNameAndSlug() {
        Category existing = TestDataFactory.category("Adventure");
        CategoryDTO dto = new CategoryDTO();
        dto.setName("Extreme Sports");

        when(categoryRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameIgnoreCase("Extreme Sports")).thenReturn(false);
        when(categoryRepository.findBySlug("extreme-sports")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryDTO result = categoryService.updateCategory(existing.getId(), dto);

        assertThat(result.getName()).isEqualTo("Extreme Sports");
        assertThat(result.getSlug()).isEqualTo("extreme-sports");
    }

    @Test
    void updateCategory_duplicateName_throwsBadRequest() {
        Category existing = TestDataFactory.category("Adventure");
        CategoryDTO dto = new CategoryDTO();
        dto.setName("Nightlife");

        when(categoryRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameIgnoreCase("Nightlife")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(existing.getId(), dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateCategory_sameNameDifferentCase_allowed() {
        Category existing = TestDataFactory.category("Adventure");
        CategoryDTO dto = new CategoryDTO();
        dto.setName("ADVENTURE");

        when(categoryRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryDTO result = categoryService.updateCategory(existing.getId(), dto);

        assertThat(result.getName()).isEqualTo("ADVENTURE");
    }

    @Test
    void updateCategory_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        CategoryDTO dto = TestDataFactory.categoryDTO();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(id, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteCategory_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> categoryService.deleteCategory(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void getCategoryUsage_withActivityAndPackage_returnsNames() {
        Category category = TestDataFactory.category();
        Destination dest = TestDataFactory.destination();
        Activity activity = TestDataFactory.activity(dest);
        activity.setName("Hiking Tour");
        Package pkg = new Package();
        pkg.setName("Explorer Pack");

        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(activityRepository.findByCategoriesId(category.getId())).thenReturn(List.of(activity));
        when(packageRepository.findByCategoriesId(category.getId())).thenReturn(List.of(pkg));

        CategoryUsageDTO result = categoryService.getCategoryUsage(category.getId());

        assertThat(result.getActivityNames()).containsExactly("Hiking Tour");
        assertThat(result.getPackageNames()).containsExactly("Explorer Pack");
    }

    @Test
    void getCategoryUsage_noAssociations_returnsEmptyLists() {
        Category category = TestDataFactory.category();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(activityRepository.findByCategoriesId(category.getId())).thenReturn(List.of());
        when(packageRepository.findByCategoriesId(category.getId())).thenReturn(List.of());

        CategoryUsageDTO result = categoryService.getCategoryUsage(category.getId());

        assertThat(result.getActivityNames()).isEmpty();
        assertThat(result.getPackageNames()).isEmpty();
    }

    @Test
    void deleteCategory_removesAllCategoryLinksAndDeletes() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.existsById(id)).thenReturn(true);

        categoryService.deleteCategory(id);

        verify(activityRepository).deleteCategoryLinks(id);
        verify(packageRepository).deleteCategoryLinks(id);
        verify(quizAnswerWeightRepository).deleteAllByCategoryId(id);
        verify(destinationRepository).deleteCategoryLinks(id);
        verify(voteSessionRepository).deleteLikedCategoryLinks(id);
        verify(categoryRepository).deleteById(id);
    }
}
