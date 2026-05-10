package com.myhive.backend.repository;

import com.myhive.backend.entity.VoteSessionResultActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VoteSessionResultActivityRepository extends JpaRepository<VoteSessionResultActivity, UUID> {

    List<VoteSessionResultActivity> findBySessionIdOrderBySortOrder(UUID sessionId);
}
