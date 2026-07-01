package com.myhive.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.myhive.backend.dto.AdminPaymentLinkResponse;
import com.myhive.backend.dto.ConsultationLeadResponse;
import com.myhive.backend.dto.DepositSessionResponse;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.BookingPaymentShare;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.model.PaymentShareType;
import com.myhive.backend.payment.StripeGateway;
import com.myhive.backend.payment.StripeRefs.CheckoutSessionRef;
import com.myhive.backend.payment.StripeRefs.PaymentLinkRef;
import com.myhive.backend.repository.BookingPaymentShareRepository;
import com.myhive.backend.repository.BookingRepository;
import com.myhive.backend.repository.ProcessedStripeEventRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private BookingService bookingService;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingPaymentShareRepository shareRepository;
    @Mock private ProcessedStripeEventRepository processedEventRepository;
    @Mock private VoteSessionService voteSessionService;
    @Mock private StripeGateway stripeGateway;
    @Mock private com.myhive.backend.config.StripeProperties stripeProperties;
    @Mock private EmailService emailService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(bookingService, bookingRepository, shareRepository,
                processedEventRepository, voteSessionService, stripeGateway, stripeProperties, emailService);
        ReflectionTestUtils.setField(paymentService, "frontendUrl", "http://localhost:3000");
    }

    @Test
    void createDepositBookingAndSession_computes30PercentDepositAndReturnsUrl() {
        UUID shareToken = UUID.randomUUID();
        UUID managerToken = UUID.randomUUID();
        long expectedDepositCents = 3000L; // 30% of €100

        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        when(voteSessionService.requireManager(shareToken, managerToken)).thenReturn(session);
        when(bookingRepository.findFirstByVoteSessionIdAndConsultationRequestedFalseAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(voteSessionService.getParticipantCount(shareToken)).thenReturn(4L); // N = 3

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setTripId("TRV-TEST0001");
        booking.setTotalAmount(new BigDecimal("100.00"));
        when(bookingService.createBookingEntity(any(TripExportRequest.class), eq(true))).thenReturn(booking);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shareRepository.save(any(BookingPaymentShare.class))).thenAnswer(inv -> {
            BookingPaymentShare s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
        when(stripeProperties.getDepositPct()).thenReturn(30);
        when(stripeProperties.getCurrency()).thenReturn("eur");
        when(stripeGateway.createCheckoutSession(anyLong(), anyString(), anyString(), anyMap(), anyString(), anyString(), anyString()))
                .thenReturn(new CheckoutSessionRef("cs_test_1", "https://checkout.stripe.com/cs_test_1"));

        DepositSessionResponse response = paymentService.createDepositBookingAndSession(shareToken, managerToken, new TripExportRequest());

        assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.stripe.com/cs_test_1");
        assertThat(response.getBookingId()).isEqualTo(booking.getId());
        assertThat(booking.getParticipantShareCount()).isEqualTo(3);
        assertThat(booking.getDepositAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(booking.getVoteSessionId()).isEqualTo(session.getId());

        // C1: deposit charges money, so the booking must be built with catalog-trusted pricing.
        verify(bookingService).createBookingEntity(any(TripExportRequest.class), eq(true));

        ArgumentCaptor<Long> amount = ArgumentCaptor.forClass(Long.class);
        verify(stripeGateway).createCheckoutSession(amount.capture(), eq("eur"), anyString(), anyMap(), anyString(), anyString(), anyString());
        assertThat(amount.getValue()).isEqualTo(expectedDepositCents);

        ArgumentCaptor<BookingPaymentShare> share = ArgumentCaptor.forClass(BookingPaymentShare.class);
        verify(shareRepository, org.mockito.Mockito.atLeastOnce()).save(share.capture());
        assertThat(share.getAllValues()).anyMatch(s -> s.getType() == PaymentShareType.DEPOSIT
                && s.getAmount().compareTo(new BigDecimal("30.00")) == 0);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void createDepositBookingAndSession_returnsExistingSession_whenActiveBookingExists() {
        UUID shareToken = UUID.randomUUID();
        UUID managerToken = UUID.randomUUID();
        String expectedUrl = "https://checkout.stripe.com/cs_existing";

        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        when(voteSessionService.requireManager(shareToken, managerToken)).thenReturn(session);

        Booking existing = new Booking();
        existing.setId(UUID.randomUUID());
        existing.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findFirstByVoteSessionIdAndConsultationRequestedFalseAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(existing));

        BookingPaymentShare deposit = new BookingPaymentShare();
        deposit.setId(UUID.randomUUID());
        deposit.setType(PaymentShareType.DEPOSIT);
        deposit.setPaymentUrl(expectedUrl);
        when(shareRepository.findByBookingId(existing.getId())).thenReturn(java.util.List.of(deposit));

        DepositSessionResponse response =
                paymentService.createDepositBookingAndSession(shareToken, managerToken, new TripExportRequest());

        assertThat(response.getBookingId()).isEqualTo(existing.getId());
        assertThat(response.getCheckoutUrl()).isEqualTo(expectedUrl);
        verify(stripeGateway, never()).createCheckoutSession(anyLong(), anyString(), anyString(),
                anyMap(), anyString(), anyString(), anyString());
    }

    @Test
    void createDepositBookingAndSession_belowStripeMinimum_throwsBadRequest() {
        // L3 (deposit): a trip total so small the 30% deposit is under Stripe's minimum -> clean 400, no 502.
        UUID shareToken = UUID.randomUUID();
        UUID managerToken = UUID.randomUUID();
        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        when(voteSessionService.requireManager(shareToken, managerToken)).thenReturn(session);
        when(bookingRepository.findFirstByVoteSessionIdAndConsultationRequestedFalseAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(voteSessionService.getParticipantCount(shareToken)).thenReturn(2L);
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setTotalAmount(new BigDecimal("1.00")); // 30% = 30 cents < 50-cent minimum
        when(bookingService.createBookingEntity(any(TripExportRequest.class), eq(true))).thenReturn(booking);
        when(stripeProperties.getDepositPct()).thenReturn(30);

        assertThatThrownBy(() ->
                        paymentService.createDepositBookingAndSession(shareToken, managerToken, new TripExportRequest()))
                .isInstanceOf(BadRequestException.class);
        verify(stripeGateway, never()).createCheckoutSession(anyLong(), anyString(), anyString(),
                anyMap(), anyString(), anyString(), anyString());
    }

    @Test
    void createTripDepositSession_computesDepositAndReturnsUrl_withoutVoteSession() {
        // Trip Builder direct deposit: catalog-trusted pricing (C1), 30% deposit, NO vote session.
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setTripId("TRV-DIRECT1");
        booking.setTotalAmount(new BigDecimal("100.00"));
        when(bookingService.createBookingEntity(any(TripExportRequest.class), eq(true))).thenReturn(booking);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shareRepository.save(any(BookingPaymentShare.class))).thenAnswer(inv -> {
            BookingPaymentShare s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
        when(stripeProperties.getDepositPct()).thenReturn(30);
        when(stripeProperties.getCurrency()).thenReturn("eur");
        ArgumentCaptor<Long> amount = ArgumentCaptor.forClass(Long.class);
        when(stripeGateway.createCheckoutSession(amount.capture(), anyString(), anyString(), anyMap(),
                anyString(), anyString(), anyString()))
                .thenReturn(new CheckoutSessionRef("cs_direct", "https://checkout/cs_direct"));

        DepositSessionResponse response = paymentService.createTripDepositSession(new TripExportRequest());

        assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout/cs_direct");
        assertThat(response.getBookingId()).isEqualTo(booking.getId());
        assertThat(booking.getDepositAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getVoteSessionId()).isNull();
        assertThat(amount.getValue()).isEqualTo(3000L);
        verify(bookingService).createBookingEntity(any(TripExportRequest.class), eq(true));
        verifyNoInteractions(voteSessionService);
    }

    private com.myhive.backend.payment.StripeRefs.StripeWebhookEvent paidEvent(String eventId, String shareId, long cents) {
        return new com.myhive.backend.payment.StripeRefs.StripeWebhookEvent(
                eventId, "checkout.session.completed", shareId, null, "cs_x", "pi_" + shareId,
                "payer@example.com", "paid", cents, null, false);
    }

    @Test
    void handleStripeEvent_depositPaid_transitionsToDepositPaidAndEmails() {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setTripId("TRV-1");
        booking.setUserEmail("init@test.com");
        booking.setCustomerName("Init");
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(new BigDecimal("100.00"));
        booking.setAmountPaid(BigDecimal.ZERO);

        BookingPaymentShare deposit = new BookingPaymentShare();
        deposit.setId(UUID.randomUUID());
        deposit.setBooking(booking);
        deposit.setType(PaymentShareType.DEPOSIT);
        deposit.setAmount(new BigDecimal("30.00"));
        deposit.setPaid(false);

        when(processedEventRepository.existsById("evt_1")).thenReturn(false);
        when(stripeGateway.constructEvent("body", "sig"))
                .thenReturn(paidEvent("evt_1", deposit.getId().toString(), 3000L));
        when(shareRepository.findById(deposit.getId())).thenReturn(java.util.Optional.of(deposit));
        when(shareRepository.findByBookingId(booking.getId())).thenReturn(java.util.List.of(deposit));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingService.toExportRequest(booking)).thenReturn(new TripExportRequest());

        paymentService.handleStripeEvent("body", "sig");

        assertThat(deposit.isPaid()).isTrue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DEPOSIT_PAID);
        assertThat(booking.getAmountPaid()).isEqualByComparingTo(new BigDecimal("30.00"));
        verify(emailService).sendPaymentReceived(eq("init@test.com"), eq("Init"), eq("TRV-1"),
                any(BigDecimal.class), any(BigDecimal.class), eq(false));
        // A paid deposit also sends the customer's itinerary confirmation (with the paid amount + total)
        // and alerts the bookings inbox.
        verify(emailService).sendItineraryConfirmation(eq("init@test.com"), eq("Init"),
                any(TripExportRequest.class), eq("TRV-1"),
                any(java.math.BigDecimal.class), any(java.math.BigDecimal.class));
        verify(emailService).sendBookingNotification(eq(booking), any(TripExportRequest.class));
        verify(processedEventRepository).save(any());
    }

    @Test
    void handleStripeEvent_lastSharePaid_transitionsToPaid() {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setTripId("TRV-2");
        booking.setUserEmail("init@test.com");
        booking.setStatus(BookingStatus.DEPOSIT_PAID);
        booking.setTotalAmount(new BigDecimal("100.00"));
        booking.setAmountPaid(new BigDecimal("30.00"));

        BookingPaymentShare deposit = new BookingPaymentShare();
        deposit.setId(UUID.randomUUID());
        deposit.setBooking(booking);
        deposit.setType(PaymentShareType.DEPOSIT);
        deposit.setAmount(new BigDecimal("30.00"));
        deposit.setPaid(true);

        BookingPaymentShare full = new BookingPaymentShare();
        full.setId(UUID.randomUUID());
        full.setBooking(booking);
        full.setType(PaymentShareType.BALANCE_FULL);
        full.setAmount(new BigDecimal("70.00"));
        full.setPaid(false);

        when(processedEventRepository.existsById("evt_2")).thenReturn(false);
        when(stripeGateway.constructEvent("b", "s"))
                .thenReturn(paidEvent("evt_2", full.getId().toString(), 7000L));
        when(shareRepository.findById(full.getId())).thenReturn(java.util.Optional.of(full));
        when(shareRepository.findByBookingId(booking.getId())).thenReturn(java.util.List.of(deposit, full));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.handleStripeEvent("b", "s");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAID);
        assertThat(booking.getPaidAt()).isNotNull();
        verify(emailService).sendPaymentReceived(any(), any(), any(), any(), any(), eq(true));
    }

    @Test
    void handleStripeEvent_duplicateEventId_isIgnored() {
        when(processedEventRepository.existsById("evt_dup")).thenReturn(true);
        when(stripeGateway.constructEvent("b", "s")).thenReturn(paidEvent("evt_dup", UUID.randomUUID().toString(), 3000L));

        paymentService.handleStripeEvent("b", "s");

        org.mockito.Mockito.verifyNoInteractions(emailService);
        verify(shareRepository, org.mockito.Mockito.never()).findById(any());
        verify(processedEventRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void handleStripeEvent_unresolvedPaymentEvent_throwsForRetry() {
        UUID shareId = UUID.randomUUID();
        when(processedEventRepository.existsById("evt_unresolved")).thenReturn(false);
        when(stripeGateway.constructEvent("b", "s"))
                .thenReturn(paidEvent("evt_unresolved", shareId.toString(), 3000L));
        // Every resolution path returns empty → no share matches.
        when(shareRepository.findById(shareId)).thenReturn(java.util.Optional.empty());
        when(shareRepository.findByStripeCheckoutSessionId("cs_x")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> paymentService.handleStripeEvent("b", "s"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void handleStripeEvent_chargeRefunded_marksShareAndBookingRefunded() {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setStatus(BookingStatus.DEPOSIT_PAID);
        booking.setTotalAmount(new BigDecimal("100.00"));
        booking.setAmountPaid(new BigDecimal("30.00"));
        booking.setRefundedAmount(BigDecimal.ZERO);

        BookingPaymentShare deposit = new BookingPaymentShare();
        deposit.setId(UUID.randomUUID());
        deposit.setBooking(booking);
        deposit.setType(PaymentShareType.DEPOSIT);
        deposit.setAmount(new BigDecimal("30.00"));
        deposit.setPaid(true);
        deposit.setStripePaymentIntentId("pi_ref");

        when(processedEventRepository.existsById("evt_ref")).thenReturn(false);
        when(stripeGateway.constructEvent("b", "s")).thenReturn(
                new com.myhive.backend.payment.StripeRefs.StripeWebhookEvent(
                        "evt_ref", "charge.refunded", null, null, null, "pi_ref", null, null, null, 3000L, true));
        when(shareRepository.findByStripePaymentIntentId("pi_ref")).thenReturn(java.util.Optional.of(deposit));
        when(shareRepository.findByBookingId(booking.getId())).thenReturn(java.util.List.of(deposit));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.handleStripeEvent("b", "s");

        assertThat(deposit.isRefunded()).isTrue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REFUNDED);
    }

    @Test
    void handleStripeEvent_amountMismatch_doesNotCreditShare() {
        // SEC-2 (L4): Stripe-charged amount != expected share amount -> share stays unpaid, no email.
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setTripId("TRV-AMT");
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(new BigDecimal("100.00"));

        BookingPaymentShare deposit = new BookingPaymentShare();
        deposit.setId(UUID.randomUUID());
        deposit.setBooking(booking);
        deposit.setType(PaymentShareType.DEPOSIT);
        deposit.setAmount(new BigDecimal("30.00"));
        deposit.setPaid(false);

        when(processedEventRepository.existsById("evt_amt")).thenReturn(false);
        when(stripeGateway.constructEvent("b", "s"))
                .thenReturn(paidEvent("evt_amt", deposit.getId().toString(), 100L)); // €1.00, expected €30.00
        when(shareRepository.findById(deposit.getId())).thenReturn(Optional.of(deposit));

        paymentService.handleStripeEvent("b", "s");

        assertThat(deposit.isPaid()).isFalse();
        verify(shareRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void handleStripeEvent_unsettledPaymentStatus_defersWithoutCrediting() {
        // STRIPE-1 (L4): payment_status not settled -> defer, no credit, no email.
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(new BigDecimal("100.00"));

        BookingPaymentShare deposit = new BookingPaymentShare();
        deposit.setId(UUID.randomUUID());
        deposit.setBooking(booking);
        deposit.setType(PaymentShareType.DEPOSIT);
        deposit.setAmount(new BigDecimal("30.00"));
        deposit.setPaid(false);

        com.myhive.backend.payment.StripeRefs.StripeWebhookEvent unsettled =
                new com.myhive.backend.payment.StripeRefs.StripeWebhookEvent(
                        "evt_unsettled", "checkout.session.completed", deposit.getId().toString(), null, "cs_x",
                        "pi_x", "payer@example.com", "unpaid", 3000L, null, false);
        when(processedEventRepository.existsById("evt_unsettled")).thenReturn(false);
        when(stripeGateway.constructEvent("b", "s")).thenReturn(unsettled);
        when(shareRepository.findById(deposit.getId())).thenReturn(Optional.of(deposit));

        paymentService.handleStripeEvent("b", "s");

        assertThat(deposit.isPaid()).isFalse();
        verify(shareRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void handleStripeEvent_refundOfDeposit_recomputesNetAndDoesNotMarkPaid() {
        // M2: a refunded deposit is excluded from net paid, so the booking is not (and cannot be) PAID.
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setStatus(BookingStatus.PARTIALLY_PAID);
        booking.setTotalAmount(new BigDecimal("100.00"));
        booking.setAmountPaid(new BigDecimal("65.00"));
        booking.setRefundedAmount(BigDecimal.ZERO);

        BookingPaymentShare deposit = new BookingPaymentShare();
        deposit.setId(UUID.randomUUID());
        deposit.setBooking(booking);
        deposit.setType(PaymentShareType.DEPOSIT);
        deposit.setAmount(new BigDecimal("30.00"));
        deposit.setPaid(true);
        deposit.setStripePaymentIntentId("pi_dep");

        BookingPaymentShare share1 = new BookingPaymentShare();
        share1.setId(UUID.randomUUID());
        share1.setBooking(booking);
        share1.setType(PaymentShareType.BALANCE_SHARE);
        share1.setAmount(new BigDecimal("35.00"));
        share1.setPaid(true);

        when(processedEventRepository.existsById("evt_refnet")).thenReturn(false);
        when(stripeGateway.constructEvent("b", "s")).thenReturn(
                new com.myhive.backend.payment.StripeRefs.StripeWebhookEvent(
                        "evt_refnet", "charge.refunded", null, null, null, "pi_dep", null, null, null, 3000L, true));
        when(shareRepository.findByStripePaymentIntentId("pi_dep")).thenReturn(Optional.of(deposit));
        when(shareRepository.findByBookingId(booking.getId())).thenReturn(java.util.List.of(deposit, share1));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.handleStripeEvent("b", "s");

        assertThat(deposit.isRefunded()).isTrue();
        assertThat(booking.getRefundedAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(booking.getAmountPaid()).isEqualByComparingTo(new BigDecimal("35.00")); // net excludes refunded
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PARTIALLY_PAID);
    }

    @Test
    void handleStripeEvent_refundWithNoMatchingShare_throwsForRetry() {
        // F4: an unmappable refund must throw (rolling back the dedup marker) so Stripe retries,
        // consistent with handlePaymentSucceeded — never silently commit it as processed.
        when(processedEventRepository.existsById("evt_reforphan")).thenReturn(false);
        when(stripeGateway.constructEvent("b", "s")).thenReturn(
                new com.myhive.backend.payment.StripeRefs.StripeWebhookEvent(
                        "evt_reforphan", "charge.refunded", null, null, null, "pi_unknown", null, null, null, 3000L, true));
        when(shareRepository.findByStripePaymentIntentId("pi_unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.handleStripeEvent("b", "s"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createConsultationLead_overCap_throwsConflict() {
        // L5: a vote session can only raise a bounded number of consultation leads.
        UUID shareToken = UUID.randomUUID();
        UUID managerToken = UUID.randomUUID();
        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        when(voteSessionService.requireManager(shareToken, managerToken)).thenReturn(session);
        when(bookingRepository.countByVoteSessionIdAndConsultationRequestedTrue(session.getId())).thenReturn(1L);

        assertThatThrownBy(() ->
                        paymentService.createConsultationLead(shareToken, managerToken, new TripExportRequest()))
                .isInstanceOf(com.myhive.backend.exception.ConflictException.class);
        verifyNoInteractions(bookingService);
        // F3: the per-session lock is taken before the cap check so the count-then-insert is serialized.
        verify(voteSessionService).lockSession(session.getId());
    }

    @Test
    void createAdminPaymentLink_createsLinkAndBalanceShare() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setTripId("TRV-ADMIN1");
        booking.setStatus(BookingStatus.DEPOSIT_PAID);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(stripeProperties.getCurrency()).thenReturn("eur");
        when(stripeGateway.createPaymentLink(eq(2800L), eq("eur"), anyString(), anyMap()))
                .thenReturn(new PaymentLinkRef("plink_1", "https://pay/plink_1"));
        when(shareRepository.save(any(BookingPaymentShare.class))).thenAnswer(inv -> {
            BookingPaymentShare s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });

        AdminPaymentLinkResponse response = paymentService.createAdminPaymentLink(bookingId, 2800L);

        assertThat(response.url()).isEqualTo("https://pay/plink_1");
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("28.00"));
        ArgumentCaptor<BookingPaymentShare> shareCaptor = ArgumentCaptor.forClass(BookingPaymentShare.class);
        verify(shareRepository, org.mockito.Mockito.times(2)).save(shareCaptor.capture());
        BookingPaymentShare saved = shareCaptor.getValue();
        assertThat(saved.getType()).isEqualTo(PaymentShareType.BALANCE);
        assertThat(saved.getStripePaymentLinkId()).isEqualTo("plink_1");
        assertThat(saved.getPaymentUrl()).isEqualTo("https://pay/plink_1");
        assertThat(saved.isPaid()).isFalse();
        assertThat(response.shareId()).isEqualTo(saved.getId());
    }

    @Test
    void createAdminPaymentLink_belowMinimum_throwsBadRequest() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setStatus(BookingStatus.DEPOSIT_PAID);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(
                        () -> paymentService.createAdminPaymentLink(bookingId, 40L))
                .isInstanceOf(BadRequestException.class);
        verify(stripeGateway, never()).createPaymentLink(anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    void createAdminPaymentLink_aboveMaximum_throwsBadRequest() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setStatus(BookingStatus.DEPOSIT_PAID);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(
                        () -> paymentService.createAdminPaymentLink(bookingId, 5_000_001L))
                .isInstanceOf(BadRequestException.class);
        verify(stripeGateway, never()).createPaymentLink(anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    void createAdminPaymentLink_cancelledBooking_throwsBadRequest() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(
                        () -> paymentService.createAdminPaymentLink(bookingId, 2800L))
                .isInstanceOf(BadRequestException.class);
        verify(stripeGateway, never()).createPaymentLink(anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    void createConsultationLead_flagsBookingAndNotifiesConsultant() {
        UUID shareToken = UUID.randomUUID();
        UUID managerToken = UUID.randomUUID();
        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        when(voteSessionService.requireManager(any(), any())).thenReturn(session);

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setTripId("TRV-LEAD");
        booking.setStatus(BookingStatus.PENDING);
        when(bookingService.createBookingEntity(any(TripExportRequest.class))).thenReturn(booking);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsultationLeadResponse response =
                paymentService.createConsultationLead(shareToken, managerToken, new TripExportRequest());

        assertThat(booking.isConsultationRequested()).isTrue();
        assertThat(booking.getVoteSessionId()).isEqualTo(session.getId());
        assertThat(response.getBookingId()).isEqualTo(booking.getId());
        verify(emailService).sendConsultationLead(booking);
    }
}
