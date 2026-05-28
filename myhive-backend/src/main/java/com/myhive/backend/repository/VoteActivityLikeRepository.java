package com.myhive.backend.repository;

import com.myhive.backend.entity.VoteActivityLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoteActivityLikeRepository extends JpaRepository<VoteActivityLike, UUID> {

    Optional<VoteActivityLike> findBySessionIdAndVoterTokenAndActivityId(
            UUID sessionId, UUID voterToken, UUID activityId);

    boolean existsBySessionIdAndVoterToken(UUID sessionId, UUID voterToken);

    @Query("SELECT COUNT(DISTINCT l.voterToken) FROM VoteActivityLike l WHERE l.session.id = :sessionId")
    long countDistinctVoterTokensBySessionId(@Param("sessionId") UUID sessionId);

    @Query("""
            SELECT l.activity.id AS activityId, l.activity.duration AS duration, COUNT(l) AS likeCount
            FROM VoteActivityLike l
            WHERE l.session.id = :sessionId AND l.liked = true
            GROUP BY l.activity.id, l.activity.duration
            ORDER BY likeCount DESC
            """)
    List<ActivityLikeCount> findLikedActivitiesWithCounts(@Param("sessionId") UUID sessionId);

    @Query("""
            SELECT l.activity.id AS activityId,
                   SUM(CASE WHEN l.liked = true THEN 1 ELSE 0 END) AS likeCount,
                   SUM(CASE WHEN l.liked = false THEN 1 ELSE 0 END) AS skipCount
            FROM VoteActivityLike l
            WHERE l.session.id = :sessionId
            GROUP BY l.activity.id
            """)
    List<ActivityVoteCount> findVoteCountsBySessionId(@Param("sessionId") UUID sessionId);
}
