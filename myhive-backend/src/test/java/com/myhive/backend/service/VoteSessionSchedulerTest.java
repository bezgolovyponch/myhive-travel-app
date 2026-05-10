package com.myhive.backend.service;

import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteSessionSchedulerTest {

    @Mock private VoteSessionRepository voteSessionRepository;
    @Mock private VoteSessionService voteSessionService;

    @InjectMocks
    private VoteSessionScheduler scheduler;

    @Test
    void processExpiredSessions_delegatesToServiceForEachSession() {
        VoteSession session1 = new VoteSession();
        session1.setId(UUID.randomUUID());
        session1.setStatus(VoteSessionStatus.ACTIVE);
        session1.setStartDate(LocalDate.of(2026, 7, 1));
        session1.setEndDate(LocalDate.of(2026, 7, 2));

        VoteSession session2 = new VoteSession();
        session2.setId(UUID.randomUUID());
        session2.setStatus(VoteSessionStatus.ACTIVE);
        session2.setStartDate(LocalDate.of(2026, 7, 3));
        session2.setEndDate(LocalDate.of(2026, 7, 5));

        when(voteSessionRepository.findByStatusAndExpiresAtBefore(eq(VoteSessionStatus.ACTIVE), any()))
                .thenReturn(List.of(session1, session2));

        scheduler.processExpiredSessions();

        verify(voteSessionService, times(2)).processSession(any());
        verify(voteSessionService).processSession(session1);
        verify(voteSessionService).processSession(session2);
    }

    @Test
    void processExpiredSessions_continuesOnError() {
        VoteSession session1 = new VoteSession();
        session1.setId(UUID.randomUUID());
        session1.setStatus(VoteSessionStatus.ACTIVE);
        session1.setStartDate(LocalDate.of(2026, 7, 1));
        session1.setEndDate(LocalDate.of(2026, 7, 2));

        VoteSession session2 = new VoteSession();
        session2.setId(UUID.randomUUID());
        session2.setStatus(VoteSessionStatus.ACTIVE);
        session2.setStartDate(LocalDate.of(2026, 7, 3));
        session2.setEndDate(LocalDate.of(2026, 7, 5));

        when(voteSessionRepository.findByStatusAndExpiresAtBefore(eq(VoteSessionStatus.ACTIVE), any()))
                .thenReturn(List.of(session1, session2));
        doThrow(new RuntimeException("Processing error")).when(voteSessionService).processSession(session1);

        scheduler.processExpiredSessions();

        verify(voteSessionService).processSession(session1);
        verify(voteSessionService).processSession(session2);
    }

    @Test
    void cleanupOldSessions_deletesOldCompletedSessions() {
        when(voteSessionRepository.deleteByStatusAndExpiresAtBefore(eq(VoteSessionStatus.COMPLETED), any()))
                .thenReturn(5);

        scheduler.cleanupOldSessions();

        verify(voteSessionRepository).deleteByStatusAndExpiresAtBefore(eq(VoteSessionStatus.COMPLETED), any());
    }
}
