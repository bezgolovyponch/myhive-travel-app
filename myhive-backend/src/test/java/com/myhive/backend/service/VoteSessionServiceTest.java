package com.myhive.backend.service;

import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.dto.VoteRequest;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.EmailSendException;
import com.myhive.backend.exception.SessionFullException;
import com.myhive.backend.model.VoteMode;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteSessionServiceTest {

    @Mock private VoteSessionRepository voteSessionRepository;
    @Mock private VoteActivityLikeRepository voteActivityLikeRepository;
    @Mock private DestinationRepository destinationRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private EmailService emailService;
    @Mock private com.myhive.backend.service.QuizService quizService;
    @Mock private com.myhive.backend.repository.QuizQuestionRepository quizQuestionRepository;
    @Mock private com.myhive.backend.repository.QuizAnswerRepository quizAnswerRepository;
    @Mock private com.myhive.backend.repository.VoteSessionActivityRepository voteSessionActivityRepository;
    @Mock private com.myhive.backend.repository.VoteSessionQuizResponseRepository voteSessionQuizResponseRepository;
    @Mock private com.myhive.backend.repository.VoteSessionResultActivityRepository resultActivityRepository;
    @Mock private com.myhive.backend.service.VoteSuggestionsService voteSuggestionsService;
    @Mock private com.myhive.backend.service.TripLeadService tripLeadService;
    @Mock private MetaCapiService metaCapiService;

    @InjectMocks
    private VoteSessionService voteSessionService;

    // createSession_savesSessionAndReturnsShareToken and createSession_rejectsCategoryNotBelongingToDestination
    // were obsoleted by the Plan 2 createSession rewrite. The new flow (curated activityIds + organizer
    // quizResponses + name/price snapshots) is covered end-to-end in VoteSessionCreateSessionTest.

    @Test
    void createSession_rejectsEndDateBeforeStartDate() {
        UUID destId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();

        Category category = new Category();
        category.setId(catId);

        Destination destination = new Destination();
        destination.setId(destId);
        destination.setCategories(Set.of(category));

        when(destinationRepository.findById(destId)).thenReturn(Optional.of(destination));

        VoteSessionCreateRequest request = new VoteSessionCreateRequest();
        request.setDestinationId(destId);
        request.setInitiatorEmail("alice@example.com");
        request.setNumberOfTravelers(1);
        request.setStartDate(LocalDate.of(2026, 7, 7));
        request.setEndDate(LocalDate.of(2026, 7, 1));
        request.setLikedCategoryIds(List.of(catId));

        assertThatThrownBy(() -> voteSessionService.createSession(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("endDate must be on or after startDate");
    }

    @Test
    void castVote_throwsSessionFullForNewVoterAtLimit() {
        UUID shareToken = UUID.randomUUID();
        UUID voterToken = UUID.randomUUID();

        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setMaxParticipants(50);

        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));
        when(voteActivityLikeRepository.existsBySessionIdAndVoterToken(any(), any())).thenReturn(false);
        when(voteActivityLikeRepository.countDistinctVoterTokensBySessionId(any())).thenReturn(50L);

        VoteRequest request = new VoteRequest();
        request.setVoterToken(voterToken);
        request.setActivityId(UUID.randomUUID());
        request.setLiked(true);

        assertThatThrownBy(() -> voteSessionService.castVote(shareToken, request))
                .isInstanceOf(SessionFullException.class);
    }

    @Test
    void castVote_existingVoterCanUpdateVoteEvenWhenFull() {
        UUID shareToken = UUID.randomUUID();
        UUID voterToken = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();

        Destination destination = new Destination();
        destination.setId(destId);

        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setMaxParticipants(50);
        session.setDestination(destination);

        Activity activity = new Activity();
        activity.setId(activityId);
        activity.setDestination(destination);

        VoteActivityLike existing = new VoteActivityLike();
        existing.setLiked(false);

        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));
        when(voteActivityLikeRepository.existsBySessionIdAndVoterToken(any(), eq(voterToken))).thenReturn(true);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(voteActivityLikeRepository.findBySessionIdAndVoterTokenAndActivityId(any(), any(), any()))
                .thenReturn(Optional.of(existing));
        when(voteActivityLikeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        VoteRequest request = new VoteRequest();
        request.setVoterToken(voterToken);
        request.setActivityId(activityId);
        request.setLiked(true);

        voteSessionService.castVote(shareToken, request);

        assertThat(existing.getLiked()).isTrue();
    }

    @Test
    void castVotes_savesAllVotesInSingleTransaction() {
        UUID shareToken = UUID.randomUUID();
        UUID voterToken = UUID.randomUUID();
        UUID activityId1 = UUID.randomUUID();
        UUID activityId2 = UUID.randomUUID();
        UUID destId = UUID.randomUUID();

        Destination destination = new Destination();
        destination.setId(destId);

        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setMaxParticipants(50);
        session.setDestination(destination);

        Activity activity1 = new Activity();
        activity1.setId(activityId1);
        activity1.setDestination(destination);
        Activity activity2 = new Activity();
        activity2.setId(activityId2);
        activity2.setDestination(destination);

        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));
        when(voteActivityLikeRepository.existsBySessionIdAndVoterToken(any(), eq(voterToken))).thenReturn(false);
        when(voteActivityLikeRepository.countDistinctVoterTokensBySessionId(any())).thenReturn(0L);
        when(activityRepository.findAllById(any())).thenReturn(List.of(activity1, activity2));
        when(voteActivityLikeRepository.findBySessionIdAndVoterTokenAndActivityId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(voteActivityLikeRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        VoteBatchRequest.VoteItem item1 = new VoteBatchRequest.VoteItem();
        item1.setActivityId(activityId1);
        item1.setLiked(true);
        VoteBatchRequest.VoteItem item2 = new VoteBatchRequest.VoteItem();
        item2.setActivityId(activityId2);
        item2.setLiked(false);

        VoteBatchRequest request = new VoteBatchRequest();
        request.setVoterToken(voterToken);
        request.setVotes(List.of(item1, item2));

        voteSessionService.castVotes(shareToken, request);

        verify(voteActivityLikeRepository).saveAll(any());
    }

    // getResult_throwsResultNotReadyWhenActive and getResult_returnsActivitiesWithTotalPrice
    // were obsoleted by the Plan 3 getResult rewrite to the two-tier result+suggestions+budget
    // shape. The new flow is covered end-to-end in VoteSessionGetResultTest.

    @Test
    void getSession_returnsResponseWithParticipantCount() {
        UUID shareToken = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Destination destination = new Destination();
        destination.setName("Bali");
        destination.setSlug("bali");

        VoteSession session = new VoteSession();
        session.setId(sessionId);
        session.setShareToken(shareToken);
        session.setDestination(destination);
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setExpiresAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(24));

        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));
        when(voteActivityLikeRepository.countDistinctVoterTokensBySessionId(sessionId)).thenReturn(3L);

        VoteSessionResponse response = voteSessionService.getSession(shareToken);

        assertThat(response.getShareToken()).isEqualTo(shareToken);
        assertThat(response.getParticipantCount()).isEqualTo(3L);
        assertThat(response.getDestinationName()).isEqualTo("Bali");
        assertThat(response.getManagerToken()).isNull();
    }

    @Test
    void getActivities_returnsActivitiesFilteredByLikedCategories() {
        UUID shareToken = UUID.randomUUID();
        UUID destId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        String expectedActivityName = "Surfing";
        String expectedActivitySlug = "surfing-bali";
        String expectedDestinationSlug = "bali";

        Category category = new Category();
        category.setId(catId);

        Destination destination = new Destination();
        destination.setId(destId);
        destination.setSlug(expectedDestinationSlug);

        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setShareToken(shareToken);
        session.setDestination(destination);
        session.setLikedCategories(Set.of(category));
        session.setStatus(VoteSessionStatus.ACTIVE);

        Activity activity = new Activity();
        activity.setId(UUID.randomUUID());
        activity.setName(expectedActivityName);
        activity.setSlug(expectedActivitySlug);
        activity.setPrice(BigDecimal.valueOf(65));
        activity.setDuration(180);

        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));
        // No curated list → falls back to legacy category-based read.
        when(voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId())).thenReturn(List.of());
        when(activityRepository.findByDestinationIdAndCategoriesIdIn(eq(destId), any())).thenReturn(List.of(activity));

        var result = voteSessionService.getActivities(shareToken);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo(expectedActivityName);
        assertThat(result.get(0).getSlug()).isEqualTo(expectedActivitySlug);
        assertThat(result.get(0).getDestinationSlug()).isEqualTo(expectedDestinationSlug);
    }

    @Test
    void getParticipantCount_returnsDistinctVoterCount() {
        UUID shareToken = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Destination destination = new Destination();
        destination.setName("Bali");
        destination.setSlug("bali");

        VoteSession session = new VoteSession();
        session.setId(sessionId);
        session.setShareToken(shareToken);
        session.setDestination(destination);
        session.setStatus(VoteSessionStatus.ACTIVE);

        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));
        when(voteActivityLikeRepository.countDistinctVoterTokensBySessionId(sessionId)).thenReturn(7L);

        long count = voteSessionService.getParticipantCount(shareToken);

        assertThat(count).isEqualTo(7L);
    }

    @Test
    void closeSession_throwsBadRequestForInvalidManagerToken() {
        UUID shareToken = UUID.randomUUID();
        UUID correctManagerToken = UUID.randomUUID();
        UUID wrongManagerToken = UUID.randomUUID();

        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setManagerToken(correctManagerToken);

        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> voteSessionService.closeSession(shareToken, wrongManagerToken))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid manager token");
    }

    @Test
    void processSession_completesSessionEvenWhenResultEmailFails() {
        ReflectionTestUtils.setField(voteSessionService, "frontendUrl", "https://trivlu.com");

        Destination destination = new Destination();
        destination.setName("Bali");

        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setVoteMode(VoteMode.CART);
        session.setDestination(destination);
        session.setInitiatorEmail("alice@example.com");

        Activity activity = new Activity();
        activity.setId(UUID.randomUUID());
        VoteSessionActivity curatedRow = new VoteSessionActivity();
        curatedRow.setActivity(activity);
        curatedRow.setSortOrder(0);

        when(voteSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId()))
                .thenReturn(List.of(curatedRow));
        when(voteActivityLikeRepository.findVoteCountsBySessionId(session.getId())).thenReturn(List.of());
        doThrow(new RuntimeException("template rendering failed"))
                .when(emailService).sendVoteResult(any(), any(), any());

        // The ranked results are already frozen at this point — a failed notification must not
        // roll back COMPLETED, or the scheduler would retry the same failing session forever.
        assertThatCode(() -> voteSessionService.processSession(session)).doesNotThrowAnyException();

        assertThat(session.getStatus()).isEqualTo(VoteSessionStatus.COMPLETED);
        verify(voteSessionRepository).save(session);
    }

    private VoteSessionCreateRequest happyPathCreateSetup() {
        UUID destId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        Category category = new Category();
        category.setId(catId);
        category.setVotable(true);

        Destination destination = new Destination();
        destination.setId(destId);
        destination.setName("Bali");
        destination.setSlug("bali");
        destination.setCategories(Set.of(category));

        Activity activity = new Activity();
        activity.setId(activityId);
        activity.setDestination(destination);
        activity.setName("Surfing");
        activity.setPrice(new BigDecimal("65"));
        activity.setCategories(Set.of(category));

        when(destinationRepository.findById(destId)).thenReturn(Optional.of(destination));
        when(activityRepository.findAllById(any())).thenReturn(List.of(activity));
        when(voteSessionRepository.save(any())).thenAnswer(i -> {
            VoteSession saved = i.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(voteActivityLikeRepository.countDistinctVoterTokensBySessionId(any())).thenReturn(0L);

        VoteSessionCreateRequest request = new VoteSessionCreateRequest();
        request.setDestinationId(destId);
        request.setInitiatorEmail("alice@example.com");
        request.setNumberOfTravelers(2);
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setEndDate(LocalDate.of(2026, 8, 10));
        request.setVoterToken(UUID.randomUUID());
        request.setQuizResponses(List.of());
        request.setActivityIds(List.of(activityId));
        return request;
    }

    @Test
    void createSession_sendsConfirmationEmail() {
        ReflectionTestUtils.setField(voteSessionService, "frontendUrl", "https://trivlu.com");
        VoteSessionCreateRequest request = happyPathCreateSetup();

        VoteSessionResponse response = voteSessionService.createSession(request);

        assertThat(response.getShareToken()).isNotNull();
        verify(emailService).sendVoteCreatedConfirmation(any(VoteSession.class), eq("https://trivlu.com"));
    }

    @Test
    void createSession_succeedsEvenWhenConfirmationEmailFails() {
        ReflectionTestUtils.setField(voteSessionService, "frontendUrl", "https://trivlu.com");
        VoteSessionCreateRequest request = happyPathCreateSetup();
        doThrow(new EmailSendException("smtp down", null))
                .when(emailService).sendVoteCreatedConfirmation(any(), any());

        VoteSessionResponse response = voteSessionService.createSession(request);

        assertThat(response.getShareToken()).isNotNull();
    }

    @Test
    void createSession_sendsStartGroupVoteCapiEvent() {
        ReflectionTestUtils.setField(voteSessionService, "frontendUrl", "https://trivlu.com");
        VoteSessionCreateRequest request = happyPathCreateSetup();
        request.setFbp("fb.1.111.aaa");
        request.setFbc("fb.1.222.bbb");

        voteSessionService.createSession(request);

        ArgumentCaptor<MetaCapiService.MetaCapiEvent> captor =
                ArgumentCaptor.forClass(MetaCapiService.MetaCapiEvent.class);
        verify(metaCapiService).sendEvent(captor.capture());
        MetaCapiService.MetaCapiEvent event = captor.getValue();
        assertThat(event.eventName).isEqualTo("start_group_vote");
        assertThat(event.email).isEqualTo("alice@example.com");
        assertThat(event.fbp).isEqualTo("fb.1.111.aaa");
        assertThat(event.fbc).isEqualTo("fb.1.222.bbb");
    }

    private VoteSessionCartCreateRequest happyPathCartCreateSetup() {
        UUID destId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        Destination destination = new Destination();
        destination.setId(destId);
        destination.setName("Bali");
        destination.setSlug("bali");

        Activity activity = new Activity();
        activity.setId(activityId);
        activity.setDestination(destination);
        activity.setName("Surfing");
        activity.setPrice(new BigDecimal("65"));

        when(destinationRepository.findById(destId)).thenReturn(Optional.of(destination));
        when(activityRepository.findAllById(any())).thenReturn(List.of(activity));
        when(voteSessionRepository.save(any())).thenAnswer(i -> {
            VoteSession saved = i.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        VoteSessionCartCreateRequest request = new VoteSessionCartCreateRequest();
        request.setDestinationId(destId);
        request.setInitiatorEmail("bob@example.com");
        request.setNumberOfTravelers(2);
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setEndDate(LocalDate.of(2026, 8, 10));
        request.setActivityIds(List.of(activityId));
        return request;
    }

    @Test
    void createCartSession_sendsStartGroupVoteCapiEvent() {
        VoteSessionCartCreateRequest request = happyPathCartCreateSetup();
        request.setFbclid("clickid123");

        voteSessionService.createCartSession(request);

        ArgumentCaptor<MetaCapiService.MetaCapiEvent> captor =
                ArgumentCaptor.forClass(MetaCapiService.MetaCapiEvent.class);
        verify(metaCapiService).sendEvent(captor.capture());
        MetaCapiService.MetaCapiEvent event = captor.getValue();
        assertThat(event.eventName).isEqualTo("start_group_vote");
        assertThat(event.email).isEqualTo("bob@example.com");
        assertThat(event.fbc).startsWith("fb.1.").endsWith(".clickid123");
    }
}
