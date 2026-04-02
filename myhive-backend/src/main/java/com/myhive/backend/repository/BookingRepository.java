package com.myhive.backend.repository;

import com.myhive.backend.entity.Booking;
import com.myhive.backend.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByStripeSessionId(String stripeSessionId);
    List<Booking> findByUserEmail(String userEmail);

    List<Booking> findByStatus(BookingStatus status);

    long countByStatus(BookingStatus status);
}
