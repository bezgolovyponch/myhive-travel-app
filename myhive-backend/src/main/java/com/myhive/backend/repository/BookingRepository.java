package com.myhive.backend.repository;

import com.myhive.backend.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByStripeSessionId(String stripeSessionId);
    List<Booking> findByUserEmail(String userEmail);
    List<Booking> findByStatus(String status);
}
