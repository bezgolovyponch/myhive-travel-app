package com.myhive.backend.entity;

import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SchemaColumnsTest {

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private DestinationRepository destinationRepository;

    @Test
    void category_votable_defaultsTrue_andPersists() {
        Category category = new Category();
        category.setName("Transfer");
        category.setSlug("transfer");

        boolean defaultBeforeSave = category.isVotable();
        category.setVotable(false);

        Category saved = categoryRepository.saveAndFlush(category);
        Category reloaded = categoryRepository.findById(saved.getId()).orElseThrow();

        assertThat(defaultBeforeSave).isTrue();
        assertThat(reloaded.isVotable()).isFalse();
    }

    @Test
    void activity_featuredWeight_persists() {
        int expectedWeight = 7;

        Destination destination = new Destination();
        destination.setName("Prague");
        Destination savedDestination = destinationRepository.save(destination);

        Activity activity = new Activity();
        activity.setDestination(savedDestination);
        activity.setName("Tank Driving");
        activity.setPrice(new BigDecimal("150.00"));
        activity.setFeaturedWeight(expectedWeight);

        Activity saved = activityRepository.saveAndFlush(activity);
        Activity reloaded = activityRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getFeaturedWeight()).isEqualTo(expectedWeight);
    }
}
