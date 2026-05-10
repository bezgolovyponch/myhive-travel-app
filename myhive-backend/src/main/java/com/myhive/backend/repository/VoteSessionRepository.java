package com.myhive.backend.repository;

import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.VoteSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoteSessionRepository extends JpaRepository<VoteSession, UUID> {

    Optional<VoteSession> findByShareToken(UUID shareToken);

    List<VoteSession> findByStatusAndExpiresAtBefore(VoteSessionStatus status, LocalDateTime time);

    @Modifying
    @Transactional
    @Query("DELETE FROM VoteSession s WHERE s.status = :status AND s.expiresAt < :cutoff")
    int deleteByStatusAndExpiresAtBefore(
            @Param("status") VoteSessionStatus status,
            @Param("cutoff") LocalDateTime cutoff);
}
