package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteActivityResponse;
import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.dto.VoteSessionActivityAddRequest;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionContactRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.dto.VoteTallyResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ConflictException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteRecommendationRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Group vote v3: one ballot per friend with recommendations, the organiser dropping, restoring and
 * adding activities on a running vote, and the organiser's second contact. Not @Transactional:
 * processSession runs REQUIRES_NEW and must see committed rows, so every test builds its own data.
 */
@SpringBootTest
@Import(TestSecurityConfig.class)
class VoteSessionGroupVoteTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionResultActivityRepository resultActivityRepository;
    @Autowired private VoteRecommendationRepository voteRecommendationRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    private Destination prague;
    private Activity shooting;
    private Activity steak;
    private Activity tank;
    private Activity pubGolf;

    @BeforeEach
    void setUp() {
        prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        shooting = activityRepository.saveAndFlush(TestDataFactory.activity(prague, "AK-47 shooting", new BigDecimal("89.00")));
        steak = activityRepository.saveAndFlush(TestDataFactory.activity(prague, "Steak dinner", new BigDecimal("59.00")));
        tank = activityRepository.saveAndFlush(TestDataFactory.activity(prague, "Tank driving", new BigDecimal("149.00")));
        pubGolf = activityRepository.saveAndFlush(TestDataFactory.activity(prague, "Pub golf", new BigDecimal("29.00")));
    }

    @Test
    void castVotes_secondBallotFromTheSameFriend_isRejected() {
        VoteSessionResponse session = createVote(shooting, steak);
        UUID friend = UUID.randomUUID();
        voteSessionService.castVotes(session.getShareToken(), ballot(friend, List.of(shooting.getId()), List.of()));

        assertThatThrownBy(() -> voteSessionService.castVotes(session.getShareToken(),
                ballot(friend, List.of(steak.getId()), List.of())))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void castVotes_savesRecommendations_andTallyListsThoseOffTheBallotMostRecommendedFirst() {
        VoteSessionResponse session = createVote(shooting, steak);
        voteSessionService.castVotes(session.getShareToken(),
                ballot(UUID.randomUUID(), List.of(shooting.getId()), List.of(pubGolf.getId(), tank.getId())));
        voteSessionService.castVotes(session.getShareToken(),
                ballot(UUID.randomUUID(), List.of(steak.getId()), List.of(pubGolf.getId(), shooting.getId())));

        VoteTallyResponse tally = voteSessionService.getTally(session.getShareToken(), null, session.getManagerToken());

        // Shooting is already on the ballot, so recommending it adds nothing to the list.
        assertThat(tally.getRecommendations()).extracting(VoteTallyResponse.RecommendationRow::getName)
                .containsExactly("Pub golf", "Tank driving");
        assertThat(tally.getRecommendations().get(0).getRecommendationCount()).isEqualTo(2);
    }

    @Test
    void castVotes_recommendationFromAnotherDestination_rejectsTheWholeBallot() {
        Destination berlin = destinationRepository.save(TestDataFactory.destination("Berlin"));
        Activity berlinBar = activityRepository.saveAndFlush(TestDataFactory.activity(berlin, "Berlin bar", new BigDecimal("20.00")));
        VoteSessionResponse session = createVote(shooting);
        UUID friend = UUID.randomUUID();

        assertThatThrownBy(() -> voteSessionService.castVotes(session.getShareToken(),
                ballot(friend, List.of(shooting.getId()), List.of(berlinBar.getId()))))
                .isInstanceOf(BadRequestException.class);

        // Nothing was saved, so the friend can send a corrected ballot.
        voteSessionService.castVotes(session.getShareToken(), ballot(friend, List.of(shooting.getId()), List.of()));
        assertThat(voteSessionService.getParticipantCount(session.getShareToken())).isEqualTo(1);
    }

    @Test
    void excludeActivity_hidesItFromFriends_flagsItInTheTally_andRestoreBringsItBack() {
        VoteSessionResponse session = createVote(shooting, steak, tank);

        voteSessionService.excludeActivity(session.getShareToken(), session.getManagerToken(), tank.getId());

        assertThat(voteSessionService.getActivities(session.getShareToken()))
                .extracting(VoteActivityResponse::getName).containsExactly("AK-47 shooting", "Steak dinner");
        VoteTallyResponse tally = voteSessionService.getTally(session.getShareToken(), null, session.getManagerToken());
        assertThat(tally.getRows()).filteredOn(VoteTallyResponse.TallyRow::isExcluded)
                .extracting(VoteTallyResponse.TallyRow::getName).containsExactly("Tank driving");

        voteSessionService.restoreActivity(session.getShareToken(), session.getManagerToken(), tank.getId());

        assertThat(voteSessionService.getActivities(session.getShareToken()))
                .extracting(VoteActivityResponse::getName).containsExactly("AK-47 shooting", "Steak dinner", "Tank driving");
    }

    @Test
    void excludeActivity_wrongManagerToken_orActivityNotOnTheVote_isRejected() {
        VoteSessionResponse session = createVote(shooting);

        assertThatThrownBy(() -> voteSessionService.excludeActivity(session.getShareToken(), UUID.randomUUID(), shooting.getId()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> voteSessionService.excludeActivity(session.getShareToken(), session.getManagerToken(), pubGolf.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void processSession_leavesDroppedActivitiesOutOfTheResult() {
        VoteSessionResponse session = createVote(shooting, tank);
        voteSessionService.castVotes(session.getShareToken(),
                ballot(UUID.randomUUID(), List.of(shooting.getId(), tank.getId()), List.of()));
        voteSessionService.excludeActivity(session.getShareToken(), session.getManagerToken(), tank.getId());

        VoteSession entity = voteSessionRepository.findByShareToken(session.getShareToken()).orElseThrow();
        voteSessionService.processSession(entity);

        List<VoteSessionResultActivity> results = resultActivityRepository.findBySessionIdOrderBySortOrder(entity.getId());
        assertThat(results).extracting(r -> r.getActivity().getId()).containsExactly(shooting.getId());
    }

    @Test
    void addActivity_appendsARecommendationToTheBallot_andIsIdempotent() {
        VoteSessionResponse session = createVote(shooting);
        VoteSessionActivityAddRequest add = new VoteSessionActivityAddRequest();
        add.setActivityId(pubGolf.getId());

        voteSessionService.addActivity(session.getShareToken(), session.getManagerToken(), add);
        voteSessionService.addActivity(session.getShareToken(), session.getManagerToken(), add);

        assertThat(voteSessionService.getActivities(session.getShareToken()))
                .extracting(VoteActivityResponse::getName).containsExactly("AK-47 shooting", "Pub golf");
    }

    @Test
    void addActivity_afterVotingClosed_isRejected() {
        VoteSessionResponse session = createVote(shooting);
        VoteSession entity = voteSessionRepository.findByShareToken(session.getShareToken()).orElseThrow();
        voteSessionService.processSession(entity);
        VoteSessionActivityAddRequest add = new VoteSessionActivityAddRequest();
        add.setActivityId(pubGolf.getId());

        assertThatThrownBy(() -> voteSessionService.addActivity(session.getShareToken(), session.getManagerToken(), add))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateContact_addsAnEmailToAWhatsAppVote_withoutReplacingTheNumber() {
        VoteSessionCartCreateRequest request = cartRequest(shooting);
        request.setInitiatorEmail(null);
        request.setInitiatorPhone("+447700900123");
        VoteSessionResponse session = voteSessionService.createCartSession(request);
        VoteSessionContactRequest contact = new VoteSessionContactRequest();
        contact.setInitiatorEmail(" organiser@example.com ");
        contact.setInitiatorPhone("+420602123456");

        voteSessionService.updateContact(session.getShareToken(), session.getManagerToken(), contact);

        VoteSession entity = voteSessionRepository.findByShareToken(session.getShareToken()).orElseThrow();
        assertThat(entity.getInitiatorEmail()).isEqualTo("organiser@example.com");
        assertThat(entity.getEmailCapturedAt()).isNotNull();
        assertThat(entity.getInitiatorPhone()).isEqualTo("+447700900123");
    }

    @Test
    void updateContact_wrongManagerToken_isRejected() {
        VoteSessionResponse session = createVote(shooting);
        VoteSessionContactRequest contact = new VoteSessionContactRequest();
        contact.setInitiatorPhone("+447700900123");

        assertThatThrownBy(() -> voteSessionService.updateContact(session.getShareToken(), UUID.randomUUID(), contact))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createCartSession_usesTheLinkTokenTheBrowserPicked_andRefusesAReusedOne() {
        UUID shareToken = UUID.randomUUID();
        VoteSessionCartCreateRequest request = cartRequest(shooting);
        request.setShareToken(shareToken);

        VoteSessionResponse session = voteSessionService.createCartSession(request);

        assertThat(session.getShareToken()).isEqualTo(shareToken);
        VoteSessionCartCreateRequest again = cartRequest(shooting);
        again.setShareToken(shareToken);
        assertThatThrownBy(() -> voteSessionService.createCartSession(again)).isInstanceOf(ConflictException.class);
    }

    @Test
    void getSession_returnsTripDatesForTheFriendScreens() {
        VoteSessionResponse session = createVote(shooting);

        VoteSessionResponse read = voteSessionService.getSession(session.getShareToken());

        assertThat(read.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(read.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 18));
    }

    private VoteSessionResponse createVote(Activity... activities) {
        return voteSessionService.createCartSession(cartRequest(activities));
    }

    private VoteSessionCartCreateRequest cartRequest(Activity... activities) {
        VoteSessionCartCreateRequest request = new VoteSessionCartCreateRequest();
        request.setDestinationId(prague.getId());
        request.setInitiatorEmail("initiator+" + UUID.randomUUID() + "@example.com");
        request.setNumberOfTravelers(10);
        request.setStartDate(LocalDate.of(2026, 10, 16));
        request.setEndDate(LocalDate.of(2026, 10, 18));
        request.setActivityIds(List.of(activities).stream().map(Activity::getId).toList());
        return request;
    }

    private static VoteBatchRequest ballot(UUID voterToken, List<UUID> liked, List<UUID> recommended) {
        VoteBatchRequest batch = new VoteBatchRequest();
        batch.setVoterToken(voterToken);
        batch.setVotes(liked.stream().map(id -> {
            VoteBatchRequest.VoteItem item = new VoteBatchRequest.VoteItem();
            item.setActivityId(id);
            item.setLiked(true);
            return item;
        }).toList());
        batch.setRecommendedActivityIds(recommended);
        return batch;
    }
}
