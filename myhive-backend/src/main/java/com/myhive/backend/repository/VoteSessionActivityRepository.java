package com.myhive.backend.repository;

import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.model.VoteSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VoteSessionActivityRepository extends JpaRepository<VoteSessionActivity, UUID> {

    List<VoteSessionActivity> findBySessionIdOrderBySortOrder(UUID sessionId);

    boolean existsByActivityIdAndSession_StatusIn(UUID activityId, Collection<VoteSessionStatus> statuses);
}
