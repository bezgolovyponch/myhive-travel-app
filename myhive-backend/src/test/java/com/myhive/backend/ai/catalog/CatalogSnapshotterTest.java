package com.myhive.backend.ai.catalog;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.ActivityRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogSnapshotterTest {

    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final CatalogSnapshotter snapshotter = new CatalogSnapshotter(activityRepository);

    @Test
    void snapshot_localizesAndCompactsFields() {
        String expectedGermanName = "Bierfahrrad";
        String expectedCategorySlug = "nightlife";
        Destination destination = TestDataFactory.destination();
        Category nightlife = TestDataFactory.category("Nightlife");
        nightlife.setSlug(expectedCategorySlug);
        Activity activity = TestDataFactory.activity(destination, nightlife);
        activity.setName("Beer Bike");
        activity.setDescription("Pedal. Drink. Repeat. " + "x".repeat(300));
        activity.setDuration(null);
        activity.setTranslations(Map.of("de", Map.of("name", expectedGermanName)));
        when(activityRepository.findByDestinationId(any())).thenReturn(List.of(activity));

        List<CatalogActivity> snapshot = snapshotter.snapshot(UUID.randomUUID(), Brief.empty(), "de");

        assertThat(snapshot).hasSize(1);
        CatalogActivity row = snapshot.get(0);
        assertThat(row.name()).isEqualTo(expectedGermanName);
        assertThat(row.oneLine()).hasSizeLessThanOrEqualTo(CatalogSnapshotter.ONE_LINE_MAX);
        assertThat(row.durationMinutes()).isEqualTo(CatalogSnapshotter.DEFAULT_DURATION_MINUTES);
        assertThat(row.durationKnown()).isFalse();
        assertThat(row.categorySlugs()).containsExactly(expectedCategorySlug);
    }

    @Test
    void snapshot_capsAt80_preferringBriefCategoriesThenFeaturedWeight() {
        String expectedCategorySlug = "driving";
        Destination destination = TestDataFactory.destination();
        Category wanted = TestDataFactory.category("Driving");
        wanted.setSlug(expectedCategorySlug);
        Category other = TestDataFactory.category("Culture");
        other.setSlug("culture");
        List<Activity> activities = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Activity a = TestDataFactory.activity(destination, i < 50 ? other : wanted);
            a.setId(UUID.randomUUID());
            a.setName("A" + i);
            a.setFeaturedWeight(i);
            activities.add(a);
        }
        when(activityRepository.findByDestinationId(any())).thenReturn(activities);
        Brief brief = Brief.empty().withCategorySlugs(List.of(expectedCategorySlug));

        List<CatalogActivity> snapshot = snapshotter.snapshot(UUID.randomUUID(), brief, "en");

        assertThat(snapshot).hasSize(CatalogSnapshotter.MAX_ACTIVITIES);
        assertThat(snapshot.subList(0, 50)).allMatch(c -> c.categorySlugs().contains(expectedCategorySlug));
        assertThat(snapshot.get(0).name()).isEqualTo("A99");
    }

    @Test
    void snapshot_keepsPricesAsCatalogValues() {
        BigDecimal expectedPrice = new BigDecimal("45.00");
        BigDecimal expectedMinPrice = new BigDecimal("300.00");
        Destination destination = TestDataFactory.destination();
        Activity activity = TestDataFactory.activity(destination, "Karting", expectedPrice);
        activity.setMinPrice(expectedMinPrice);
        when(activityRepository.findByDestinationId(any())).thenReturn(List.of(activity));

        CatalogActivity row = snapshotter.snapshot(UUID.randomUUID(), Brief.empty(), null).get(0);

        assertThat(row.price()).isEqualByComparingTo(expectedPrice);
        assertThat(row.minPrice()).isEqualByComparingTo(expectedMinPrice);
    }
}
