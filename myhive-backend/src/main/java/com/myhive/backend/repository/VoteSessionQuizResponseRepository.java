package com.myhive.backend.repository;

import com.myhive.backend.entity.VoteSessionQuizResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VoteSessionQuizResponseRepository extends JpaRepository<VoteSessionQuizResponse, UUID> {

    List<VoteSessionQuizResponse> findBySessionId(UUID sessionId);
}
