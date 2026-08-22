package com.myhive.backend.repository;

import com.myhive.backend.entity.VoteSessionOpen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VoteSessionOpenRepository extends JpaRepository<VoteSessionOpen, UUID> {
    boolean existsBySessionIdAndVoterToken(UUID sessionId, UUID voterToken);
    long countBySessionId(UUID sessionId);
}
