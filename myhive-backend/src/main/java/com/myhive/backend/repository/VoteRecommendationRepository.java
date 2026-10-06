package com.myhive.backend.repository;

import com.myhive.backend.entity.VoteRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VoteRecommendationRepository extends JpaRepository<VoteRecommendation, UUID> {

    boolean existsBySessionIdAndVoterToken(UUID sessionId, UUID voterToken);

    /** One row per recommended activity with how many friends recommended it, most recommended first. */
    @Query("""
            SELECT r.activity.id AS activityId, COUNT(r) AS recommendationCount
            FROM VoteRecommendation r
            WHERE r.session.id = :sessionId
            GROUP BY r.activity.id
            ORDER BY COUNT(r) DESC
            """)
    List<ActivityRecommendationCount> countBySessionId(@Param("sessionId") UUID sessionId);
}
