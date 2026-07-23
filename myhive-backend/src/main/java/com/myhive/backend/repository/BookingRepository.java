package com.myhive.backend.repository;

import com.myhive.backend.entity.Booking;
import com.myhive.backend.model.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByStripeSessionId(String stripeSessionId);

    List<Booking> findByStatus(BookingStatus status);

    long countByStatus(BookingStatus status);

    /** Newest non-consultation active booking for a vote session — used by the deposit dedup so a
     *  consultation-lead booking (no deposit share) cannot shadow a real deposit booking. */
    Optional<Booking> findFirstByVoteSessionIdAndConsultationRequestedFalseAndStatusInOrderByCreatedAtDesc(
            UUID voteSessionId, Collection<BookingStatus> statuses);

    long countByVoteSessionIdAndConsultationRequestedTrue(UUID voteSessionId);

    /** Pessimistic write lock on a booking row. Reserved for the balance-collection flow (mode-lock
     *  race-safety, H2) on the follow-up branch; unused by the deposit-only flow. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    /** Trip-lead reminder stop condition: any booking by this email since the lead was captured. */
    boolean existsByUserEmailIgnoreCaseAndCreatedAtAfter(String userEmail, LocalDateTime createdAt);

    /** Trip-lead reminder stop condition: any booking (incl. consultation) from this vote session. */
    boolean existsByVoteSessionId(UUID voteSessionId);
}
