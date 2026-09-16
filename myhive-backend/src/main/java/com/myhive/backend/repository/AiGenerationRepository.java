package com.myhive.backend.repository;

import com.myhive.backend.entity.AiGeneration;
import com.myhive.backend.entity.AiGenerationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiGenerationRepository extends JpaRepository<AiGeneration, UUID> {

    Optional<AiGeneration> findFirstBySessionIdOrderByCreatedAtDesc(UUID sessionId);

    /**
     * Generation jobs run on a pool thread with no open persistence context, so the session they
     * need for the graph thread id and the status flip has to come back already loaded.
     */
    @EntityGraph(attributePaths = "session")
    Optional<AiGeneration> findWithSessionById(UUID id);

    boolean existsBySessionIdAndStatusIn(UUID sessionId, Collection<AiGenerationStatus> statuses);

    List<AiGeneration> findByStatusAndStartedAtBefore(AiGenerationStatus status, LocalDateTime before);

    /**
     * The QUEUED half of the stale sweep. A row orphaned before its pool thread ever ran has no
     * {@code startedAt} to age on, so it has to be aged on {@code createdAt} instead.
     */
    List<AiGeneration> findByStatusAndCreatedAtBefore(AiGenerationStatus status, LocalDateTime before);

    int deleteBySessionId(UUID sessionId);
}
