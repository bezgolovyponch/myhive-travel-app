package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.QuizResponseDTO;
import com.myhive.backend.dto.ResultActivityDTO;
import com.myhive.backend.dto.SuggestionDTO;
import com.myhive.backend.dto.VoteResultResponse;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.exception.ResultNotReadyException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSessionGetResultTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteActivityLikeRepository voteActivityLikeRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void getResult_beforeProcessing_throwsResultNotReady() {
        VoteSession session = createMinimalActiveSession();
        UUID shareToken = session.getShareToken();
        assertThatThrownBy(() -> voteSessionService.getResult(shareToken))
                .isInstanceOf(ResultNotReadyException.class);
    }

    @Test
    void getResult_returnsResultWithSnapshotPricesAndCounts() {
        Destination destination = saveDest();
        Category nightlife = saveCat("Nightlife", "nightlife", true);
        attachCat(destination, nightlife);
        Activity activity = saveAct(destination, "Tank Driving", new BigDecimal("150.00"), 5, Set.of(nightlife));

        VoteSession session = createAndPopulate(destination, new BigDecimal("3000"),
                List.of(activity.getId()), List.of(), 2);
        like(session, activity, true);
        like(session, activity, true);
        like(session, activity, false);

        voteSessionService.closeSession(session.getShareToken(), session.getManagerToken());
        VoteResultResponse response = voteSessionService.getResult(session.getShareToken());

        assertThat(response.getResult()).hasSize(1);
        ResultActivityDTO row = response.getResult().get(0);
        assertThat(row.getActivityId()).isEqualTo(activity.getId());
        assertThat(row.getName()).isEqualTo("Tank Driving");
        assertThat(row.getPrice()).isEqualByComparingTo("150.00");
        assertThat(row.getLikeCount()).isEqualTo(2);
        assertThat(row.getSkipCount()).isEqualTo(1);

        assertThat(response.getTotalPrice()).isEqualByComparingTo("300.00");
        assertThat(response.getBudget()).isEqualByComparingTo("3000");
        assertThat(response.getRemaining()).isEqualByComparingTo("2700.00");
        assertThat(response.getNumberOfTravelers()).isEqualTo(2);
    }

    @Test
    void getResult_nullBudget_remainingIsNull() {
        Destination destination = saveDest();
        Category nightlife = saveCat("Nightlife", "nightlife", true);
        attachCat(destination, nightlife);
        Activity activity = saveAct(destination, "A", new BigDecimal("100"), 5, Set.of(nightlife));

        VoteSession session = createAndPopulate(destination, null,
                List.of(activity.getId()), List.of(), 2);
        like(session, activity, true);

        voteSessionService.closeSession(session.getShareToken(), session.getManagerToken());
        VoteResultResponse response = voteSessionService.getResult(session.getShareToken());

        assertThat(response.getBudget()).isNull();
        assertThat(response.getRemaining()).isNull();
    }

    @Test
    void getResult_includesSuggestionsExcludingCuratedList() {
        Destination destination = saveDest();
        Category nightlife = saveCat("Nightlife", "nightlife", true);
        attachCat(destination, nightlife);
        Activity curated = saveAct(destination, "curated", new BigDecimal("100"), 5, Set.of(nightlife));
        Activity suggested = saveAct(destination, "suggested", new BigDecimal("200"), 7, Set.of(nightlife));

        VoteSession session = createAndPopulate(destination, new BigDecimal("1000"),
                List.of(curated.getId()), List.of(), 2);
        like(session, curated, true);
        voteSessionService.closeSession(session.getShareToken(), session.getManagerToken());

        VoteResultResponse response = voteSessionService.getResult(session.getShareToken());

        assertThat(response.getSuggestions()).extracting(SuggestionDTO::getActivityId)
                .containsExactly(suggested.getId());
    }

    @Test
    void getResult_groupMinimum_floorsTotalAndExposesMinPrice() {
        BigDecimal expectedGroupMinimum = new BigDecimal("300.00");

        Destination destination = saveDest();
        Category nightlife = saveCat("Nightlife", "nightlife", true);
        attachCat(destination, nightlife);
        Activity activity = saveAct(destination, "Boat Rental", new BigDecimal("50.00"), 5, Set.of(nightlife));
        activity.setMinPrice(expectedGroupMinimum);
        activityRepository.save(activity);

        VoteSession session = createAndPopulate(destination, null,
                List.of(activity.getId()), List.of(), 2);
        like(session, activity, true);

        voteSessionService.closeSession(session.getShareToken(), session.getManagerToken());
        VoteResultResponse response = voteSessionService.getResult(session.getShareToken());

        assertThat(response.getResult().getFirst().getMinPrice()).isEqualByComparingTo(expectedGroupMinimum);
        // 2 travelers × €50 = €100 < €300 -> the estimate uses the group minimum,
        // matching what the Trip Builder will compute after hydration.
        assertThat(response.getTotalPrice()).isEqualByComparingTo(expectedGroupMinimum);
    }

    // ---- helpers ----

    private VoteSession createMinimalActiveSession() {
        Destination destination = saveDest();
        Category nightlife = saveCat("Nightlife", "nightlife", true);
        attachCat(destination, nightlife);
        Activity activity = saveAct(destination, "A", new BigDecimal("100"), 5, Set.of(nightlife));
        return createAndPopulate(destination, null, List.of(activity.getId()), List.of(), 2);
    }

    private VoteSession createAndPopulate(Destination destination, BigDecimal budget,
                                          List<UUID> activityIds,
                                          List<QuizResponseDTO> quizResponses,
                                          int travelers) {
        VoteSessionCreateRequest req = new VoteSessionCreateRequest();
        req.setDestinationId(destination.getId());
        req.setInitiatorEmail("o+" + UUID.randomUUID() + "@example.com");
        req.setNumberOfTravelers(travelers);
        req.setStartDate(LocalDate.of(2026, 8, 1));
        req.setEndDate(LocalDate.of(2026, 8, 10));
        req.setBudget(budget);
        req.setVoterToken(UUID.randomUUID());
        req.setQuizResponses(quizResponses);
        req.setActivityIds(activityIds);
        VoteSessionResponse response = voteSessionService.createSession(req);
        return voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
    }

    private Destination saveDest() {
        Destination d = new Destination();
        d.setName("Prague");
        return destinationRepository.save(d);
    }

    private Category saveCat(String name, String slug, boolean votable) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setVotable(votable);
        return categoryRepository.save(c);
    }

    private void attachCat(Destination destination, Category... cats) {
        Set<Category> set = new HashSet<>(destination.getCategories());
        for (Category c : cats) {
            set.add(c);
        }
        destination.setCategories(set);
        destinationRepository.saveAndFlush(destination);
    }

    private Activity saveAct(Destination destination, String name, BigDecimal price,
                              int featuredWeight, Set<Category> cats) {
        Activity a = new Activity();
        a.setDestination(destination);
        a.setName(name);
        a.setPrice(price);
        a.setFeaturedWeight(featuredWeight);
        a.setCategories(new HashSet<>(cats));
        return activityRepository.saveAndFlush(a);
    }

    private void like(VoteSession session, Activity activity, boolean liked) {
        VoteActivityLike l = new VoteActivityLike();
        l.setSession(session);
        l.setVoterToken(UUID.randomUUID());
        l.setActivity(activity);
        l.setLiked(liked);
        voteActivityLikeRepository.save(l);
    }
}
