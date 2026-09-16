package com.myhive.backend.repository;

import com.myhive.backend.entity.AiSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiSessionRepository extends JpaRepository<AiSession, UUID> {

    Optional<AiSession> findByToken(UUID token);

    List<AiSession> findByLastActivityAtBefore(LocalDateTime cutoff);
}
