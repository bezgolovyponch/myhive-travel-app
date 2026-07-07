package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.dto.VoteRequest;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.repository.ActivityRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSessionCartVotingTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private VoteActivityLikeRepository voteActivityLikeRepository;
    @Autowired private VoteSessionRepository voteSessionRepository;

    @Test
    void castVote_recordsSkipOnCartSession() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        VoteSessionResponse session = createCartSession(prague, barCrawl);
        UUID expectedVoterToken = UUID.randomUUID();

        VoteRequest skip = new VoteRequest();
        skip.setVoterToken(expectedVoterToken);
        skip.setActivityId(barCrawl.getId());
        skip.setLiked(false);

        voteSessionService.castVote(session.getShareToken(), skip);

        VoteActivityLike recorded =
                recordedVote(session.getShareToken(), expectedVoterToken, barCrawl.getId());
        assertThat(recorded.getLiked()).isFalse();
    }

    @Test
    void castVotes_recordsSkipsInMixedBatchOnCartSession() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        Activity karting = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Karting", new BigDecimal("45.00")));
        VoteSessionResponse session = createCartSession(prague, barCrawl, karting);
        UUID expectedVoterToken = UUID.randomUUID();

        VoteBatchRequest batch = batch(expectedVoterToken,
                vote(barCrawl.getId(), true), vote(karting.getId(), false));

        voteSessionService.castVotes(session.getShareToken(), batch);

        assertThat(recordedVote(session.getShareToken(), expectedVoterToken, barCrawl.getId())
                .getLiked()).isTrue();
        assertThat(recordedVote(session.getShareToken(), expectedVoterToken, karting.getId())
                .getLiked()).isFalse();
    }

    @Test
    void castVotes_acceptsUpvoteBatchOnCartSession() {
        long expectedParticipants = 1L;

        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        VoteSessionResponse session = createCartSession(prague, barCrawl);

        VoteBatchRequest batch = batch(UUID.randomUUID(), vote(barCrawl.getId(), true));

        assertThatCode(() -> voteSessionService.castVotes(session.getShareToken(), batch))
                .doesNotThrowAnyException();
        assertThat(voteSessionService.getParticipantCount(session.getShareToken()))
                .isEqualTo(expectedParticipants);
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

    private VoteBatchRequest batch(UUID voterToken, VoteBatchRequest.VoteItem... items) {
        VoteBatchRequest request = new VoteBatchRequest();
        request.setVoterToken(voterToken);
        request.setVotes(List.of(items));
        return request;
    }

    private VoteBatchRequest.VoteItem vote(UUID activityId, boolean liked) {
        VoteBatchRequest.VoteItem item = new VoteBatchRequest.VoteItem();
        item.setActivityId(activityId);
        item.setLiked(liked);
        return item;
    }

    private VoteActivityLike recordedVote(UUID shareToken, UUID voterToken, UUID activityId) {
        UUID sessionId = voteSessionRepository.findByShareToken(shareToken).orElseThrow().getId();
        return voteActivityLikeRepository
                .findBySessionIdAndVoterTokenAndActivityId(sessionId, voterToken, activityId)
                .orElseThrow();
    }
}
