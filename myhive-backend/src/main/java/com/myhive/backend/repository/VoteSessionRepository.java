package com.myhive.backend.repository;

import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.VoteSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** Organizer halfway email candidates: open, emailed, not yet notified — the count check happens in Java. */
    List<VoteSession> findByStatusAndInitiatorEmailIsNotNullAndHalfwayEmailSentAtIsNull(VoteSessionStatus status);

    /** Organizer reminder candidates: open, emailed, not yet reminded, closing before {@code cutoff}. */
    List<VoteSession> findByStatusAndInitiatorEmailIsNotNullAndReminderEmailSentAtIsNullAndExpiresAtBefore(
            VoteSessionStatus status, LocalDateTime cutoff);

    /** Pessimistic write lock to serialize deposit creation per session, preventing duplicate
     *  bookings/checkout sessions from a double-submit (M1). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VoteSession v where v.id = :id")
    Optional<VoteSession> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query("DELETE FROM VoteSession s WHERE s.status = :status AND s.expiresAt < :cutoff")
    int deleteByStatusAndExpiresAtBefore(
            @Param("status") VoteSessionStatus status,
            @Param("cutoff") LocalDateTime cutoff);

    /** Bulk-removes join rows; the join table has no entity, so a native query is required. */
    @Modifying
    @Query(value = "DELETE FROM vote_session_liked_categories WHERE category_id = :categoryId", nativeQuery = true)
    void deleteLikedCategoryLinks(@Param("categoryId") UUID categoryId);

    /** Trip-lead reminder stop condition: the lead started a vote at or after being captured. */
    boolean existsByInitiatorEmailIgnoreCaseAndCreatedAtGreaterThanEqual(String initiatorEmail, LocalDateTime createdAt);
}
