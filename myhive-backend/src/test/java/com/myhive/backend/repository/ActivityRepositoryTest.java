package com.myhive.backend.repository;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ActivityRepositoryTest {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private DestinationRepository destinationRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private UUID destinationId;

    @BeforeEach
    void setUp() {
        Destination dest = new Destination();
        dest.setName("Rome");
        dest.setCountry("Italy");
        dest = destinationRepository.save(dest);
        destinationId = dest.getId();

        Category history = new Category();
        history.setName("History");
        history.setSlug("history");
        history = categoryRepository.save(history);

        Category food = new Category();
        food.setName("Food");
        food.setSlug("food");
        food = categoryRepository.save(food);

        Activity a1 = new Activity();
        a1.setDestination(dest);
        a1.setName("Colosseum");
        a1.setSlug("colosseum");
        a1.setPrice(new BigDecimal("20.00"));
        Set<Category> c1 = new HashSet<>();
        c1.add(history);
        a1.setCategories(c1);
        activityRepository.save(a1);

        Activity a2 = new Activity();
        a2.setDestination(dest);
        a2.setName("Pasta Class");
        a2.setSlug("pasta-class");
        a2.setPrice(new BigDecimal("40.00"));
        Set<Category> c2 = new HashSet<>();
        c2.add(food);
        c2.add(history);
        a2.setCategories(c2);
        activityRepository.save(a2);
    }

    @Test
    void findByDestinationId_returnsActivities() {
        var result = activityRepository.findByDestinationId(destinationId);
        assertThat(result).hasSize(2);
    }

    @Test
    void findByCategoriesSlug_returnsMatchingActivities() {
        var result = activityRepository.findByCategoriesSlug("food");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Pasta Class");
    }

    @Test
    void findByCategoriesSlug_matchesMultipleActivitiesSharingCategory() {
        var result = activityRepository.findByCategoriesSlug("history");
        assertThat(result).hasSize(2);
    }

    @Test
    void findByDestinationIdAndCategoriesSlug_returnsCombinedFilter() {
        var result = activityRepository.findByDestinationIdAndCategoriesSlug(destinationId, "food");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Pasta Class");
    }
}
