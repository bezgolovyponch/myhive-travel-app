package com.myhive.backend.repository;

import com.myhive.backend.entity.AiGeneration;
import com.myhive.backend.entity.AiGenerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiGenerationRepository extends JpaRepository<AiGeneration, UUID> {

    Optional<AiGeneration> findFirstBySessionIdOrderByCreatedAtDesc(UUID sessionId);

    boolean existsBySessionIdAndStatusIn(UUID sessionId, Collection<AiGenerationStatus> statuses);

    List<AiGeneration> findByStatusAndStartedAtBefore(AiGenerationStatus status, LocalDateTime before);

    int deleteBySessionId(UUID sessionId);
}
