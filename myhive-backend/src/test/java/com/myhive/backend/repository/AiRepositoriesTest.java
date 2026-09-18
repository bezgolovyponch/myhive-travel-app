package com.myhive.backend.repository;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.AiGeneration;
import com.myhive.backend.entity.AiGenerationKind;
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
        assertThat(generationRepository.findFirstBySessionIdOrderByCreatedAtDescIdDesc(session.getId()))
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

    /**
     * The child row and its {@code parent_id}. The column is a plain UUID on the entity — there is no JPA
     * relation — so nothing here enforces that the parent exists: on Postgres the FK and its
     * {@code ON DELETE SET NULL} come from {@code V8__ai_planner_edits.sql}, which H2 never runs
     * (dev and tests keep Flyway off and build the schema from the entities).
     */
    @Test
    void editedGeneration_isTheNewestReady_andKeepsItsParentLink() {
        AiSession session = newSession();
        AiGeneration generated = new AiGeneration();
        generated.setSession(session);
        generated.setStatus(AiGenerationStatus.READY);
        generated.setBriefSnapshot("{}");
        generated.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        generationRepository.saveAndFlush(generated);
        String expectedEditReport = "{\"applied\":[],\"rejected\":[],\"tierRulesRelaxed\":false,\"textsRefreshed\":false}";
        AiGeneration expectedEdited = new AiGeneration();
        expectedEdited.setSession(session);
        expectedEdited.setStatus(AiGenerationStatus.READY);
        expectedEdited.setBriefSnapshot("{}");
        expectedEdited.setKind(AiGenerationKind.EDITED);
        expectedEdited.setParentId(generated.getId());
        expectedEdited.setEditReport(expectedEditReport);
        expectedEdited.setCreatedAt(LocalDateTime.now());
        generationRepository.saveAndFlush(expectedEdited);
        entityManager.clear();

        assertThat(generationRepository.findFirstBySessionIdAndStatusOrderByCreatedAtDescIdDesc(session.getId(),
                AiGenerationStatus.READY)).map(AiGeneration::getId).contains(expectedEdited.getId());
        AiGeneration reloadedGenerated = generationRepository.findById(generated.getId()).orElseThrow();
        AiGeneration reloadedEdited = generationRepository.findById(expectedEdited.getId()).orElseThrow();
        assertThat(reloadedGenerated.getKind()).isEqualTo(AiGenerationKind.GENERATED);
        assertThat(reloadedGenerated.getParentId()).isNull();
        assertThat(reloadedEdited.getKind()).isEqualTo(AiGenerationKind.EDITED);
        assertThat(reloadedEdited.getParentId()).isEqualTo(generated.getId());
        assertThat(reloadedEdited.getEditReport()).isEqualTo(expectedEditReport);
    }

    /**
     * An edit turn stamps its row with {@code LocalDateTime.now()} while the parent it hangs off may have
     * been written in the very same tick, and "the newest generation" then decided which packages a token
     * restore came back to. The id settles it, so the answer is at least deterministic rather than a coin
     * toss between a plan and its edit.
     */
    @Test
    void generationsWrittenInTheSameTick_areOrderedDeterministically() {
        AiSession session = newSession();
        LocalDateTime sameInstant = LocalDateTime.now();
        AiGeneration first = readyGeneration(session, sameInstant);
        AiGeneration second = readyGeneration(session, sameInstant);
        // Compared as text, not with UUID.compareTo: the database orders the 16 bytes unsigned, while
        // compareTo treats the halves as signed longs, so the two disagree on every id with the high bit
        // set. Hex string order is the unsigned byte order.
        UUID expectedNewestId = first.getId().toString().compareTo(second.getId().toString()) > 0
                ? first.getId()
                : second.getId();
        entityManager.clear();

        assertThat(generationRepository.findFirstBySessionIdOrderByCreatedAtDescIdDesc(session.getId()))
                .map(AiGeneration::getId).contains(expectedNewestId);
        assertThat(generationRepository.findFirstBySessionIdAndStatusOrderByCreatedAtDescIdDesc(session.getId(),
                AiGenerationStatus.READY)).map(AiGeneration::getId).contains(expectedNewestId);
    }

    private AiGeneration readyGeneration(AiSession session, LocalDateTime createdAt) {
        AiGeneration generation = new AiGeneration();
        generation.setSession(session);
        generation.setStatus(AiGenerationStatus.READY);
        generation.setBriefSnapshot("{}");
        generation.setCreatedAt(createdAt);
        return generationRepository.saveAndFlush(generation);
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
        assertThat(generationRepository.findFirstBySessionIdOrderByCreatedAtDescIdDesc(session.getId())).isEmpty();
    }
}
