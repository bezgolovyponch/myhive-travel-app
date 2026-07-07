package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.dto.VoteTallyResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
class VoteSessionTallyTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void getTally_forbiddenForVoterWhoHasNotVoted_403() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        VoteSessionResponse session = createCartSession(prague, barCrawl);

        UUID strangerToken = UUID.randomUUID();

        assertThatThrownBy(() ->
                voteSessionService.getTally(session.getShareToken(), strangerToken, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void getTally_returnsSortedCountsForVoter() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));   // 0 votes, cart position 1
        Activity karting = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Karting", new BigDecimal("45.00")));     // 1 vote, cart position 2
        VoteSessionResponse session = createCartSession(prague, barCrawl, karting);

        UUID voterToken = UUID.randomUUID();
        castVote(session.getShareToken(), voterToken, karting.getId(), true);

        VoteTallyResponse tally = voteSessionService.getTally(session.getShareToken(), voterToken, null);

        assertThat(tally.getParticipantCount()).isEqualTo(1);
        assertThat(tally.getStatus()).isEqualTo("ACTIVE");
        assertThat(tally.getRows()).extracting(VoteTallyResponse.TallyRow::getName)
                .containsExactly("Karting", "Bar Crawl");
        assertThat(tally.getRows().get(0).getLikeCount()).isEqualTo(1);
        assertThat(tally.getRows().get(1).getLikeCount()).isZero();
    }

    @Test
    void getTally_allSkipsVoterIsParticipantWithTallyAccess() {
        long expectedParticipants = 1L;

        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        VoteSessionResponse session = createCartSession(prague, barCrawl);

        UUID voterToken = UUID.randomUUID();
        castVote(session.getShareToken(), voterToken, barCrawl.getId(), false);

        VoteTallyResponse tally = voteSessionService.getTally(session.getShareToken(), voterToken, null);

        assertThat(tally.getParticipantCount()).isEqualTo(expectedParticipants);
        assertThat(tally.getRows().get(0).getLikeCount()).isZero();
    }

    @Test
    void getTally_skipsDoNotInflateLikeCountsOrRanking() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));   // 0 likes, 2 skips
        Activity karting = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Karting", new BigDecimal("45.00")));     // 1 like, 1 skip
        VoteSessionResponse session = createCartSession(prague, barCrawl, karting);

        UUID voterA = UUID.randomUUID();
        UUID voterB = UUID.randomUUID();
        castVote(session.getShareToken(), voterA, barCrawl.getId(), false);
        castVote(session.getShareToken(), voterA, karting.getId(), true);
        castVote(session.getShareToken(), voterB, barCrawl.getId(), false);
        castVote(session.getShareToken(), voterB, karting.getId(), false);

        VoteTallyResponse tally = voteSessionService.getTally(session.getShareToken(), voterA, null);

        assertThat(tally.getParticipantCount()).isEqualTo(2);
        assertThat(tally.getRows()).extracting(VoteTallyResponse.TallyRow::getName)
                .containsExactly("Karting", "Bar Crawl");
        assertThat(tally.getRows().get(0).getLikeCount()).isEqualTo(1);
        assertThat(tally.getRows().get(1).getLikeCount()).isZero();
    }

    @Test
    void getTally_allowedWithManagerTokenWithoutVoting() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        VoteSessionResponse session = createCartSession(prague, barCrawl);

        VoteTallyResponse tally = voteSessionService.getTally(
                session.getShareToken(), null, session.getManagerToken());

        assertThat(tally.getRows()).hasSize(1);
    }

    @Test
    void getTally_conflictForQuizSession_409() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Category nightlife = newCategory();
        attachCategory(prague, nightlife);
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        barCrawl.getCategories().add(nightlife);
        activityRepository.saveAndFlush(barCrawl);

        VoteSessionCreateRequest quizRequest = new VoteSessionCreateRequest();
        quizRequest.setDestinationId(prague.getId());
        quizRequest.setInitiatorEmail("organiser@example.com");
        quizRequest.setNumberOfTravelers(2);
        quizRequest.setStartDate(LocalDate.of(2026, 8, 1));
        quizRequest.setEndDate(LocalDate.of(2026, 8, 3));
        quizRequest.setVoterToken(UUID.randomUUID());
        quizRequest.setQuizResponses(List.of());
        quizRequest.setActivityIds(List.of(barCrawl.getId()));
        VoteSessionResponse quizSession = voteSessionService.createSession(quizRequest);

        assertThatThrownBy(() -> voteSessionService.getTally(
                quizSession.getShareToken(), null, quizSession.getManagerToken()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    private void castVote(UUID shareToken, UUID voterToken, UUID activityId, boolean liked) {
        VoteBatchRequest batch = new VoteBatchRequest();
        batch.setVoterToken(voterToken);
        VoteBatchRequest.VoteItem item = new VoteBatchRequest.VoteItem();
        item.setActivityId(activityId);
        item.setLiked(liked);
        batch.setVotes(List.of(item));
        voteSessionService.castVotes(shareToken, batch);
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

    private Category newCategory() {
        Category category = new Category();
        category.setName("Nightlife");
        category.setSlug("nightlife");
        return categoryRepository.save(category);
    }

    private void attachCategory(Destination destination, Category category) {
        Set<Category> set = new HashSet<>(destination.getCategories());
        set.add(category);
        destination.setCategories(set);
        destinationRepository.saveAndFlush(destination);
    }
}
