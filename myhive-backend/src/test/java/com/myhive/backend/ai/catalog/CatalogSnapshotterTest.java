package com.myhive.backend.ai.catalog;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.Package;
import com.myhive.backend.entity.PackageActivity;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.PackageRepository;
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
    private final PackageRepository packageRepository = mock(PackageRepository.class);
    private final CatalogSnapshotter snapshotter = new CatalogSnapshotter(activityRepository, packageRepository);

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

    @Test
    void oneLine_breaksOnWordBoundaryBeforeLimit() {
        String expectedLastWord = "lighthouse";
        String longDescription = (expectedLastWord + " ").repeat(20);

        String result = CatalogSnapshotter.oneLine(longDescription);

        assertThat(result).endsWith("…");
        assertThat(result).hasSizeLessThanOrEqualTo(CatalogSnapshotter.ONE_LINE_MAX);
        String withoutEllipsis = result.substring(0, result.length() - 1);
        assertThat(withoutEllipsis).doesNotEndWith(" ");
        assertThat(withoutEllipsis).hasSizeLessThan(CatalogSnapshotter.ONE_LINE_MAX - 1);
        assertThat(withoutEllipsis).endsWith(expectedLastWord);
        assertThat(longDescription.strip()).startsWith(withoutEllipsis);
    }

    @Test
    void oneLine_fallsBackToHardCutWhenNoWordBoundaryExists() {
        int expectedTruncatedLength = CatalogSnapshotter.ONE_LINE_MAX - 1;
        String noSpaceDescription = "x".repeat(200);

        String result = CatalogSnapshotter.oneLine(noSpaceDescription);

        assertThat(result).hasSize(CatalogSnapshotter.ONE_LINE_MAX);
        assertThat(result).endsWith("…");
        assertThat(result.substring(0, expectedTruncatedLength)).isEqualTo("x".repeat(expectedTruncatedLength));
    }

    /** Production "what is included" texts run to 450 characters, and every one of them rides in the prompt. */
    @Test
    void includes_isFlattenedAndCutLikeTheOneLiner_andAnEmptyOneIsNone() {
        String expectedFlat = "Guide; transport; helmet";
        String overlong = "Trivlu Guide; Private Transport (Roundtrip); ".repeat(12);

        assertThat(CatalogSnapshotter.includes("  Guide;\n transport;   helmet ")).isEqualTo(expectedFlat);
        assertThat(CatalogSnapshotter.includes(overlong))
                .hasSizeLessThanOrEqualTo(CatalogSnapshotter.INCLUDES_MAX).endsWith("…");
        assertThat(CatalogSnapshotter.includes("   ")).isNull();
        assertThat(CatalogSnapshotter.includes(null)).isNull();
    }

    private static CatalogActivity catalogRow(UUID id) {
        return new CatalogActivity(id, "slug", "Name", "line", 60, true, new BigDecimal("10.00"), null, "img", List.of());
    }

    private static Package preset(Destination destination, String name, Tier tier, UUID... activityIds) {
        Package pkg = TestDataFactory.pkg(destination);
        pkg.setId(UUID.randomUUID());
        pkg.setName(name);
        pkg.setTier(tier);
        List<PackageActivity> links = new ArrayList<>();
        for (int i = 0; i < activityIds.length; i++) {
            Activity activity = TestDataFactory.activity(destination);
            activity.setId(activityIds[i]);
            links.add(new PackageActivity(pkg, activity, i));
        }
        pkg.setPackageActivities(links);
        return pkg;
    }

    @Test
    void presets_keepPackageOrder_andLeaveOutWhatTheCatalogLacks() {
        UUID expectedFirst = UUID.randomUUID();
        UUID expectedSecond = UUID.randomUUID();
        UUID notInCatalog = UUID.randomUUID();
        String expectedName = "Classic Stag: Essential";
        Destination destination = TestDataFactory.destination();
        Package pkg = preset(destination, expectedName, Tier.BASIC, expectedFirst, notInCatalog, expectedSecond);
        when(packageRepository.findByDestinationIdAndTierIsNotNull(any())).thenReturn(List.of(pkg));

        List<CatalogPreset> presets = snapshotter.presets(UUID.randomUUID(),
                List.of(catalogRow(expectedSecond), catalogRow(expectedFirst)));

        assertThat(presets).hasSize(1);
        assertThat(presets.get(0).name()).isEqualTo(expectedName);
        assertThat(presets.get(0).tier()).isEqualTo(Tier.BASIC);
        assertThat(presets.get(0).activityIds()).containsExactly(expectedFirst, expectedSecond);
    }

    @Test
    void presets_orderByTier_andDropAPackageWithNothingLeft() {
        UUID known = UUID.randomUUID();
        Destination destination = TestDataFactory.destination();
        Package premium = preset(destination, "Legend", Tier.PREMIUM, known);
        Package basic = preset(destination, "Essential", Tier.BASIC, known);
        Package emptied = preset(destination, "Gone", Tier.MEDIUM, UUID.randomUUID());
        when(packageRepository.findByDestinationIdAndTierIsNotNull(any())).thenReturn(List.of(premium, emptied, basic));

        List<CatalogPreset> presets = snapshotter.presets(UUID.randomUUID(), List.of(catalogRow(known)));

        assertThat(presets).extracting(CatalogPreset::tier).containsExactly(Tier.BASIC, Tier.PREMIUM);
    }
}
