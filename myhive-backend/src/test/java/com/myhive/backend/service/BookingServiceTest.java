package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.BookingDTO;
import com.myhive.backend.dto.BookingStatsDTO;
import com.myhive.backend.dto.CreateBookingRequest;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.BookingItem;
import com.myhive.backend.entity.BookingPaymentShare;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.PaymentGatewayException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.model.PaymentShareType;
import com.myhive.backend.payment.StripeGateway;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.BookingPaymentShareRepository;
import com.myhive.backend.repository.BookingRepository;
import com.myhive.backend.repository.PackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PackageRepository packageRepository;

    @Mock
    private BookingPaymentShareRepository shareRepository;

    @Mock
    private StripeGateway stripeGateway;

    @InjectMocks
    private BookingService bookingService;

    private Destination destination;
    private Activity activity;

    @BeforeEach
    void setUp() {
        destination = TestDataFactory.destination();
        activity = TestDataFactory.activity(destination);
    }

    @Test
    void createBooking_withValidRequest_calculatesTotalAndSaves() {
        String expectedStatus = "PENDING";
        BigDecimal expectedTotal = new BigDecimal("199.98"); // 99.99 * quantity 2

        CreateBookingRequest request = TestDataFactory.createBookingRequest(activity.getId());
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        BookingDTO result = bookingService.createBooking(request);

        assertThat(result.getStatus()).isEqualTo(expectedStatus);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(expectedTotal);
        assertThat(result.getItems()).hasSize(1);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void createBooking_withGroupMinimum_flooredTotalAndSnapshot() {
        // 2 travelers x EUR50.00 = EUR100.00 < EUR300.00 group minimum -> the line bills EUR300.00,
        // and the legacy request-based path must snapshot minPrice just like the export path does.
        BigDecimal expectedTotal = new BigDecimal("300.00");
        BigDecimal expectedMinPrice = new BigDecimal("300.00");
        activity.setPrice(new BigDecimal("50.00"));
        activity.setMinPrice(expectedMinPrice);

        CreateBookingRequest request = TestDataFactory.createBookingRequest(activity.getId());
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        BookingDTO result = bookingService.createBooking(request);

        assertThat(result.getTotalAmount()).isEqualByComparingTo(expectedTotal);
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getBookingItems().getFirst().getMinPrice())
                .isEqualByComparingTo(expectedMinPrice);
    }

    @Test
    void toExportRequest_rebuildsItineraryFromBooking_groupingByDestination() {
        Booking booking = new Booking();
        booking.setUserEmail("buyer@test.com");
        booking.setCustomerName("Buyer");
        booking.setPhone("+123");
        booking.setNumberOfTravelers(2);
        booking.setTripId("TRV-XYZ");
        booking.setStartDate(java.time.LocalDate.parse("2026-08-01"));
        booking.setEndDate(java.time.LocalDate.parse("2026-08-05"));

        BookingItem first = new BookingItem();
        first.setBooking(booking);
        first.setActivity(activity); // has an id
        first.setActivityName("Pub Crawl");
        first.setDestinationName("Prague");
        first.setPrice(new BigDecimal("40.00"));
        first.setQuantity(2);

        BookingItem second = new BookingItem();
        second.setBooking(booking);
        second.setActivityName("River Cruise"); // no catalog activity → null activityId
        second.setDestinationName("Prague");
        second.setPrice(new BigDecimal("55.00"));
        second.setQuantity(2);

        booking.setBookingItems(List.of(first, second));

        TripExportRequest request = bookingService.toExportRequest(booking);

        assertThat(request.getUserEmail()).isEqualTo("buyer@test.com");
        assertThat(request.getCustomerName()).isEqualTo("Buyer");
        assertThat(request.getNumberOfTravelers()).isEqualTo(2);
        assertThat(request.getTripId()).isEqualTo("TRV-XYZ");
        // Both items share one destination → grouped under a single DestinationExport.
        assertThat(request.getDestinations()).hasSize(1);
        TripExportRequest.DestinationExport dest = request.getDestinations().get(0);
        assertThat(dest.getDestinationName()).isEqualTo("Prague");
        assertThat(dest.getStartDate()).isEqualTo("2026-08-01");
        assertThat(dest.getEndDate()).isEqualTo("2026-08-05");
        assertThat(dest.getActivities()).hasSize(2);
        assertThat(dest.getActivities().get(0).getActivityId()).isEqualTo(activity.getId());
        assertThat(dest.getActivities().get(0).getActivityName()).isEqualTo("Pub Crawl");
        assertThat(dest.getActivities().get(0).getPrice()).isEqualTo(40.0);
        assertThat(dest.getActivities().get(1).getActivityId()).isNull();
        assertThat(dest.getActivities().get(1).getPrice()).isEqualTo(55.0);
    }

    @Test
    void toExportRequest_carriesMinPriceSnapshot() {
        Booking booking = new Booking();
        booking.setUserEmail("u@example.com");
        booking.setCustomerName("User");
        BookingItem item = new BookingItem();
        item.setActivity(activity);
        item.setActivityName("Boat Rental");
        item.setDestinationName("Tenerife");
        item.setPrice(new BigDecimal("50.00"));
        item.setQuantity(2);
        item.setMinPrice(new BigDecimal("300.00"));
        booking.setBookingItems(List.of(item));

        TripExportRequest result = bookingService.toExportRequest(booking);

        TripExportRequest.ActivityExport exported =
                result.getDestinations().getFirst().getActivities().getFirst();
        assertThat(exported.getMinPrice()).isEqualByComparingTo("300.00");
    }

    @Test
    void createBooking_withNonexistentActivity_throwsResourceNotFound() {
        UUID fakeId = UUID.randomUUID();
        CreateBookingRequest request = TestDataFactory.createBookingRequest(fakeId);
        when(activityRepository.findById(fakeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Activity");
    }

    @Test
    void getBookingById_found_returnsDTO() {
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setBookingItems(List.of());
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        BookingDTO result = bookingService.getBookingById(booking.getId());

        assertThat(result.getId()).isEqualTo(booking.getId());
        assertThat(result.getUserEmail()).isEqualTo(booking.getUserEmail());
    }

    @Test
    void getBookingById_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(bookingRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBookingById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Booking");
    }

    @Test
    void updateBookingStatus_operationalStatuses_updateAndSave() {
        for (String expectedStatus : List.of("PENDING", "CONFIRMED", "CANCELLED")) {
            Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
            booking.setBookingItems(List.of());
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

            BookingDTO result = bookingService.updateBookingStatus(booking.getId(), expectedStatus);

            assertThat(result.getStatus()).isEqualTo(expectedStatus);
        }
    }

    @Test
    void updateBookingStatus_paymentStatus_throwsBadRequest() {
        // Payment statuses are owned by the Stripe webhook; setting them manually would
        // desync the booking from its payment-share bookkeeping.
        for (String paymentStatus : List.of("DEPOSIT_PAID", "PARTIALLY_PAID", "PAID", "REFUNDED")) {
            assertThatThrownBy(() -> bookingService.updateBookingStatus(UUID.randomUUID(), paymentStatus))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Stripe webhook");
        }
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void updateBookingStatus_fromPaymentStatus_allowsOnlyCancellation() {
        Booking booking = TestDataFactory.booking(BookingStatus.DEPOSIT_PAID);
        booking.setBookingItems(List.of());
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        // Leaving a payment status for PENDING/CONFIRMED would hide collected money.
        assertThatThrownBy(() -> bookingService.updateBookingStatus(booking.getId(), "CONFIRMED"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("can only be cancelled");
        verify(bookingRepository, never()).save(any(Booking.class));

        // Cancelling is the one legal manual exit.
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        BookingDTO result = bookingService.updateBookingStatus(booking.getId(), "CANCELLED");
        assertThat(result.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void updateBookingStatus_cancelDeactivatesUnpaidPaymentLinks() {
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setBookingItems(List.of());

        BookingPaymentShare openLink = new BookingPaymentShare();
        openLink.setStripePaymentLinkId("plink_open");
        openLink.setPaid(false);

        BookingPaymentShare paidLink = new BookingPaymentShare();
        paidLink.setStripePaymentLinkId("plink_paid");
        paidLink.setPaid(true);

        BookingPaymentShare depositSession = new BookingPaymentShare();
        depositSession.setPaid(false); // Checkout Session share — no payment link to deactivate

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(shareRepository.findByBookingId(booking.getId()))
                .thenReturn(List.of(openLink, paidLink, depositSession));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingDTO result = bookingService.updateBookingStatus(booking.getId(), "CANCELLED");

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        verify(stripeGateway).deactivatePaymentLink("plink_open");
        verify(stripeGateway, never()).deactivatePaymentLink("plink_paid");
    }

    @Test
    void updateBookingStatus_cancelFailsLoudWhenDeactivationFails() {
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setBookingItems(List.of());

        BookingPaymentShare openLink = new BookingPaymentShare();
        openLink.setStripePaymentLinkId("plink_open");
        openLink.setPaid(false);

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(shareRepository.findByBookingId(booking.getId())).thenReturn(List.of(openLink));
        doThrow(new PaymentGatewayException("Unable to deactivate payment link."))
                .when(stripeGateway).deactivatePaymentLink("plink_open");

        // Fail loud: a still-payable link on a cancelled booking is worse than a failed cancel.
        assertThatThrownBy(() -> bookingService.updateBookingStatus(booking.getId(), "CANCELLED"))
                .isInstanceOf(PaymentGatewayException.class);
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void updateBookingStatus_doesNotTouchStripeSessionId() {
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setStripeSessionId("existing_session");
        booking.setBookingItems(List.of());
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingDTO result = bookingService.updateBookingStatus(booking.getId(), "CONFIRMED");

        assertThat(result.getStripeSessionId()).isEqualTo("existing_session");
    }

    @Test
    void updateBookingStatus_invalidStatus_throwsBadRequest() {
        assertThatThrownBy(() -> bookingService.updateBookingStatus(UUID.randomUUID(), "INVALID"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid booking status");
    }

    @Test
    void updateBookingStatus_bookingNotFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(bookingRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.updateBookingStatus(id, "CONFIRMED"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getBookingStats_returnsCorrectCounts() {
        long expectedTotal = 10L;
        long expectedPending = 5L;
        long expectedConfirmed = 3L;
        long expectedPaid = 2L;

        when(bookingRepository.count()).thenReturn(expectedTotal);
        when(bookingRepository.countByStatus(BookingStatus.PENDING)).thenReturn(expectedPending);
        when(bookingRepository.countByStatus(BookingStatus.CONFIRMED)).thenReturn(expectedConfirmed);
        when(bookingRepository.countByStatus(BookingStatus.PAID)).thenReturn(expectedPaid);

        BookingStatsDTO stats = bookingService.getBookingStats();

        assertThat(stats.getTotal()).isEqualTo(expectedTotal);
        assertThat(stats.getPending()).isEqualTo(expectedPending);
        assertThat(stats.getConfirmed()).isEqualTo(expectedConfirmed);
        assertThat(stats.getPaid()).isEqualTo(expectedPaid);
    }

    @Test
    void getAllBookings_returnsAllAsDTOs() {
        Booking b = TestDataFactory.booking(BookingStatus.PAID);
        b.setBookingItems(List.of());
        when(bookingRepository.findAll()).thenReturn(List.of(b));

        List<BookingDTO> result = bookingService.getAllBookings();

        assertThat(result).hasSize(1);
    }

    @Test
    void createBookingFromExport_withValidRequest_savesAllFields() {
        String expectedCustomerName = "Test User";
        String expectedStatus = "PENDING";

        TripExportRequest request = TestDataFactory.tripExportRequest();
        UUID activityId = request.getDestinations().getFirst().getActivities().getFirst().getActivityId();
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        BookingDTO result = bookingService.createBookingFromExport(request);

        assertThat(result.getCustomerName()).isEqualTo(expectedCustomerName);
        assertThat(result.getStatus()).isEqualTo(expectedStatus);
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    void createBookingFromExport_sendsConfirmationEmail() {
        TripExportRequest request = TestDataFactory.tripExportRequest();
        UUID activityId = request.getDestinations().getFirst().getActivities().getFirst().getActivityId();
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        bookingService.createBookingFromExport(request);

        verify(emailService).sendItineraryConfirmation(eq("user@test.com"), eq("Test User"), any(), any());
    }

    @Test
    void createBookingFromExport_sendsBookingNotification() {
        TripExportRequest request = TestDataFactory.tripExportRequest();
        UUID activityId = request.getDestinations().getFirst().getActivities().getFirst().getActivityId();
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        bookingService.createBookingFromExport(request);

        // Both the customer confirmation and the internal bookings-inbox notification fire.
        verify(emailService).sendItineraryConfirmation(eq("user@test.com"), eq("Test User"), any(), any());
        verify(emailService).sendBookingNotification(any(Booking.class), eq(request));
    }

    @Test
    void createBookingFromExport_withBookingNotificationFailure_stillReturnsBooking() {
        TripExportRequest request = TestDataFactory.tripExportRequest();
        UUID activityId = request.getDestinations().getFirst().getActivities().getFirst().getActivityId();
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
        doThrow(new RuntimeException("SMTP down")).when(emailService)
                .sendBookingNotification(any(Booking.class), any());

        BookingDTO result = bookingService.createBookingFromExport(request);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void createBookingFromExport_withEmailFailure_stillReturnsBooking() {
        TripExportRequest request = TestDataFactory.tripExportRequest();
        UUID activityId = request.getDestinations().getFirst().getActivities().getFirst().getActivityId();
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
        doThrow(new RuntimeException("SMTP down")).when(emailService)
                .sendItineraryConfirmation(any(), any(), any(), any());

        BookingDTO result = bookingService.createBookingFromExport(request);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void createBookingFromExport_withTripIdAndUtm_persistsAttributionAndReturnsTripId() {
        String expectedTripId = "vote-abc-123";
        String expectedUtmSource = "instagram";
        String expectedUtmMedium = "social";
        String expectedUtmCampaign = "summer2026";
        String expectedRef = "partner42";

        TripExportRequest request = TestDataFactory.tripExportRequest();
        request.setTripId(expectedTripId);
        request.setUtmSource(expectedUtmSource);
        request.setUtmMedium(expectedUtmMedium);
        request.setUtmCampaign(expectedUtmCampaign);
        request.setRef(expectedRef);

        UUID activityId = request.getDestinations().getFirst().getActivities().getFirst().getActivityId();
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        BookingDTO result = bookingService.createBookingFromExport(request);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();
        assertThat(saved.getTripId()).isEqualTo(expectedTripId);
        assertThat(saved.getUtmSource()).isEqualTo(expectedUtmSource);
        assertThat(saved.getUtmMedium()).isEqualTo(expectedUtmMedium);
        assertThat(saved.getUtmCampaign()).isEqualTo(expectedUtmCampaign);
        assertThat(saved.getRef()).isEqualTo(expectedRef);
        assertThat(result.getTripId()).isEqualTo(expectedTripId);
        // Attribution must also be surfaced on the returned DTO (admin booking card).
        assertThat(result.getUtmSource()).isEqualTo(expectedUtmSource);
        assertThat(result.getUtmMedium()).isEqualTo(expectedUtmMedium);
        assertThat(result.getUtmCampaign()).isEqualTo(expectedUtmCampaign);
        assertThat(result.getRef()).isEqualTo(expectedRef);
    }

    @Test
    void createBookingFromExport_withNullTripId_generatesTrvFallback() {
        TripExportRequest request = TestDataFactory.tripExportRequest();
        request.setTripId(null);

        UUID activityId = request.getDestinations().getFirst().getActivities().getFirst().getActivityId();
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        bookingService.createBookingFromExport(request);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        String generatedTripId = captor.getValue().getTripId();
        assertThat(generatedTripId).startsWith("TRV-");
        assertThat(generatedTripId).hasSize(12); // "TRV-" (4) + 8 chars
    }

    @Test
    void createBookingEntity_buildsPendingEntityWithTotal_andSendsNoEmail() {
        TripExportRequest request = TestDataFactory.tripExportRequest();
        // SEC-1: price is taken from the looked-up activity, not the request body.
        activity.setPrice(new BigDecimal("75.00"));
        UUID activityId = request.getDestinations().getFirst().getActivities().getFirst().getActivityId();
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        Booking result = bookingService.createBookingEntity(request);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("150.00")); // activity price 75.00 * 2 travelers
        verifyNoInteractions(emailService);
    }

    @Test
    void packageBookingAppliesDiscountToTotal() {
        Activity a1 = TestDataFactory.activity(destination); a1.setPrice(new BigDecimal("100.00"));
        Activity a2 = TestDataFactory.activity(destination); a2.setPrice(new BigDecimal("200.00"));
        com.myhive.backend.entity.Package pkg = TestDataFactory.pkg(destination);
        pkg.setDiscountPct(new BigDecimal("10.00"));

        when(activityRepository.findById(a1.getId())).thenReturn(Optional.of(a1));
        when(activityRepository.findById(a2.getId())).thenReturn(Optional.of(a2));
        when(packageRepository.findById(pkg.getId())).thenReturn(Optional.of(pkg));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        TripExportRequest req = new TripExportRequest();
        req.setUserEmail("a@b.com");
        req.setNumberOfTravelers(1);
        TripExportRequest.DestinationExport de = new TripExportRequest.DestinationExport();
        de.setDestinationName(destination.getName());
        TripExportRequest.ActivityExport ae1 = new TripExportRequest.ActivityExport();
        ae1.setActivityId(a1.getId()); ae1.setActivityName(a1.getName()); ae1.setPrice(100.0);
        ae1.setPackageId(pkg.getId()); ae1.setPackageName(pkg.getName());
        ae1.setPackageDiscountPct(new BigDecimal("10.00"));
        TripExportRequest.ActivityExport ae2 = new TripExportRequest.ActivityExport();
        ae2.setActivityId(a2.getId()); ae2.setActivityName(a2.getName()); ae2.setPrice(200.0);
        ae2.setPackageId(pkg.getId()); ae2.setPackageName(pkg.getName());
        ae2.setPackageDiscountPct(new BigDecimal("10.00"));
        de.setActivities(List.of(ae1, ae2));
        req.setDestinations(List.of(de));

        BookingDTO dto = bookingService.createBookingFromExport(req);

        BigDecimal expectedTotal = new BigDecimal("270.00");
        assertThat(dto.getTotalAmount()).isEqualByComparingTo(expectedTotal);
        assertThat(dto.getItems()).allMatch(i -> pkg.getId().equals(i.getPackageId()));
    }

    @Test
    void createBookingFromExport_snapshotsDestinationFromCatalogNotClientLabel() {
        // The Trip Builder sends a placeholder destination label ("Custom Travel Package");
        // the catalog is the authority on where an activity belongs — the admin bookings
        // filter groups by this snapshot.
        Activity a1 = TestDataFactory.activity(destination);
        when(activityRepository.findById(a1.getId())).thenReturn(Optional.of(a1));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        TripExportRequest req = new TripExportRequest();
        req.setUserEmail("a@b.com");
        req.setNumberOfTravelers(1);
        TripExportRequest.DestinationExport de = new TripExportRequest.DestinationExport();
        de.setDestinationName("Custom Travel Package");
        TripExportRequest.ActivityExport ae = new TripExportRequest.ActivityExport();
        ae.setActivityId(a1.getId());
        ae.setActivityName(a1.getName());
        ae.setPrice(50.0);
        de.setActivities(List.of(ae));
        req.setDestinations(List.of(de));

        BookingDTO dto = bookingService.createBookingFromExport(req);

        String expectedDestinationName = destination.getName();
        assertThat(dto.getItems())
                .isNotEmpty()
                .allMatch(i -> expectedDestinationName.equals(i.getDestinationName()));
    }

    @Test
    void createBookingEntity_paidFlow_usesCatalogDiscountNotClientValue() {
        // C1: a malicious initiator sends the real activity + real package but packageDiscountPct=99.
        // The paid flow must apply the persisted catalog discount (10%), never the request value.
        Activity a1 = TestDataFactory.activity(destination);
        a1.setPrice(new BigDecimal("100.00"));
        com.myhive.backend.entity.Package pkg = TestDataFactory.pkg(destination);
        pkg.setDiscountPct(new BigDecimal("10.00"));
        com.myhive.backend.entity.PackageActivity pa = new com.myhive.backend.entity.PackageActivity();
        pa.setActivity(a1);
        pkg.setPackageActivities(List.of(pa));

        when(activityRepository.findById(a1.getId())).thenReturn(Optional.of(a1));
        when(packageRepository.findById(pkg.getId())).thenReturn(Optional.of(pkg));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        TripExportRequest req = new TripExportRequest();
        req.setUserEmail("a@b.com");
        req.setCustomerName("A");
        req.setNumberOfTravelers(1);
        TripExportRequest.DestinationExport de = new TripExportRequest.DestinationExport();
        de.setDestinationName(destination.getName());
        TripExportRequest.ActivityExport ae = new TripExportRequest.ActivityExport();
        ae.setActivityId(a1.getId());
        ae.setActivityName(a1.getName());
        ae.setPackageId(pkg.getId());
        ae.setPackageName(pkg.getName());
        ae.setPackageDiscountPct(new BigDecimal("99.00")); // attacker value — must be ignored
        de.setActivities(List.of(ae));
        req.setDestinations(List.of(de));

        Booking saved = bookingService.createBookingEntity(req, true);

        // 100.00 * (100 - 10)/100 = 90.00 ; the client's 99% would have deflated this to 1.00
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("90.00"));
    }

    @Test
    void createBookingEntity_paidFlow_rejectsItemWithoutActivityId() {
        // C1: in the paid flow every line must reference a catalog activity, so a client-supplied
        // price (e.g. 0.01) can never set what is charged.
        TripExportRequest req = new TripExportRequest();
        req.setUserEmail("a@b.com");
        req.setCustomerName("A");
        req.setNumberOfTravelers(1);
        TripExportRequest.DestinationExport de = new TripExportRequest.DestinationExport();
        de.setDestinationName(destination.getName());
        TripExportRequest.ActivityExport ae = new TripExportRequest.ActivityExport();
        ae.setActivityId(null);
        ae.setPrice(0.01);
        de.setActivities(List.of(ae));
        req.setDestinations(List.of(de));

        assertThatThrownBy(() -> bookingService.createBookingEntity(req, true))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createBookingEntity_paidFlow_rejectsActivityNotInPackage() {
        // C1: a real activity cannot borrow a different package's discount — membership is verified.
        Activity a1 = TestDataFactory.activity(destination);
        a1.setPrice(new BigDecimal("100.00"));
        com.myhive.backend.entity.Package pkg = TestDataFactory.pkg(destination); // empty packageActivities
        when(activityRepository.findById(a1.getId())).thenReturn(Optional.of(a1));
        when(packageRepository.findById(pkg.getId())).thenReturn(Optional.of(pkg));

        TripExportRequest req = new TripExportRequest();
        req.setUserEmail("a@b.com");
        req.setCustomerName("A");
        req.setNumberOfTravelers(1);
        TripExportRequest.DestinationExport de = new TripExportRequest.DestinationExport();
        de.setDestinationName(destination.getName());
        TripExportRequest.ActivityExport ae = new TripExportRequest.ActivityExport();
        ae.setActivityId(a1.getId());
        ae.setPackageId(pkg.getId());
        de.setActivities(List.of(ae));
        req.setDestinations(List.of(de));

        assertThatThrownBy(() -> bookingService.createBookingEntity(req, true))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getBookingById_includesAmountPaidAndPaymentHistory() {
        Booking booking = TestDataFactory.booking(BookingStatus.DEPOSIT_PAID);
        booking.setTotalAmount(new BigDecimal("40.00"));
        booking.setAmountPaid(new BigDecimal("12.00"));
        booking.setBookingItems(List.of());

        BookingPaymentShare deposit = new BookingPaymentShare();
        deposit.setId(UUID.randomUUID());
        deposit.setType(PaymentShareType.DEPOSIT);
        deposit.setAmount(new BigDecimal("12.00"));
        deposit.setPaid(true);
        deposit.setPaymentUrl("https://checkout/cs_dep");

        BookingPaymentShare balance = new BookingPaymentShare();
        balance.setId(UUID.randomUUID());
        balance.setType(PaymentShareType.BALANCE);
        balance.setAmount(new BigDecimal("28.00"));
        balance.setPaid(false);
        balance.setPaymentUrl("https://pay/plink_1");

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(shareRepository.findByBookingId(booking.getId())).thenReturn(List.of(deposit, balance));

        BookingDTO dto = bookingService.getBookingById(booking.getId());

        assertThat(dto.getAmountPaid()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(dto.getPaymentLinks()).hasSize(2);

        BookingDTO.PaymentLinkDTO depositLink = dto.getPaymentLinks().stream()
                .filter(pl -> "DEPOSIT".equals(pl.getType()))
                .findFirst().orElseThrow();
        assertThat(depositLink.isPaid()).isTrue();

        BookingDTO.PaymentLinkDTO balanceLink = dto.getPaymentLinks().stream()
                .filter(pl -> "BALANCE".equals(pl.getType()))
                .findFirst().orElseThrow();
        assertThat(balanceLink.getUrl()).isEqualTo("https://pay/plink_1");
        assertThat(balanceLink.isPaid()).isFalse();
    }

    @Test
    void createBookingEntity_leadFlow_keepsClientPriceForNonCatalogItem() {
        // Regression: the lenient export/lead path (no money charged) still honors client prices.
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        TripExportRequest req = new TripExportRequest();
        req.setUserEmail("a@b.com");
        req.setCustomerName("A");
        req.setNumberOfTravelers(2);
        TripExportRequest.DestinationExport de = new TripExportRequest.DestinationExport();
        de.setDestinationName(destination.getName());
        TripExportRequest.ActivityExport ae = new TripExportRequest.ActivityExport();
        ae.setActivityId(null);
        ae.setPrice(50.0);
        de.setActivities(List.of(ae));
        req.setDestinations(List.of(de));

        Booking saved = bookingService.createBookingEntity(req); // lenient default overload

        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100.00")); // 50 * 2 travelers
    }

    // -----------------------------------------------------------------------
    // verifyChargeablePricing — C1 guard for charging an EXISTING lead booking
    // (success-screen deposit): every line must be catalog-anchored.
    // -----------------------------------------------------------------------

    private static BookingItem chargeableItem(Activity act, BigDecimal price, int quantity) {
        BookingItem item = new BookingItem();
        item.setActivity(act);
        item.setPrice(price);
        item.setQuantity(quantity);
        return item;
    }

    @Test
    void verifyChargeablePricing_passes_forCatalogAnchoredBooking() {
        Booking booking = new Booking();
        booking.setBookingItems(List.of(
                chargeableItem(activity, new BigDecimal("50.00"), 2),
                chargeableItem(TestDataFactory.activity(destination), new BigDecimal("25.00"), 2)));
        booking.setTotalAmount(new BigDecimal("150.00"));

        bookingService.verifyChargeablePricing(booking); // must not throw
    }

    @Test
    void verifyChargeablePricing_passes_forPackageBookingMatchingCatalog() {
        com.myhive.backend.entity.Package pkg = TestDataFactory.pkg(destination); // catalog discount 15%
        com.myhive.backend.entity.PackageActivity pa = new com.myhive.backend.entity.PackageActivity();
        pa.setActivity(activity);
        pkg.setPackageActivities(List.of(pa));
        BookingItem item = chargeableItem(activity, new BigDecimal("100.00"), 1);
        item.setPkg(pkg);
        item.setPackageDiscountPct(pkg.getDiscountPct());
        Booking booking = new Booking();
        booking.setBookingItems(List.of(item));
        booking.setTotalAmount(new BigDecimal("85.00")); // 100 − 15%

        bookingService.verifyChargeablePricing(booking); // must not throw
    }

    @Test
    void verifyChargeablePricing_rejectsItemWithoutCatalogActivity() {
        // A line without an activity FK was priced from the request body (lenient flow) — not chargeable.
        BookingItem foreign = chargeableItem(null, new BigDecimal("0.01"), 1);
        Booking booking = new Booking();
        booking.setBookingItems(List.of(foreign));
        booking.setTotalAmount(new BigDecimal("0.01"));

        assertThatThrownBy(() -> bookingService.verifyChargeablePricing(booking))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void verifyChargeablePricing_rejectsDiscountSnapshotMismatch() {
        // C1: a crafted lead booking with packageDiscountPct=99 while the catalog says 15 must not be charged.
        com.myhive.backend.entity.Package pkg = TestDataFactory.pkg(destination);
        com.myhive.backend.entity.PackageActivity pa = new com.myhive.backend.entity.PackageActivity();
        pa.setActivity(activity);
        pkg.setPackageActivities(List.of(pa));
        BookingItem item = chargeableItem(activity, new BigDecimal("100.00"), 1);
        item.setPkg(pkg);
        item.setPackageDiscountPct(new BigDecimal("99.00"));
        Booking booking = new Booking();
        booking.setBookingItems(List.of(item));
        booking.setTotalAmount(new BigDecimal("1.00"));

        assertThatThrownBy(() -> bookingService.verifyChargeablePricing(booking))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void verifyChargeablePricing_rejectsActivityNotInPackage() {
        // C1: an activity cannot borrow another package's discount — membership is re-verified.
        com.myhive.backend.entity.Package pkg = TestDataFactory.pkg(destination); // empty packageActivities
        BookingItem item = chargeableItem(activity, new BigDecimal("100.00"), 1);
        item.setPkg(pkg);
        item.setPackageDiscountPct(pkg.getDiscountPct());
        Booking booking = new Booking();
        booking.setBookingItems(List.of(item));
        booking.setTotalAmount(new BigDecimal("85.00"));

        assertThatThrownBy(() -> bookingService.verifyChargeablePricing(booking))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void verifyChargeablePricing_rejectsStoredTotalMismatch() {
        // Integrity: the persisted total must equal the total recomputed from the line items.
        Booking booking = new Booking();
        booking.setBookingItems(List.of(chargeableItem(activity, new BigDecimal("50.00"), 2)));
        booking.setTotalAmount(new BigDecimal("40.00"));

        assertThatThrownBy(() -> bookingService.verifyChargeablePricing(booking))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createBookingEntity_groupMinimum_floorsLineTotal() {
        // 2 travelers × €50 = €100 < €300 group minimum -> the line bills €300.
        TripExportRequest request = TestDataFactory.tripExportRequest();
        activity.setPrice(new BigDecimal("50.00"));
        activity.setMinPrice(new BigDecimal("300.00"));
        UUID activityId = request.getDestinations().getFirst().getActivities().getFirst().getActivityId();
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        Booking result = bookingService.createBookingEntity(request);

        BigDecimal expectedTotal = new BigDecimal("300.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo(expectedTotal);
        // SEC-1: the snapshot comes from the catalog entity, not the request body.
        assertThat(result.getBookingItems().getFirst().getMinPrice()).isEqualByComparingTo("300.00");
    }

    @Test
    void createBookingEntity_groupMinimumBelowLineTotal_keepsRegularPricing() {
        // 2 travelers × €50 = €100 >= €80 minimum -> per-person math unchanged.
        TripExportRequest request = TestDataFactory.tripExportRequest();
        activity.setPrice(new BigDecimal("50.00"));
        activity.setMinPrice(new BigDecimal("80.00"));
        UUID activityId = request.getDestinations().getFirst().getActivities().getFirst().getActivityId();
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.createBookingEntity(request);

        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void createBookingEntity_zeroMinimum_meansNoFloor() {
        // 2 travelers x EUR50.00 = EUR100.00; minPrice of ZERO must not act as a floor.
        BigDecimal expectedTotal = new BigDecimal("100.00");
        TripExportRequest request = TestDataFactory.tripExportRequest();
        activity.setPrice(new BigDecimal("50.00"));
        activity.setMinPrice(BigDecimal.ZERO);
        UUID activityId = request.getDestinations().getFirst().getActivities().getFirst().getActivityId();
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.createBookingEntity(request);

        assertThat(result.getTotalAmount()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void createBookingEntity_packageGroup_floorsLinesBeforeDiscount() {
        // Line A: max(2×€50, €300) = 300; line B: 2×€40 = 80. (300+80) − 10% = 342.00
        Activity floored = TestDataFactory.activity(destination);
        floored.setPrice(new BigDecimal("50.00"));
        floored.setMinPrice(new BigDecimal("300.00"));
        Activity regular = TestDataFactory.activity(destination);
        regular.setPrice(new BigDecimal("40.00"));
        com.myhive.backend.entity.Package pkg = TestDataFactory.pkg(destination);

        TripExportRequest request = TestDataFactory.tripExportRequest();
        TripExportRequest.ActivityExport ae1 = new TripExportRequest.ActivityExport();
        ae1.setActivityId(floored.getId());
        ae1.setActivityName("Boat Rental");
        ae1.setPackageId(pkg.getId());
        ae1.setPackageDiscountPct(new BigDecimal("10.00"));
        TripExportRequest.ActivityExport ae2 = new TripExportRequest.ActivityExport();
        ae2.setActivityId(regular.getId());
        ae2.setActivityName("Bar Crawl");
        ae2.setPackageId(pkg.getId());
        ae2.setPackageDiscountPct(new BigDecimal("10.00"));
        request.getDestinations().getFirst().setActivities(List.of(ae1, ae2));

        when(activityRepository.findById(floored.getId())).thenReturn(Optional.of(floored));
        when(activityRepository.findById(regular.getId())).thenReturn(Optional.of(regular));
        when(packageRepository.findById(pkg.getId())).thenReturn(Optional.of(pkg));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.createBookingEntity(request);

        BigDecimal expectedTotal = new BigDecimal("342.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void verifyChargeablePricing_flooredStoredTotal_passes() {
        // A booking whose stored total came from a floored line must recompute identically.
        Booking booking = new Booking();
        BookingItem item = new BookingItem();
        item.setActivity(activity);
        item.setPrice(new BigDecimal("50.00"));
        item.setQuantity(2);
        item.setMinPrice(new BigDecimal("300.00"));
        booking.setBookingItems(List.of(item));
        booking.setTotalAmount(new BigDecimal("300.00"));

        assertThatCode(() -> bookingService.verifyChargeablePricing(booking))
                .doesNotThrowAnyException();
    }
}
