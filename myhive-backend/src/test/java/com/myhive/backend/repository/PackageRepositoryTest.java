package com.myhive.backend.repository;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.Package;
import com.myhive.backend.entity.PackageActivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PackageRepositoryTest {

    @Autowired private PackageRepository packageRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Destination destination;
    private Activity activity;
    private Category category;

    @BeforeEach
    void setUp() {
        Destination dest = new Destination();
        dest.setName("Bali");
        dest.setCountry("Indonesia");
        destination = destinationRepository.save(dest);

        Activity act = new Activity();
        act.setDestination(destination);
        act.setName("Volcano Hike");
        act.setSlug("volcano-hike");
        act.setPrice(new BigDecimal("89.00"));
        activity = activityRepository.save(act);

        Category cat = new Category();
        cat.setName("Beach");
        cat.setSlug("beach");
        category = categoryRepository.save(cat);
    }

    @Test
    void findBySlugReturnsSavedPackage() {
        Package saved = packageRepository.save(buildPackage("honeymoon-bali"));

        Optional<Package> found = packageRepository.findBySlug("honeymoon-bali");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void existsBySlugIsTrueAfterSave() {
        packageRepository.save(buildPackage("adventure-tour"));

        assertThat(packageRepository.existsBySlug("adventure-tour")).isTrue();
        assertThat(packageRepository.existsBySlug("missing")).isFalse();
    }

    @Test
    void findByDestinationIdReturnsMatching() {
        packageRepository.save(buildPackage("p1"));
        packageRepository.save(buildPackage("p2"));

        List<Package> result = packageRepository.findByDestinationId(destination.getId());

        assertThat(result).hasSize(2);
    }

    @Test
    void findByCategoriesSlugReturnsMatching() {
        Package pkg = buildPackage("with-cat");
        pkg.getCategories().add(category);
        packageRepository.save(pkg);

        List<Package> result = packageRepository.findByCategoriesSlug("beach");

        assertThat(result).hasSize(1);
    }

    @Test
    void findByDestinationIdAndCategoriesSlugReturnsCombinedFilter() {
        Package matching = buildPackage("matching");
        matching.getCategories().add(category);
        packageRepository.save(matching);

        Package wrongCategory = buildPackage("no-cat");
        packageRepository.save(wrongCategory);

        List<Package> result = packageRepository.findByDestinationIdAndCategoriesSlug(
                destination.getId(), "beach");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSlug()).isEqualTo("matching");
    }

    @Test
    void findPackageNamesByActivityIdReturnsMatchingNames() {
        Package pkg = buildPackage("with-act");
        PackageActivity pa = new PackageActivity(pkg, activity, 0);
        pkg.getPackageActivities().add(pa);
        packageRepository.save(pkg);

        List<String> packageNames = packageRepository.findPackageNamesByActivityId(activity.getId());

        assertThat(packageNames).containsExactly("Honeymoon Bali");
    }

    private Package buildPackage(String slug) {
        Package p = new Package();
        p.setDestination(destination);
        p.setSlug(slug);
        p.setName("Honeymoon Bali");
        p.setDiscountPct(new BigDecimal("15.00"));
        return p;
    }
}
