package com.myhive.backend.repository;

import com.myhive.backend.entity.BookingPaymentShare;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingPaymentShareRepository extends JpaRepository<BookingPaymentShare, UUID> {

    List<BookingPaymentShare> findByBookingId(UUID bookingId);

    Optional<BookingPaymentShare> findByStripePaymentLinkId(String stripePaymentLinkId);

    Optional<BookingPaymentShare> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);

    Optional<BookingPaymentShare> findByStripePaymentIntentId(String stripePaymentIntentId);
}
