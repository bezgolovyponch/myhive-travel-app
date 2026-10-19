package com.myhive.backend.repository;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.PrimaryCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PrimaryCategoryRepositoryTest {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private DestinationRepository destinationRepository;

    @Autowired
    private PrimaryCategoryRepository primaryCategoryRepository;

    /** Fresh entities without preset ids, so JPA treats them as new rather than merging. */
    private Activity newActivity() {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination.setCountry("Czechia");
        destination = destinationRepository.save(destination);

        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName("Axe Throwing");
        activity.setSlug("axe-throwing");
        activity.setPrice(new BigDecimal("30.00"));
        return activity;
    }

    @Test
    void findBySlugDe_returnsActivityWithGermanSlug() {
        Activity activity = newActivity();
        String expectedSlugDe = "axtwerfen";
        activity.setSlugDe(expectedSlugDe);
        Activity saved = activityRepository.save(activity);

        assertThat(activityRepository.findBySlugDe(expectedSlugDe))
                .get()
                .extracting(Activity::getId)
                .isEqualTo(saved.getId());
        assertThat(activityRepository.existsBySlugDe(expectedSlugDe)).isTrue();
        assertThat(activityRepository.existsBySlugDe("missing")).isFalse();
    }

    @Test
    void save_stampsUpdatedAtAndMovesItOnUpdate() throws InterruptedException {
        Activity activity = activityRepository.saveAndFlush(newActivity());
        LocalDateTime firstUpdatedAt = activity.getUpdatedAt();
        assertThat(firstUpdatedAt).isNotNull();

        Thread.sleep(20);
        String expectedName = "Renamed Activity";
        activity.setName(expectedName);
        Activity updated = activityRepository.saveAndFlush(activity);

        assertThat(updated.getName()).isEqualTo(expectedName);
        assertThat(updated.getUpdatedAt()).isAfter(firstUpdatedAt);
    }

    @Test
    void primaryCategories_orderBySortOrder() {
        PrimaryCategory second = TestDataFactory.primaryCategory("driving");
        second.setSortOrder(2);
        PrimaryCategory first = TestDataFactory.primaryCategory("shooting");
        first.setSortOrder(1);
        primaryCategoryRepository.save(second);
        primaryCategoryRepository.save(first);

        List<PrimaryCategory> ordered = primaryCategoryRepository.findAllByOrderBySortOrderAsc();

        assertThat(ordered).extracting(PrimaryCategory::getSlug).containsExactly("shooting", "driving");
        assertThat(primaryCategoryRepository.findBySlug("driving"))
                .get()
                .extracting(PrimaryCategory::getSlugDe)
                .isEqualTo("driving-de");
        assertThat(primaryCategoryRepository.findBySlug("missing")).isEmpty();
    }
}
