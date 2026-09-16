package com.myhive.backend.ai.service;

import com.myhive.backend.ai.graph.PlannerGraph;
import com.myhive.backend.ai.llm.AiProperties;
import com.myhive.backend.entity.AiSession;
import com.myhive.backend.repository.AiGenerationRepository;
import com.myhive.backend.repository.AiSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCleanupSchedulerTest {

    private final AiProperties properties = new AiProperties();
    private final AiSessionRepository sessionRepository = mock(AiSessionRepository.class);
    private final AiGenerationRepository generationRepository = mock(AiGenerationRepository.class);
    private final PlannerGraph graph = mock(PlannerGraph.class);
    private final SessionLocks sessionLocks = mock(SessionLocks.class);
    private final AiCleanupScheduler scheduler =
            new AiCleanupScheduler(properties, sessionRepository, generationRepository, graph, sessionLocks);

    private static AiSession expiredSession() {
        AiSession session = new AiSession();
        session.setId(UUID.randomUUID());
        session.setToken(UUID.randomUUID());
        return session;
    }

    @Test
    void cleanupExpiredSessions_deletesRowsAndForgetsTheGraphThreadAndTheLock() {
        AiSession expectedSession = expiredSession();
        when(sessionRepository.findByLastActivityAtBefore(any())).thenReturn(List.of(expectedSession));

        scheduler.cleanupExpiredSessions();

        InOrder order = inOrder(generationRepository, sessionRepository, graph, sessionLocks);
        // Generations first: the FK points at the session row.
        order.verify(generationRepository).deleteBySessionId(expectedSession.getId());
        order.verify(sessionRepository).delete(expectedSession);
        order.verify(graph).release(expectedSession.getToken());
        // Without this the lock map grows for the life of the process.
        order.verify(sessionLocks).release(expectedSession.getToken());
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

        verify(sessionRepository).delete(expectedSurvivor);
        verify(graph).release(expectedSurvivor.getToken());
        // A dead graph thread must not keep the lock entry alive either.
        verify(sessionLocks).release(failing.getToken());
        verify(sessionLocks).release(expectedSurvivor.getToken());
    }
}
