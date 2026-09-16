package com.myhive.backend.repository;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.AiGeneration;
import com.myhive.backend.entity.AiGenerationStatus;
import com.myhive.backend.entity.AiSession;
import com.myhive.backend.entity.AiSessionStatus;
import com.myhive.backend.entity.Destination;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class AiRepositoriesTest {

    @Autowired
    private DestinationRepository destinationRepository;
    @Autowired
    private AiSessionRepository sessionRepository;
    @Autowired
    private AiGenerationRepository generationRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private AiSession newSession() {
        Destination destination = destinationRepository.save(TestDataFactory.destination("Prague"));
        AiSession s = new AiSession();
        s.setToken(UUID.randomUUID());
        s.setDestination(destination);
        s.setLocale("en");
        s.setStatus(AiSessionStatus.COLLECTING);
        s.setCreatedAt(LocalDateTime.now());
        s.setLastActivityAt(LocalDateTime.now());
        return sessionRepository.save(s);
    }

    @Test
    void findByToken_andLatestGeneration() {
        AiSession session = newSession();
        AiGeneration older = new AiGeneration();
        older.setSession(session);
        older.setStatus(AiGenerationStatus.FAILED);
        older.setBriefSnapshot("{}");
        older.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        generationRepository.save(older);
        AiGeneration expectedLatest = new AiGeneration();
        expectedLatest.setSession(session);
        expectedLatest.setStatus(AiGenerationStatus.QUEUED);
        expectedLatest.setBriefSnapshot("{}");
        expectedLatest.setCreatedAt(LocalDateTime.now());
        generationRepository.save(expectedLatest);

        assertThat(sessionRepository.findByToken(session.getToken())).isPresent();
        assertThat(generationRepository.findFirstBySessionIdOrderByCreatedAtDesc(session.getId()))
                .map(AiGeneration::getId).contains(expectedLatest.getId());
        assertThat(generationRepository.existsBySessionIdAndStatusIn(session.getId(),
                List.of(AiGenerationStatus.QUEUED, AiGenerationStatus.RUNNING))).isTrue();
    }

    @Test
    void staleRunningGenerations_areFoundByStartedAt() {
        AiSession session = newSession();
        AiGeneration stale = new AiGeneration();
        stale.setSession(session);
        stale.setStatus(AiGenerationStatus.RUNNING);
        stale.setBriefSnapshot("{}");
        stale.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        stale.setStartedAt(LocalDateTime.now().minusMinutes(10));
        generationRepository.save(stale);

        assertThat(generationRepository.findByStatusAndStartedAtBefore(AiGenerationStatus.RUNNING,
                LocalDateTime.now().minusMinutes(3))).hasSize(1);
    }

    @Test
    void findByLastActivityAtBefore_returnsOnlyStaleSessions() {
        AiSession expectedStaleSession = newSession();
        expectedStaleSession.setLastActivityAt(LocalDateTime.now().minusDays(40));
        sessionRepository.saveAndFlush(expectedStaleSession);
        AiSession freshSession = newSession();
        freshSession.setLastActivityAt(LocalDateTime.now());
        sessionRepository.saveAndFlush(freshSession);

        List<AiSession> staleSessions = sessionRepository.findByLastActivityAtBefore(LocalDateTime.now().minusDays(30));

        assertThat(staleSessions).extracting(AiSession::getId).containsExactly(expectedStaleSession.getId());
    }

    @Test
    void deleteBySessionId_removesGenerationsAndReturnsCount() {
        AiSession session = newSession();
        AiGeneration first = new AiGeneration();
        first.setSession(session);
        first.setStatus(AiGenerationStatus.FAILED);
        first.setBriefSnapshot("{}");
        first.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        generationRepository.saveAndFlush(first);
        AiGeneration second = new AiGeneration();
        second.setSession(session);
        second.setStatus(AiGenerationStatus.QUEUED);
        second.setBriefSnapshot("{}");
        second.setCreatedAt(LocalDateTime.now());
        generationRepository.saveAndFlush(second);
        int expectedDeletedCount = 2;

        int deletedCount = generationRepository.deleteBySessionId(session.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedCount).isEqualTo(expectedDeletedCount);
        assertThat(generationRepository.findFirstBySessionIdOrderByCreatedAtDesc(session.getId())).isEmpty();
    }
}
