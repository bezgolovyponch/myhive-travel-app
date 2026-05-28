package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.exception.ActivityInUseInSessionException;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class ActivityDeletionGuardTest {

    @Autowired private ActivityService activityService;
    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void deleteActivity_inActiveSessionCuratedList_throwsConflict() {
        Activity activity = setupActivityInSession(VoteSessionStatus.ACTIVE);
        UUID id = activity.getId();
        assertThatThrownBy(() -> activityService.deleteActivity(id))
                .isInstanceOf(ActivityInUseInSessionException.class);
    }

    @Test
    void deleteActivity_inCompletedSession_succeeds() {
        Activity activity = setupActivityInSession(VoteSessionStatus.COMPLETED);
        UUID id = activity.getId();
        assertThatCode(() -> activityService.deleteActivity(id)).doesNotThrowAnyException();
    }

    private Activity setupActivityInSession(VoteSessionStatus status) {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        Category nightlife = new Category();
        nightlife.setName("Nightlife");
        nightlife.setSlug("nightlife");
        nightlife = categoryRepository.save(nightlife);
        Set<Category> cats = new HashSet<>();
        cats.add(nightlife);
        destination.setCategories(cats);
        destinationRepository.saveAndFlush(destination);

        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName("Club");
        activity.setPrice(new BigDecimal("100"));
        activity.setCategories(new HashSet<>(List.of(nightlife)));
        activity = activityRepository.saveAndFlush(activity);

        VoteSessionCreateRequest req = new VoteSessionCreateRequest();
        req.setDestinationId(destination.getId());
        req.setInitiatorEmail("o+" + UUID.randomUUID() + "@example.com");
        req.setNumberOfTravelers(2);
        req.setStartDate(LocalDate.of(2026, 8, 1));
        req.setEndDate(LocalDate.of(2026, 8, 10));
        req.setVoterToken(UUID.randomUUID());
        req.setQuizResponses(List.of());
        req.setActivityIds(List.of(activity.getId()));

        var response = voteSessionService.createSession(req);
        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        if (status == VoteSessionStatus.COMPLETED) {
            session.setStatus(VoteSessionStatus.COMPLETED);
            voteSessionRepository.saveAndFlush(session);
        }
        return activity;
    }
}
