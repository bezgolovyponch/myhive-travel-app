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

    /** Organizer reminder candidates: open, emailed, not yet reminded, closing between now and {@code cutoff}. */
    List<VoteSession> findByStatusAndInitiatorEmailIsNotNullAndReminderEmailSentAtIsNullAndExpiresAtBetween(
            VoteSessionStatus status, LocalDateTime from, LocalDateTime cutoff);

    /**
     * Claims the halfway email for this session: sets the marker only if the session is still open
     * and unclaimed, returning 1 when this caller won the claim. A targeted UPDATE, not a full-row
     * save of a stale snapshot — the organizer can close the session from a web thread at any moment,
     * and saving the whole row would write its old status back over COMPLETED.
     */
    @Modifying
    @Query("UPDATE VoteSession s SET s.halfwayEmailSentAt = :now "
            + "WHERE s.id = :id AND s.status = :status AND s.halfwayEmailSentAt IS NULL")
    int claimHalfwayEmail(@Param("id") UUID id,
                          @Param("status") VoteSessionStatus status,
                          @Param("now") LocalDateTime now);

    /** Same one-shot claim for the 12 h reminder; see {@link #claimHalfwayEmail}. */
    @Modifying
    @Query("UPDATE VoteSession s SET s.reminderEmailSentAt = :now "
            + "WHERE s.id = :id AND s.status = :status AND s.reminderEmailSentAt IS NULL")
    int claimReminderEmail(@Param("id") UUID id,
                           @Param("status") VoteSessionStatus status,
                           @Param("now") LocalDateTime now);

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
