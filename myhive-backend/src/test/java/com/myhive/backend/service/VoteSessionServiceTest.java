package com.myhive.backend.service;

import com.myhive.backend.dto.VoteRequest;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.exception.SessionFullException;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.service.VoteSessionScheduler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteSessionServiceTest {

    @Mock private VoteSessionRepository voteSessionRepository;
    @Mock private VoteActivityLikeRepository voteActivityLikeRepository;
    @Mock private VoteSessionResultActivityRepository resultActivityRepository;
    @Mock private DestinationRepository destinationRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private VoteSessionScheduler voteSessionScheduler;

    @InjectMocks
    private VoteSessionService voteSessionService;

    @Test
    void createSession_savesSessionAndReturnsShareToken() {
        UUID destId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();

        Category category = new Category();
        category.setId(catId);

        Destination destination = new Destination();
        destination.setId(destId);
        destination.setName("Bali");
        destination.setSlug("bali");
        destination.setCategories(Set.of(category));

        when(destinationRepository.findById(destId)).thenReturn(Optional.of(destination));
        when(categoryRepository.findById(catId)).thenReturn(Optional.of(category));
        when(voteSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(voteActivityLikeRepository.countDistinctVoterTokensBySessionId(any())).thenReturn(0L);

        VoteSessionCreateRequest request = new VoteSessionCreateRequest();
        request.setDestinationId(destId);
        request.setInitiatorEmail("alice@example.com");
        request.setNumberOfTravelers(3);
        request.setStartDate(LocalDate.of(2026, 7, 1));
        request.setEndDate(LocalDate.of(2026, 7, 7));
        request.setLikedCategoryIds(List.of(catId));

        VoteSessionResponse response = voteSessionService.createSession(request);

        ArgumentCaptor<VoteSession> captor = ArgumentCaptor.forClass(VoteSession.class);
        verify(voteSessionRepository).save(captor.capture());
        VoteSession saved = captor.getValue();

        assertThat(saved.getInitiatorEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getStatus()).isEqualTo(VoteSessionStatus.ACTIVE);
        assertThat(saved.getShareToken()).isNotNull();
        assertThat(response.getShareToken()).isEqualTo(saved.getShareToken());
    }

    @Test
    void createSession_rejectsCategoryNotBelongingToDestination() {
        UUID destId = UUID.randomUUID();
        UUID foreignCatId = UUID.randomUUID();

        Destination destination = new Destination();
        destination.setId(destId);
        destination.setCategories(Set.of());

        when(destinationRepository.findById(destId)).thenReturn(Optional.of(destination));

        VoteSessionCreateRequest request = new VoteSessionCreateRequest();
        request.setDestinationId(destId);
        request.setInitiatorEmail("alice@example.com");
        request.setNumberOfTravelers(1);
        request.setStartDate(LocalDate.of(2026, 7, 1));
        request.setEndDate(LocalDate.of(2026, 7, 3));
        request.setLikedCategoryIds(List.of(foreignCatId));

        assertThatThrownBy(() -> voteSessionService.createSession(request))
                .isInstanceOf(BadRequestException.class);
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

        Activity activity1 = new Activity(); activity1.setId(activityId1); activity1.setDestination(destination);
        Activity activity2 = new Activity(); activity2.setId(activityId2); activity2.setDestination(destination);

        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));
        when(voteActivityLikeRepository.existsBySessionIdAndVoterToken(any(), eq(voterToken))).thenReturn(false);
        when(voteActivityLikeRepository.countDistinctVoterTokensBySessionId(any())).thenReturn(0L);
        when(activityRepository.findById(activityId1)).thenReturn(Optional.of(activity1));
        when(activityRepository.findById(activityId2)).thenReturn(Optional.of(activity2));
        when(voteActivityLikeRepository.findBySessionIdAndVoterTokenAndActivityId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(voteActivityLikeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

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

        verify(voteActivityLikeRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void getResult_throwsNotFoundWhenActive() {
        UUID shareToken = UUID.randomUUID();
        VoteSession session = new VoteSession();
        session.setStatus(VoteSessionStatus.ACTIVE);

        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> voteSessionService.getResult(shareToken))
                .isInstanceOf(ResourceNotFoundException.class);
    }

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
    }

    @Test
    void getActivities_returnsActivitiesFilteredByLikedCategories() {
        UUID shareToken = UUID.randomUUID();
        UUID destId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();

        Category category = new Category();
        category.setId(catId);

        Destination destination = new Destination();
        destination.setId(destId);

        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setShareToken(shareToken);
        session.setDestination(destination);
        session.setLikedCategories(Set.of(category));
        session.setStatus(VoteSessionStatus.ACTIVE);

        Activity activity = new Activity();
        activity.setId(UUID.randomUUID());
        activity.setName("Surfing");
        activity.setPrice(BigDecimal.valueOf(65));
        activity.setDuration(180);

        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));
        when(activityRepository.findByDestinationIdAndCategoriesIdIn(eq(destId), any())).thenReturn(List.of(activity));

        var result = voteSessionService.getActivities(shareToken);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Surfing");
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
    void getResult_returnsActivitiesWithTotalPrice() {
        UUID shareToken = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Destination destination = new Destination();
        destination.setName("Bali");
        destination.setSlug("bali");

        VoteSession session = new VoteSession();
        session.setId(sessionId);
        session.setShareToken(shareToken);
        session.setDestination(destination);
        session.setStatus(VoteSessionStatus.COMPLETED);
        session.setNumberOfTravelers(2);
        session.setStartDate(java.time.LocalDate.of(2026, 7, 1));
        session.setEndDate(java.time.LocalDate.of(2026, 7, 7));

        Activity activity = new Activity();
        activity.setId(UUID.randomUUID());
        activity.setName("Surfing");
        activity.setPrice(BigDecimal.valueOf(50));
        activity.setDuration(180);

        VoteSessionResultActivity resultActivity = new VoteSessionResultActivity();
        resultActivity.setSession(session);
        resultActivity.setActivity(activity);
        resultActivity.setSortOrder(0);

        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));
        when(resultActivityRepository.findBySessionIdOrderBySortOrder(sessionId))
                .thenReturn(List.of(resultActivity));

        var result = voteSessionService.getResult(shareToken);

        assertThat(result.getActivities()).hasSize(1);
        assertThat(result.getActivities().get(0).getName()).isEqualTo("Surfing");
        // totalPrice = 50 * 2 travelers = 100
        assertThat(result.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }
}
