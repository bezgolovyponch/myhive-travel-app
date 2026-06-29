package com.myhive.backend.repository;

import com.myhive.backend.entity.Booking;
import com.myhive.backend.model.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByStripeSessionId(String stripeSessionId);
    List<Booking> findByUserEmail(String userEmail);

    List<Booking> findByStatus(BookingStatus status);

    long countByStatus(BookingStatus status);

    /** Newest non-consultation active booking for a vote session — used by the deposit dedup so a
     *  consultation-lead booking (no deposit share) cannot shadow a real deposit booking. */
    Optional<Booking> findFirstByVoteSessionIdAndConsultationRequestedFalseAndStatusInOrderByCreatedAtDesc(
            UUID voteSessionId, Collection<BookingStatus> statuses);

    long countByVoteSessionIdAndConsultationRequestedTrue(UUID voteSessionId);

    /** Pessimistic write lock used to serialize balance-collection so the mode-lock is race-safe (H2). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);
}
