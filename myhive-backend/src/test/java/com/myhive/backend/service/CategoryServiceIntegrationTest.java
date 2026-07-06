package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.Package;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.PackageRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class CategoryServiceIntegrationTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DestinationRepository destinationRepository;

    @Autowired
    private VoteSessionRepository voteSessionRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deleteCategory_referencedByDestination_removesLinkAndCategory() {
        Category category = saveCategory("Nightlife", "nightlife");
        Destination destination = newDestination();
        destination.getCategories().add(category);
        UUID expectedDestinationId = destinationRepository.saveAndFlush(destination).getId();
        entityManager.clear();

        categoryService.deleteCategory(category.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryRepository.findById(category.getId())).isEmpty();
        Destination reloaded = destinationRepository.findById(expectedDestinationId).orElseThrow();
        assertThat(reloaded.getCategories()).isEmpty();
    }

    @Test
    void deleteCategory_likedInVoteSession_removesLinkAndCategory() {
        Category category = saveCategory("Adventure", "adventure");
        Destination destination = destinationRepository.saveAndFlush(newDestination());
        VoteSession session = newActiveSession(destination);
        session.getLikedCategories().add(category);
        UUID expectedSessionId = voteSessionRepository.saveAndFlush(session).getId();
        entityManager.clear();

        categoryService.deleteCategory(category.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryRepository.findById(category.getId())).isEmpty();
        VoteSession reloaded = voteSessionRepository.findById(expectedSessionId).orElseThrow();
        assertThat(reloaded.getLikedCategories()).isEmpty();
    }

    @Test
    void deleteCategory_assignedToActivity_removesLinkAndCategory() {
        Category category = saveCategory("Nightlife", "nightlife");
        Destination destination = destinationRepository.saveAndFlush(newDestination());
        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName("Pub Crawl");
        activity.setPrice(new BigDecimal("25.00"));
        activity.getCategories().add(category);
        UUID expectedActivityId = activityRepository.saveAndFlush(activity).getId();
        entityManager.clear();

        categoryService.deleteCategory(category.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryRepository.findById(category.getId())).isEmpty();
        Activity reloaded = activityRepository.findById(expectedActivityId).orElseThrow();
        assertThat(reloaded.getCategories()).isEmpty();
    }

    @Test
    void deleteCategory_assignedToPackage_removesLinkAndCategory() {
        Category category = saveCategory("Adventure", "adventure");
        Destination destination = destinationRepository.saveAndFlush(newDestination());
        Package pkg = new Package();
        pkg.setDestination(destination);
        pkg.setName("Explorer Pack");
        pkg.setDiscountPct(new BigDecimal("10.00"));
        pkg.getCategories().add(category);
        UUID expectedPackageId = packageRepository.saveAndFlush(pkg).getId();
        entityManager.clear();

        categoryService.deleteCategory(category.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryRepository.findById(category.getId())).isEmpty();
        Package reloaded = packageRepository.findById(expectedPackageId).orElseThrow();
        assertThat(reloaded.getCategories()).isEmpty();
    }

    private Category saveCategory(String name, String slug) {
        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        return categoryRepository.saveAndFlush(category);
    }

    private Destination newDestination() {
        Destination destination = new Destination();
        destination.setName("Prague");
        return destination;
    }

    private VoteSession newActiveSession(Destination destination) {
        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail("test@example.com");
        session.setNumberOfTravelers(2);
        session.setStartDate(LocalDate.of(2026, 8, 1));
        session.setEndDate(LocalDate.of(2026, 8, 10));
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setExpiresAt(LocalDateTime.of(2026, 8, 10, 23, 59));
        return session;
    }
}
