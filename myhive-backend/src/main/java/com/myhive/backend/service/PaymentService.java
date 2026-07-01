package com.myhive.backend.service;

import com.myhive.backend.config.StripeProperties;
import com.myhive.backend.dto.AdminPaymentLinkResponse;
import com.myhive.backend.dto.ConsultationLeadResponse;
import com.myhive.backend.dto.DepositSessionResponse;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.BookingPaymentShare;
import com.myhive.backend.entity.ProcessedStripeEvent;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ConflictException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.model.PaymentShareType;
import com.myhive.backend.payment.StripeGateway;
import com.myhive.backend.payment.StripeRefs.CheckoutSessionRef;
import com.myhive.backend.payment.StripeRefs.PaymentLinkRef;
import com.myhive.backend.payment.StripeRefs.StripeWebhookEvent;
import com.myhive.backend.repository.BookingPaymentShareRepository;
import com.myhive.backend.repository.BookingRepository;
import com.myhive.backend.repository.ProcessedStripeEventRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@Slf4j
public class PaymentService {

    /** Stripe's per-transaction minimum for EUR (~€0.50). Smaller charges are rejected by the gateway. */
    private static final long STRIPE_MIN_CHARGE_CENTS = 50L;

    /** Typo guard: an admin-created payment link must not exceed €50,000 (5,000,000 cents). */
    private static final long ADMIN_PAYMENT_LINK_MAX_CENTS = 5_000_000L;

    /** L5: at most one consultation lead per vote session (matches the "already requested" message),
     *  to bound staff-inbox spam. */
    private static final long MAX_CONSULTATION_LEADS_PER_SESSION = 1L;

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final BookingPaymentShareRepository shareRepository;
    private final ProcessedStripeEventRepository processedEventRepository;
    private final VoteSessionService voteSessionService;
    private final StripeGateway stripeGateway;
    private final StripeProperties stripeProperties;
    private final EmailService emailService;

    @Value("${app.frontend.url:https://trivlu.com}")
    private String frontendUrl;

