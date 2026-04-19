package com.myhive.backend.repository;

import com.myhive.backend.entity.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        Category c1 = new Category();
        c1.setName("Adventure");
        c1.setSlug("adventure");
        categoryRepository.save(c1);

        Category c2 = new Category();
        c2.setName("Food & Drink");
        c2.setSlug("food-and-drink");
        categoryRepository.save(c2);
    }

    @Test
    void findBySlug_returnsCategory() {
        var result = categoryRepository.findBySlug("adventure");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Adventure");
    }

    @Test
    void findBySlug_notFound_returnsEmpty() {
        assertThat(categoryRepository.findBySlug("nonexistent")).isEmpty();
    }

    @Test
    void existsBySlug_true() {
        assertThat(categoryRepository.existsBySlug("food-and-drink")).isTrue();
    }

    @Test
    void existsBySlug_false() {
        assertThat(categoryRepository.existsBySlug("nightlife")).isFalse();
    }

    @Test
    void existsByNameIgnoreCase_matchesRegardlessOfCase() {
        assertThat(categoryRepository.existsByNameIgnoreCase("adventure")).isTrue();
        assertThat(categoryRepository.existsByNameIgnoreCase("ADVENTURE")).isTrue();
        assertThat(categoryRepository.existsByNameIgnoreCase("Adventure")).isTrue();
    }

    @Test
    void existsByNameIgnoreCase_notFound_returnsFalse() {
        assertThat(categoryRepository.existsByNameIgnoreCase("unknown")).isFalse();
    }
}
