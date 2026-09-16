package com.myhive.backend.ai.service;

import com.myhive.backend.ai.graph.CheckpointRetention;
import com.myhive.backend.ai.graph.PlannerGraph;
import com.myhive.backend.ai.llm.AiProperties;
import com.myhive.backend.entity.AiSession;
import com.myhive.backend.repository.AiGenerationRepository;
import com.myhive.backend.repository.AiSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCleanupSchedulerTest {

    private final AiProperties properties = new AiProperties();
    private final AiSessionRepository sessionRepository = mock(AiSessionRepository.class);
    private final AiGenerationRepository generationRepository = mock(AiGenerationRepository.class);
    private final PlannerGraph graph = mock(PlannerGraph.class);
    private final CheckpointRetention checkpointRetention = mock(CheckpointRetention.class);
    private final SessionLocks sessionLocks = mock(SessionLocks.class);
    /** Stands in for the transactional proxy Spring injects; wired to the scheduler itself in setUp. */
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AiCleanupScheduler> self = mock(ObjectProvider.class);
    private final AiCleanupScheduler scheduler = new AiCleanupScheduler(properties, sessionRepository,
            generationRepository, graph, checkpointRetention, sessionLocks, self);

    @BeforeEach
    void wireSelfProvider() {
        when(self.getObject()).thenReturn(scheduler);
    }

    private static AiSession expiredSession() {
        AiSession session = new AiSession();
        session.setId(UUID.randomUUID());
        session.setToken(UUID.randomUUID());
        return session;
    }

    @Test
    void cleanupExpiredSessions_deletesRowsFirst_thenForgetsTheGraphThreadAndTheLock() {
        AiSession expectedSession = expiredSession();
        when(sessionRepository.findByLastActivityAtBefore(any())).thenReturn(List.of(expectedSession));

        scheduler.cleanupExpiredSessions();

        InOrder order = inOrder(generationRepository, sessionRepository, graph, checkpointRetention, sessionLocks);
        // Generations first: the FK points at the session row.
        order.verify(generationRepository).deleteBySessionId(expectedSession.getId());
        order.verify(sessionRepository).deleteById(expectedSession.getId());
        // Releasing a thread cannot be undone, so it happens only after the rows are committed away.
        order.verify(graph).release(expectedSession.getToken());
        // release() only flags the thread released; its checkpoint rows would otherwise never go.
        order.verify(checkpointRetention).deleteThread(expectedSession.getToken());
        // Without this the lock map grows for the life of the process.
        order.verify(sessionLocks).release(expectedSession.getToken());
    }

    @Test
    void cleanupExpiredSessions_whenTheCheckpointDeleteFails_doesNotAbortTheLoop() {
        AiSession failing = expiredSession();
        AiSession expectedSurvivor = expiredSession();
        when(sessionRepository.findByLastActivityAtBefore(any())).thenReturn(List.of(failing, expectedSurvivor));
        doThrow(new IllegalStateException("checkpoint table unreachable")).when(checkpointRetention)
                .deleteThread(failing.getToken());

        assertThatCode(() -> scheduler.cleanupExpiredSessions()).doesNotThrowAnyException();

        verify(checkpointRetention).deleteThread(expectedSurvivor.getToken());
        verify(sessionLocks).release(failing.getToken());
        verify(sessionLocks).release(expectedSurvivor.getToken());
    }

    @Test
    void cleanupExpiredSessions_usesTheConfiguredTtlAsCutoff() {
        int expectedTtlDays = 7;
        properties.setSessionTtlDays(expectedTtlDays);
        when(sessionRepository.findByLastActivityAtBefore(any())).thenReturn(List.of());

        scheduler.cleanupExpiredSessions();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.captor();
        verify(sessionRepository).findByLastActivityAtBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isCloseTo(LocalDateTime.now(ZoneOffset.UTC).minusDays(expectedTtlDays),
                within(1, ChronoUnit.MINUTES));
    }

    @Test
    void cleanupExpiredSessions_releaseFailure_doesNotAbortTheLoop() {
        AiSession failing = expiredSession();
        AiSession expectedSurvivor = expiredSession();
        when(sessionRepository.findByLastActivityAtBefore(any())).thenReturn(List.of(failing, expectedSurvivor));
        doThrow(new IllegalStateException("thread already gone")).when(graph).release(failing.getToken());

        assertThatCode(() -> scheduler.cleanupExpiredSessions()).doesNotThrowAnyException();

        verify(sessionRepository).deleteById(expectedSurvivor.getId());
        verify(graph).release(expectedSurvivor.getToken());
        // A dead graph thread must not keep the lock entry alive either.
        verify(sessionLocks).release(failing.getToken());
        verify(sessionLocks).release(expectedSurvivor.getToken());
    }

    @Test
    void cleanupExpiredSessions_whenDeletionFails_keepsTheThreadAndLockAndCarriesOn() {
        AiSession failing = expiredSession();
        AiSession expectedSurvivor = expiredSession();
        when(sessionRepository.findByLastActivityAtBefore(any())).thenReturn(List.of(failing, expectedSurvivor));
        doThrow(new IllegalStateException("row is locked")).when(generationRepository)
                .deleteBySessionId(failing.getId());

        assertThatCode(() -> scheduler.cleanupExpiredSessions()).doesNotThrowAnyException();

        // Its rows survived the rollback, so the chat is still resumable and must keep all of them.
        verify(graph, never()).release(failing.getToken());
        verify(checkpointRetention, never()).deleteThread(failing.getToken());
        verify(sessionLocks, never()).release(failing.getToken());
        // One bad session does not cost the rest of the batch.
        verify(sessionRepository).deleteById(expectedSurvivor.getId());
        verify(graph).release(expectedSurvivor.getToken());
        verify(sessionLocks).release(expectedSurvivor.getToken());
    }
}
