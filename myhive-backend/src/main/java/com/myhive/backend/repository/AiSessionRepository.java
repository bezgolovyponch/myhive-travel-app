package com.myhive.backend.repository;

import com.myhive.backend.entity.AiSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiSessionRepository extends JpaRepository<AiSession, UUID> {

    /**
     * The destination comes along: a planner turn runs outside a transaction (the model call takes
     * seconds), so anything the response needs must be loaded while the query's own one is open.
     */
    @EntityGraph(attributePaths = "destination")
    Optional<AiSession> findByToken(UUID token);

    List<AiSession> findByLastActivityAtBefore(LocalDateTime cutoff);
}
