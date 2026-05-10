package com.myhive.backend.service;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityLikeCount;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteSessionSchedulerTest {

    @Mock private VoteSessionRepository voteSessionRepository;
    @Mock private VoteActivityLikeRepository voteActivityLikeRepository;
    @Mock private VoteSessionResultActivityRepository resultActivityRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private VoteSessionScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "emailEnabled", false);
        ReflectionTestUtils.setField(scheduler, "siteUrl", "https://trivlu.com");
    }

    @Test
    void processSession_selectsActivitiesByLikesWithinBudget() {
        // 2 days = 960 min budget. A=480min, B=480min, C=480min (pre-sorted by likes desc)
        // Expected: A and B selected (960min consumed), C excluded (budget = 0 remaining)
        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setStartDate(LocalDate.of(2026, 7, 1));
        session.setEndDate(LocalDate.of(2026, 7, 2));

        UUID actAId = UUID.randomUUID();
        UUID actBId = UUID.randomUUID();

        Activity actA = new Activity(); actA.setId(actAId); actA.setDuration(480);
        Activity actB = new Activity(); actB.setId(actBId); actB.setDuration(480);

        ActivityLikeCount likeA = mock(ActivityLikeCount.class);
        when(likeA.getActivityId()).thenReturn(actAId);
        when(likeA.getDuration()).thenReturn(480);

        ActivityLikeCount likeB = mock(ActivityLikeCount.class);
        when(likeB.getActivityId()).thenReturn(actBId);
        when(likeB.getDuration()).thenReturn(480);

        ActivityLikeCount likeC = mock(ActivityLikeCount.class);
        when(likeC.getDuration()).thenReturn(480);

        when(voteActivityLikeRepository.findLikedActivitiesWithCounts(session.getId()))
                .thenReturn(List.of(likeA, likeB, likeC));
        when(activityRepository.findById(actAId)).thenReturn(Optional.of(actA));
        when(activityRepository.findById(actBId)).thenReturn(Optional.of(actB));
        when(voteSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduler.processSession(session);

        ArgumentCaptor<VoteSessionResultActivity> captor =
                ArgumentCaptor.forClass(VoteSessionResultActivity.class);
        verify(resultActivityRepository, times(2)).save(captor.capture());

        List<VoteSessionResultActivity> saved = captor.getAllValues();
        assertThat(saved.get(0).getActivity().getId()).isEqualTo(actAId);
        assertThat(saved.get(0).getSortOrder()).isEqualTo(0);
        assertThat(saved.get(1).getActivity().getId()).isEqualTo(actBId);
        assertThat(saved.get(1).getSortOrder()).isEqualTo(1);
        assertThat(session.getStatus()).isEqualTo(VoteSessionStatus.COMPLETED);
    }

    @Test
    void processSession_completesWithEmptyResultWhenNoLikes() {
        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setStartDate(LocalDate.of(2026, 7, 1));
        session.setEndDate(LocalDate.of(2026, 7, 3));

        when(voteActivityLikeRepository.findLikedActivitiesWithCounts(session.getId()))
                .thenReturn(List.of());
        when(voteSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduler.processSession(session);

        verify(resultActivityRepository, never()).save(any());
        assertThat(session.getStatus()).isEqualTo(VoteSessionStatus.COMPLETED);
    }

    @Test
    void processSession_skipsActivityWithNullDuration() {
        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setStartDate(LocalDate.of(2026, 7, 1));
        session.setEndDate(LocalDate.of(2026, 7, 1));

        ActivityLikeCount like = mock(ActivityLikeCount.class);
        when(like.getDuration()).thenReturn(null);

        when(voteActivityLikeRepository.findLikedActivitiesWithCounts(session.getId()))
                .thenReturn(List.of(like));
        when(voteSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduler.processSession(session);

        verify(resultActivityRepository, never()).save(any());
        assertThat(session.getStatus()).isEqualTo(VoteSessionStatus.COMPLETED);
    }
}
