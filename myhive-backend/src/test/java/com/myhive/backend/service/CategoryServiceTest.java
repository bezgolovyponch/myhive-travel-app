package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

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
    void deleteCategory_noActivities_deletes() {
        Category category = TestDataFactory.category();
        category.setActivities(new HashSet<>());
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        categoryService.deleteCategory(category.getId());

        verify(categoryRepository).deleteById(category.getId());
    }

    @Test
    void deleteCategory_withActivities_throwsBadRequest() {
        Category category = TestDataFactory.category();
        Destination destination = TestDataFactory.destination();
        Activity activity = TestDataFactory.activity(destination);
        Set<Activity> activities = new HashSet<>();
        activities.add(activity);
        category.setActivities(activities);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.deleteCategory(category.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot delete category")
                .hasMessageContaining("1 associated activities");
    }

    @Test
    void deleteCategory_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
