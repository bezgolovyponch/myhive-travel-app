package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
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

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSessionProcessSessionTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionResultActivityRepository resultActivityRepository;
    @Autowired private VoteActivityLikeRepository voteActivityLikeRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void processSession_dropsActivitiesWithNonPositiveScore() {
        Fixture f = baseFixture(null);
        // a1: 2 likes / 0 skips (score 2 — KEEP)
        // a2: 1 like / 1 skip (score 0 — DROP)
        // a3: 0 votes (score 0 — DROP)
        // a4: 0 likes / 2 skips (score -2 — DROP)
        like(f.session, f.a1, true);
        like(f.session, f.a1, true);
        like(f.session, f.a2, true);
        like(f.session, f.a2, false);
        like(f.session, f.a4, false);
        like(f.session, f.a4, false);

        voteSessionService.closeSession(f.session.getShareToken(), f.session.getManagerToken());

        List<VoteSessionResultActivity> result =
                resultActivityRepository.findBySessionIdOrderBySortOrder(f.session.getId());
        assertThat(result).extracting(r -> r.getActivity().getId()).containsExactly(f.a1.getId());
    }

    @Test
    void processSession_skipAndContinue_budgetGreedyFill() {
        // Budget = 200. Travelers = 2.
        // a_high (snapshot 80, group=160, score 5): fits → take, running=160
        // a_big  (snapshot 150, group=300, score 4): doesn't fit → SKIP
        // a_low  (snapshot 20,  group=40,  score 3): fits → take, running=200
        Fixture f = budgetFixture(new BigDecimal("200"));
        likeMany(f.session, f.aHigh, 5, 0);
        likeMany(f.session, f.aBig, 4, 0);
        likeMany(f.session, f.aLow, 3, 0);

        voteSessionService.closeSession(f.session.getShareToken(), f.session.getManagerToken());

        List<VoteSessionResultActivity> result =
                resultActivityRepository.findBySessionIdOrderBySortOrder(f.session.getId());
        assertThat(result).extracting(r -> r.getActivity().getId())
                .containsExactly(f.aHigh.getId(), f.aLow.getId());
    }

    @Test
    void processSession_tieBreakByFeaturedWeightThenId() {
        Fixture f = baseFixture(null);
        f.a1.setFeaturedWeight(10);
        f.a2.setFeaturedWeight(5);
        activityRepository.saveAndFlush(f.a1);
        activityRepository.saveAndFlush(f.a2);
        likeMany(f.session, f.a1, 2, 0);
        likeMany(f.session, f.a2, 2, 0);

        voteSessionService.closeSession(f.session.getShareToken(), f.session.getManagerToken());

        List<VoteSessionResultActivity> result =
                resultActivityRepository.findBySessionIdOrderBySortOrder(f.session.getId());
        assertThat(result).extracting(r -> r.getActivity().getId())
                .containsExactly(f.a1.getId(), f.a2.getId());
    }

    @Test
    void processSession_nullBudget_takesAllPositives() {
        Fixture f = baseFixture(null);
        likeMany(f.session, f.a1, 2, 0);
        likeMany(f.session, f.a2, 1, 0);
        likeMany(f.session, f.a4, 5, 0);

        voteSessionService.closeSession(f.session.getShareToken(), f.session.getManagerToken());

        List<VoteSessionResultActivity> result =
                resultActivityRepository.findBySessionIdOrderBySortOrder(f.session.getId());
        assertThat(result).hasSize(3);
    }

    @Test
    void processSession_setsStatusCompleted() {
        Fixture f = baseFixture(null);
        likeMany(f.session, f.a1, 1, 0);

        voteSessionService.closeSession(f.session.getShareToken(), f.session.getManagerToken());

        VoteSession reloaded = voteSessionRepository.findById(f.session.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(VoteSessionStatus.COMPLETED);
    }

    @Test
    void processSession_resolverUsesSnapshotPriceNotLiveActivityPrice() {
        // Snapshot at curation: aLow.price=20, travelers=2, budget=200. group=40 → fits.
        // Admin re-prices aLow to 99999 mid-session → resolver still uses snapshot 20.
        Fixture f = budgetFixture(new BigDecimal("200"));
        likeMany(f.session, f.aLow, 5, 0);
        Activity priceBomb = f.aLow;
        priceBomb.setPrice(new BigDecimal("99999"));
        activityRepository.saveAndFlush(priceBomb);

        voteSessionService.closeSession(f.session.getShareToken(), f.session.getManagerToken());

        List<VoteSessionResultActivity> result =
                resultActivityRepository.findBySessionIdOrderBySortOrder(f.session.getId());
        assertThat(result).anySatisfy(r -> assertThat(r.getActivity().getId()).isEqualTo(f.aLow.getId()));
    }

    // ---------------- fixtures ----------------

    private record Fixture(VoteSession session,
                           Activity a1, Activity a2, Activity a3, Activity a4,
                           Activity aHigh, Activity aBig, Activity aLow) {}

    private Fixture baseFixture(BigDecimal budget) {
        Destination destination = saveDest();
        Category nightlife = saveCat("Nightlife", "nightlife");
        attachCat(destination, nightlife);

        Activity a1 = saveAct(destination, "a1", new BigDecimal("10"), Set.of(nightlife));
        Activity a2 = saveAct(destination, "a2", new BigDecimal("10"), Set.of(nightlife));
        Activity a3 = saveAct(destination, "a3", new BigDecimal("10"), Set.of(nightlife));
        Activity a4 = saveAct(destination, "a4", new BigDecimal("10"), Set.of(nightlife));

        VoteSession session = createSession(destination, budget,
                List.of(a1.getId(), a2.getId(), a3.getId(), a4.getId()));
        return new Fixture(session, a1, a2, a3, a4, null, null, null);
    }

    private Fixture budgetFixture(BigDecimal budget) {
        Destination destination = saveDest();
        Category nightlife = saveCat("Nightlife", "nightlife");
        attachCat(destination, nightlife);

        Activity aHigh = saveAct(destination, "aHigh", new BigDecimal("80"), Set.of(nightlife));
        Activity aBig = saveAct(destination, "aBig", new BigDecimal("150"), Set.of(nightlife));
        Activity aLow = saveAct(destination, "aLow", new BigDecimal("20"), Set.of(nightlife));

        VoteSession session = createSession(destination, budget,
                List.of(aHigh.getId(), aBig.getId(), aLow.getId()));
        return new Fixture(session, null, null, null, null, aHigh, aBig, aLow);
    }

    private VoteSession createSession(Destination destination, BigDecimal budget, List<UUID> activityIds) {
        VoteSessionCreateRequest req = new VoteSessionCreateRequest();
        req.setDestinationId(destination.getId());
        req.setInitiatorEmail("o+" + UUID.randomUUID() + "@example.com");
        req.setNumberOfTravelers(2);
        req.setStartDate(LocalDate.of(2026, 8, 1));
        req.setEndDate(LocalDate.of(2026, 8, 10));
        req.setBudget(budget);
        req.setVoterToken(UUID.randomUUID());
        req.setQuizResponses(List.of());
        req.setActivityIds(activityIds);

        VoteSessionResponse response = voteSessionService.createSession(req);
        return voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
    }

    private Destination saveDest() {
        Destination d = new Destination();
        d.setName("Prague");
        return destinationRepository.save(d);
    }

    private Category saveCat(String name, String slug) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
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

    private Activity saveAct(Destination destination, String name, BigDecimal price, Set<Category> cats) {
        Activity a = new Activity();
        a.setDestination(destination);
        a.setName(name);
        a.setPrice(price);
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

    private void likeMany(VoteSession session, Activity activity, int likes, int skips) {
        for (int i = 0; i < likes; i++) {
            like(session, activity, true);
        }
        for (int i = 0; i < skips; i++) {
            like(session, activity, false);
        }
    }
}