    public PaymentService(BookingService bookingService, BookingRepository bookingRepository,
            BookingPaymentShareRepository shareRepository, ProcessedStripeEventRepository processedEventRepository,
            VoteSessionService voteSessionService, StripeGateway stripeGateway, StripeProperties stripeProperties,
            EmailService emailService) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.shareRepository = shareRepository;
        this.processedEventRepository = processedEventRepository;
        this.voteSessionService = voteSessionService;
        this.stripeGateway = stripeGateway;
        this.stripeProperties = stripeProperties;
        this.emailService = emailService;
    }

    @Transactional
    public DepositSessionResponse createDepositBookingAndSession(UUID voteShareToken, UUID managerToken,
            TripExportRequest request) {
        VoteSession session = voteSessionService.requireManager(voteShareToken, managerToken);
        // M1: serialize deposit creation per session so a concurrent double-submit cannot race past the
        // dedup check below and create two bookings + two live deposit checkout sessions.
        voteSessionService.lockSession(session.getId());

        // Server-side dedup: a double-submit / back-button re-POST must not create a second
        // booking + deposit session. Reuse the most recent active booking's checkout URL if present.
        Optional<Booking> existing = bookingRepository
                .findFirstByVoteSessionIdAndConsultationRequestedFalseAndStatusInOrderByCreatedAtDesc(
                        session.getId(),
                        List.of(BookingStatus.PENDING, BookingStatus.DEPOSIT_PAID, BookingStatus.PARTIALLY_PAID));
        if (existing.isPresent()) {
            String existingUrl = shareRepository.findByBookingId(existing.get().getId()).stream()
                    .filter(s -> s.getType() == PaymentShareType.DEPOSIT)
                    .map(BookingPaymentShare::getPaymentUrl)
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
            if (existingUrl != null) {
                return new DepositSessionResponse(existing.get().getId(), existingUrl);
            }
        }

        // C1: the deposit actually charges money, so prices/discounts must be catalog-trusted.
        Booking booking = bookingService.createBookingEntity(request, true);
        booking.setVoteSessionId(session.getId());
        long participants = voteSessionService.getParticipantCount(voteShareToken);
        booking.setParticipantShareCount((int) Math.max(0, participants - 1));

        return createDepositCheckout(booking);
    }

    /**
     * A direct 30% deposit for a self-curated Trip Builder trip — no vote session. Turnstile is verified
     * at the controller (public endpoint). Pricing is catalog-trusted (C1) exactly like the vote deposit.
     */
    @Transactional
    public DepositSessionResponse createTripDepositSession(TripExportRequest request) {
        Booking booking = bookingService.createBookingEntity(request, true);
        return createDepositCheckout(booking);
    }

    /** Admin-created Stripe Payment Link for an arbitrary amount on a booking (balance or add-on). */
    @Transactional
    public AdminPaymentLinkResponse createAdminPaymentLink(UUID bookingId, long amountCents) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Cannot collect payment on a cancelled booking");
        }
        if (amountCents < STRIPE_MIN_CHARGE_CENTS) {
            throw new BadRequestException("Amount is below the minimum chargeable value");
        }
        if (amountCents > ADMIN_PAYMENT_LINK_MAX_CENTS) {
            throw new BadRequestException("Amount exceeds the maximum allowed value");
        }

        BookingPaymentShare share = new BookingPaymentShare();
        share.setBooking(booking);
        share.setType(PaymentShareType.BALANCE);
        share.setAmount(centsToBig(amountCents));
        share.setPaid(false);
        shareRepository.save(share);

        Map<String, String> metadata = Map.of(
                "booking_id", booking.getId().toString(),
                "share_id", share.getId().toString());
        PaymentLinkRef ref = stripeGateway.createPaymentLink(amountCents,
                stripeProperties.getCurrency(), "Payment for trip " + booking.getTripId(), metadata);

        share.setStripePaymentLinkId(ref.id());
        share.setPaymentUrl(ref.url());
        shareRepository.save(share);

        return new AdminPaymentLinkResponse(ref.url(), centsToBig(amountCents), share.getId());
    }

    /** Shared: compute the 30% deposit for a freshly-built booking, persist the DEPOSIT share, and open a
     *  Stripe Checkout session. Used by both the vote deposit and the Trip Builder deposit. */
    private DepositSessionResponse createDepositCheckout(Booking booking) {
        long totalCents = PaymentCalculator.toCents(booking.getTotalAmount());
        long depositCents = PaymentCalculator.depositCents(totalCents, stripeProperties.getDepositPct());
        // L3 (symmetry): the deposit must also clear Stripe's per-transaction minimum, else a clean 400
        // instead of a raw 502 from the gateway.
        if (depositCents < STRIPE_MIN_CHARGE_CENTS) {
            throw new BadRequestException("Trip total is too small to take a deposit");
        }
        booking.setDepositAmount(centsToBig(depositCents));
        booking.setAmountPaid(BigDecimal.ZERO);
        booking.setRefundedAmount(BigDecimal.ZERO);
        booking.setStatus(BookingStatus.PENDING);
        bookingRepository.save(booking);

        BookingPaymentShare deposit = new BookingPaymentShare();
        deposit.setBooking(booking);
        deposit.setType(PaymentShareType.DEPOSIT);
        deposit.setAmount(centsToBig(depositCents));
        deposit.setPaid(false);
        shareRepository.save(deposit);

        Map<String, String> metadata = Map.of(
                "booking_id", booking.getId().toString(),
                "share_id", deposit.getId().toString());
        String successUrl = frontendUrl + "/payment/success?booking=" + booking.getId();
        String cancelUrl = frontendUrl + "/payment/cancelled";
        CheckoutSessionRef ref = stripeGateway.createCheckoutSession(depositCents, stripeProperties.getCurrency(),
                "Deposit for trip " + booking.getTripId(), metadata, successUrl, cancelUrl,
                "deposit-" + booking.getId());

        deposit.setStripeCheckoutSessionId(ref.id());
        deposit.setPaymentUrl(ref.url());
        shareRepository.save(deposit);
        booking.setStripeSessionId(ref.id());
        bookingRepository.save(booking);

        return new DepositSessionResponse(booking.getId(), ref.url());
    }

    @Transactional
    public void handleStripeEvent(String payload, String signatureHeader) {
        StripeWebhookEvent event = stripeGateway.constructEvent(payload, signatureHeader);
        if (processedEventRepository.existsById(event.id())) {
            log.info("Stripe event {} already processed; ignoring", event.id());
            return;
        }
        // BE-1: persist the dedup marker BEFORE dispatch so the unique-PK insert is the guard.
        // If dispatch throws (e.g. unresolved payment, STRIPE-2/3), this insert rolls back with it,
        // the controller returns 5xx, and Stripe retries — no silent 200/no-retry.
        processedEventRepository.save(new ProcessedStripeEvent(event.id()));
        switch (event.type()) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> handlePaymentSucceeded(event);
            case "charge.refunded" -> handleRefund(event);
            default -> log.info("Ignoring unhandled Stripe event type {}", event.type());
        }
    }

    private void handlePaymentSucceeded(StripeWebhookEvent event) {
        BookingPaymentShare share = resolveShare(event);
        if (share == null) {
            // STRIPE-2/3: a payment event we cannot map to a share must NOT 200. Throwing rolls back the
            // ProcessedStripeEvent insert, the controller returns 5xx, and Stripe retries — no silent loss.
            log.warn("No payment share matched Stripe event {} (shareId={}, link={}, session={})",
                    event.id(), event.shareId(), event.paymentLinkId(), event.sessionId());
            throw new IllegalStateException("Unresolved Stripe payment event " + event.id() + " — retrying");
        }
        // STRIPE-1: only fulfill settled payments. With card-only this is always "paid"; defense-in-depth
        // against future async methods whose checkout.session.completed fires before settlement.
        String paymentStatus = event.paymentStatus();
        if (paymentStatus != null && !"paid".equals(paymentStatus) && !"no_payment_required".equals(paymentStatus)) {
            log.info("Checkout session {} not settled yet (payment_status={}); awaiting async settlement",
                    event.sessionId(), paymentStatus);
            return;
        }
        // SEC-2: never credit a share for less (or more) than its expected amount.
        if (event.amountTotalCents() != null
                && event.amountTotalCents().longValue() != PaymentCalculator.toCents(share.getAmount())) {
            log.warn("Stripe amount {} != expected share amount {} cents for share {}; skipping",
                    event.amountTotalCents(), PaymentCalculator.toCents(share.getAmount()), share.getId());
            return;
        }
        if (share.isPaid()) {
            return;
        }
        share.setPaid(true);
        share.setPaidAt(LocalDateTime.now());
        share.setStripePaymentIntentId(event.paymentIntentId());
        share.setPayerEmail(event.payerEmail());
        shareRepository.save(share);

        Booking booking = share.getBooking();
        // M2: status/fully-paid is decided on NET money held — refunded shares are excluded so a
        // refund mid-collection cannot push a booking to PAID.
        BigDecimal paidSum = shareRepository.findByBookingId(booking.getId()).stream()
                .filter(BookingPaymentShare::isPaid)
                .filter(s -> !s.isRefunded())
                .map(BookingPaymentShare::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        booking.setAmountPaid(paidSum);

        boolean fullyPaid = paidSum.compareTo(booking.getTotalAmount()) >= 0;
        if (fullyPaid) {
            booking.setStatus(BookingStatus.PAID);
            booking.setPaidAt(LocalDateTime.now());
        } else if (share.getType() == PaymentShareType.DEPOSIT) {
            booking.setStatus(BookingStatus.DEPOSIT_PAID);
        } else {
            booking.setStatus(BookingStatus.PARTIALLY_PAID);
        }
        bookingRepository.save(booking);

        if (share.getType() == PaymentShareType.DEPOSIT || fullyPaid) {
            try {
                emailService.sendPaymentReceived(booking.getUserEmail(), booking.getCustomerName(),
                        booking.getTripId(), paidSum, booking.getTotalAmount(), fullyPaid);
            } catch (Exception e) {
                log.error("Payment-received email failed for booking {}: {}", booking.getId(), e.getMessage(), e);
            }
            // The booking is now confirmed by payment, so send the customer's itinerary confirmation and
            // alert the bookings inbox — the same pair a normal booking sends, deferred until money is in.
            // Only the Booking is on hand here, so rebuild the itinerary request from it. All of this is
            // best-effort: the payment is already fulfilled, so a rebuild/email failure must never throw
            // (which would roll back the fulfillment and make Stripe retry).
            TripExportRequest itinerary = null;
            try {
                itinerary = bookingService.toExportRequest(booking);
            } catch (Exception e) {
                log.error("Failed to rebuild itinerary request for booking {}: {}", booking.getId(), e.getMessage(), e);
            }
            if (itinerary != null) {
                try {
                    emailService.sendItineraryConfirmation(booking.getUserEmail(), booking.getCustomerName(),
                            itinerary, booking.getTripId(), booking.getAmountPaid(), booking.getTotalAmount());
                } catch (Exception e) {
                    log.error("Itinerary confirmation email failed for booking {}: {}", booking.getId(), e.getMessage(), e);
                }
                try {
                    emailService.sendBookingNotification(booking, itinerary);
                } catch (Exception e) {
                    log.error("Booking notification email failed for booking {}: {}", booking.getId(), e.getMessage(), e);
                }
            }
        }
    }

    private void handleRefund(StripeWebhookEvent event) {
        Optional<BookingPaymentShare> match = shareRepository.findByStripePaymentIntentId(event.paymentIntentId());
        if (match.isEmpty()) {
            // F4: mirror handlePaymentSucceeded — a refund we cannot map yet (e.g. delivered before the
            // payment's intent id was persisted) must NOT be committed as processed. Throwing rolls back
            // the ProcessedStripeEvent marker so Stripe retries instead of dropping the refund.
            log.warn("No payment share matched refund for payment_intent {} — retrying", event.paymentIntentId());
            throw new IllegalStateException(
                    "Unresolved Stripe refund event " + event.id() + " — retrying");
        }
        BookingPaymentShare share = match.get();
        if (share.isRefunded()) {
            return;
        }
        if (!event.fullyRefunded()) {
            log.info("Partial refund for payment_intent {} — not modeled in v1, ignoring", event.paymentIntentId());
            return;
        }
        share.setRefunded(true);
        share.setRefundedAt(LocalDateTime.now());
        shareRepository.save(share);

        // M2: recompute on NET held (paid minus refunded), so amountPaid and status stay truthful
        // after a refund instead of leaving the refunded share counted as paid.
        Booking booking = share.getBooking();
        List<BookingPaymentShare> shares = shareRepository.findByBookingId(booking.getId());
        BigDecimal refundedSum = shares.stream()
                .filter(BookingPaymentShare::isRefunded)
                .map(BookingPaymentShare::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netPaid = shares.stream()
                .filter(s -> s.isPaid() && !s.isRefunded())
                .map(BookingPaymentShare::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        booking.setRefundedAmount(refundedSum);
        booking.setAmountPaid(netPaid);
        if (netPaid.signum() <= 0) {
            booking.setStatus(BookingStatus.REFUNDED);
        } else if (netPaid.compareTo(booking.getTotalAmount()) >= 0) {
            booking.setStatus(BookingStatus.PAID);
        } else {
            booking.setStatus(BookingStatus.PARTIALLY_PAID);
        }
        bookingRepository.save(booking);
    }

    private BookingPaymentShare resolveShare(StripeWebhookEvent event) {
        if (event.shareId() != null) {
            Optional<BookingPaymentShare> byId = shareRepository.findById(UUID.fromString(event.shareId()));
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        if (event.paymentLinkId() != null) {
            Optional<BookingPaymentShare> byLink = shareRepository.findByStripePaymentLinkId(event.paymentLinkId());
            if (byLink.isPresent()) {
                return byLink.get();
            }
        }
        if (event.sessionId() != null) {
            return shareRepository.findByStripeCheckoutSessionId(event.sessionId()).orElse(null);
        }
        return null;
    }

    @Transactional
    public ConsultationLeadResponse createConsultationLead(UUID voteShareToken, UUID managerToken,
            TripExportRequest request) {
        // SEC-5: the consultation button is initiator-only — authenticate via the vote manager token.
        VoteSession session = voteSessionService.requireManager(voteShareToken, managerToken);
        // F3: serialize per session so the count-then-insert cap below is race-safe (mirrors the deposit path).
        voteSessionService.lockSession(session.getId());
        // L5: bound staff-inbox spam — a single vote session may only raise a few consultation leads.
        if (bookingRepository.countByVoteSessionIdAndConsultationRequestedTrue(session.getId())
                >= MAX_CONSULTATION_LEADS_PER_SESSION) {
            throw new ConflictException("A consultation has already been requested for this trip");
        }
        Booking booking = bookingService.createBookingEntity(request);
        booking.setVoteSessionId(session.getId());
        booking.setConsultationRequested(true);
        booking.setStatus(BookingStatus.PENDING);
        bookingRepository.save(booking);
        try {
            emailService.sendConsultationLead(booking);
        } catch (Exception e) {
            log.error("Consultation-lead email failed for booking {}: {}", booking.getId(), e.getMessage(), e);
        }
        return new ConsultationLeadResponse(booking.getId(), "Our consultant will contact you shortly.");
    }

    private BigDecimal centsToBig(long cents) {
        return BigDecimal.valueOf(cents).movePointLeft(2);
    }
}
