package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.dto.VoteResultResponse;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// No class-level @Transactional: unlike VoteSessionProcessSessionTest (which calls
// processSession indirectly through closeSession — a self-invocation that does not go through
// the proxy, so REQUIRES_NEW is not actually applied there), this test calls
// voteSessionService.processSession(...) directly through the injected proxy. REQUIRES_NEW then
// genuinely suspends any surrounding transaction and opens a new one on a separate connection,
// which cannot see this test's uncommitted setup rows under a class-level @Transactional
// (confirmed: doing so fails every test with ResourceNotFoundException instead of the intended
// ranking assertion). Results must be visible after processSession commits, so we rely on
// unique data per test and H2 create-drop for cleanup instead.
@SpringBootTest
@Import(TestSecurityConfig.class)
class VoteSessionCartProcessTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionResultActivityRepository resultActivityRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void processSession_cart_freezesFullRankingByLikesDescThenCartOrder() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity first = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));   // 2 votes
        Activity second = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Karting", new BigDecimal("45.00")));     // 0 votes
        Activity third = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Shooting", new BigDecimal("45.00")));    // 1 vote
        VoteSessionResponse created = createCartSession(prague, first, second, third);

        castUpvotes(created.getShareToken(), List.of(first.getId(), third.getId()));
        castUpvotes(created.getShareToken(), List.of(first.getId()));

        VoteSession session = voteSessionRepository.findByShareToken(created.getShareToken()).orElseThrow();
        voteSessionService.processSession(session);

        VoteSession processed = voteSessionRepository.findByShareToken(created.getShareToken()).orElseThrow();
        List<VoteSessionResultActivity> results =
                resultActivityRepository.findBySessionIdOrderBySortOrder(processed.getId());

        assertThat(processed.getStatus()).isEqualTo(VoteSessionStatus.COMPLETED);
        // All three ballot rows survive — zero-vote activities included, ranked last.
        assertThat(results).extracting(r -> r.getActivity().getId())
                .containsExactly(first.getId(), third.getId(), second.getId());
    }

    @Test
    void processSession_cart_breaksTiesByCartOrder() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity first = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        Activity second = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Karting", new BigDecimal("45.00")));
        VoteSessionResponse created = createCartSession(prague, first, second);

        castUpvotes(created.getShareToken(), List.of(first.getId(), second.getId()));

        VoteSession session = voteSessionRepository.findByShareToken(created.getShareToken()).orElseThrow();
        voteSessionService.processSession(session);

        List<VoteSessionResultActivity> results = resultActivityRepository
                .findBySessionIdOrderBySortOrder(
                        voteSessionRepository.findByShareToken(created.getShareToken()).orElseThrow().getId());

        assertThat(results).extracting(r -> r.getActivity().getId())
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void processSession_cart_skipsDoNotAffectRankingButShowInResult() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity first = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));   // 0 likes, 2 skips
        Activity second = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Karting", new BigDecimal("45.00")));     // 1 like, 1 skip
        VoteSessionResponse created = createCartSession(prague, first, second);

        castBallot(created.getShareToken(), List.of(second.getId()), List.of(first.getId()));
        castBallot(created.getShareToken(), List.of(), List.of(first.getId(), second.getId()));

        VoteSession session = voteSessionRepository.findByShareToken(created.getShareToken()).orElseThrow();
        voteSessionService.processSession(session);

        List<VoteSessionResultActivity> results = resultActivityRepository
                .findBySessionIdOrderBySortOrder(
                        voteSessionRepository.findByShareToken(created.getShareToken()).orElseThrow().getId());

        // Karting holds the only like; Bar Crawl's two skips must not count as votes.
        assertThat(results).extracting(r -> r.getActivity().getId())
                .containsExactly(second.getId(), first.getId());

        VoteResultResponse result = voteSessionService.getResult(created.getShareToken());
        assertThat(result.getResult().get(0).getLikeCount()).isEqualTo(1);
        assertThat(result.getResult().get(0).getSkipCount()).isEqualTo(1);
        assertThat(result.getResult().get(1).getLikeCount()).isZero();
        assertThat(result.getResult().get(1).getSkipCount()).isEqualTo(2);
    }

    @Test
    void getResult_cart_exposesVoteModeParticipantCountAndNoSuggestions() {
        long expectedParticipants = 2L;

        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        VoteSessionResponse created = createCartSession(prague, barCrawl);

        castUpvotes(created.getShareToken(), List.of(barCrawl.getId()));
        castUpvotes(created.getShareToken(), List.of(barCrawl.getId()));

        VoteSession session = voteSessionRepository.findByShareToken(created.getShareToken()).orElseThrow();
        voteSessionService.processSession(session);

        VoteResultResponse result = voteSessionService.getResult(created.getShareToken());

        assertThat(result.getVoteMode()).isEqualTo("CART");
        assertThat(result.getParticipantCount()).isEqualTo(expectedParticipants);
        assertThat(result.getSuggestions()).isEmpty();
        assertThat(result.getBudget()).isNull();
        assertThat(result.getResult()).hasSize(1);
        assertThat(result.getResult().get(0).getLikeCount()).isEqualTo(2);
    }

    private void castUpvotes(UUID shareToken, List<UUID> activityIds) {
        castBallot(shareToken, activityIds, List.of());
    }

    private void castBallot(UUID shareToken, List<UUID> likedIds, List<UUID> skippedIds) {
        VoteBatchRequest batch = new VoteBatchRequest();
        batch.setVoterToken(UUID.randomUUID());
        List<VoteBatchRequest.VoteItem> items = new ArrayList<>();
        likedIds.forEach(id -> items.add(voteItem(id, true)));
        skippedIds.forEach(id -> items.add(voteItem(id, false)));
        batch.setVotes(items);
        voteSessionService.castVotes(shareToken, batch);
    }

    private VoteBatchRequest.VoteItem voteItem(UUID activityId, boolean liked) {
        VoteBatchRequest.VoteItem item = new VoteBatchRequest.VoteItem();
        item.setActivityId(activityId);
        item.setLiked(liked);
        return item;
    }

    private VoteSessionResponse createCartSession(Destination destination, Activity... activities) {
        VoteSessionCartCreateRequest request = new VoteSessionCartCreateRequest();
        request.setDestinationId(destination.getId());
        request.setInitiatorEmail("initiator@example.com");
        request.setNumberOfTravelers(4);
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setEndDate(LocalDate.of(2026, 8, 3));
        request.setActivityIds(List.of(activities).stream().map(Activity::getId).toList());
        return voteSessionService.createCartSession(request);
    }
}
