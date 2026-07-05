package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.dto.VoteRequest;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSessionCartVotingTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void castVote_rejectsDownvoteOnCartSession_400() {
        Destination prague = newDestination("Prague");
        Activity barCrawl = newActivity(prague, "Bar Crawl");
        VoteSessionResponse session = createCartSession(prague, barCrawl);

        VoteRequest downvote = new VoteRequest();
        downvote.setVoterToken(UUID.randomUUID());
        downvote.setActivityId(barCrawl.getId());
        downvote.setLiked(false);

        assertThatThrownBy(() -> voteSessionService.castVote(session.getShareToken(), downvote))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("upvotes only");
    }

    @Test
    void castVotes_rejectsBatchContainingDownvote_400() {
        Destination prague = newDestination("Prague");
        Activity barCrawl = newActivity(prague, "Bar Crawl");
        Activity karting = newActivity(prague, "Karting");
        VoteSessionResponse session = createCartSession(prague, barCrawl, karting);

        VoteBatchRequest batch = batch(UUID.randomUUID(),
                vote(barCrawl.getId(), true), vote(karting.getId(), false));

        assertThatThrownBy(() -> voteSessionService.castVotes(session.getShareToken(), batch))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("upvotes only");
    }

    @Test
    void castVotes_acceptsUpvoteBatchOnCartSession() {
        long expectedParticipants = 1L;

        Destination prague = newDestination("Prague");
        Activity barCrawl = newActivity(prague, "Bar Crawl");
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

    private Destination newDestination(String name) {
        Destination destination = new Destination();
        destination.setName(name);
        return destinationRepository.save(destination);
    }

    private Activity newActivity(Destination destination, String name) {
        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName(name);
        activity.setPrice(new BigDecimal("45.00"));
        activity.setCategories(new HashSet<>());
        return activityRepository.saveAndFlush(activity);
    }
}
