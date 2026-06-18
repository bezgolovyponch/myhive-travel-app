package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.BookingDTO;
import com.myhive.backend.dto.BookingStatsDTO;
import com.myhive.backend.dto.CreateBookingRequest;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.BookingRepository;
import com.myhive.backend.repository.PackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @InjectMocks
    private BookingService bookingService;

    private Destination destination;
    private Activity activity;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bookingService, "emailEnabled", false);
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
    void getBookingsByEmail_returnsMatchingBookings() {
        Booking b1 = TestDataFactory.booking(BookingStatus.PENDING);
        b1.setBookingItems(List.of());
        when(bookingRepository.findByUserEmail("user@test.com")).thenReturn(List.of(b1));

        List<BookingDTO> result = bookingService.getBookingsByEmail("user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getUserEmail()).isEqualTo("user@test.com");
    }

    @Test
    void updateBookingStatus_validStatus_updatesAndSaves() {
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setBookingItems(List.of());
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingDTO result = bookingService.updateBookingStatus(booking.getId(), "CONFIRMED", null);

        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void updateBookingStatus_toPAID_setsPaidAt() {
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setBookingItems(List.of());
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingDTO result = bookingService.updateBookingStatus(booking.getId(), "PAID", "sess_123");

        assertThat(result.getStatus()).isEqualTo("PAID");
        assertThat(result.getPaidAt()).isNotNull();
        assertThat(result.getStripeSessionId()).isEqualTo("sess_123");
    }

    @Test
    void updateBookingStatus_withNullStripeSessionId_doesNotOverwrite() {
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setStripeSessionId("existing_session");
        booking.setBookingItems(List.of());
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingDTO result = bookingService.updateBookingStatus(booking.getId(), "CONFIRMED", null);

        assertThat(result.getStripeSessionId()).isEqualTo("existing_session");
    }

    @Test
    void updateBookingStatus_invalidStatus_throwsBadRequest() {
        assertThatThrownBy(() -> bookingService.updateBookingStatus(UUID.randomUUID(), "INVALID", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid booking status");
    }

    @Test
    void updateBookingStatus_bookingNotFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(bookingRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.updateBookingStatus(id, "PAID", null))
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
    void createBookingFromExport_withEmailEnabled_sendsEmail() {
        ReflectionTestUtils.setField(bookingService, "emailEnabled", true);
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
    void createBookingFromExport_withEmailDisabled_doesNotSendEmail() {
        TripExportRequest request = TestDataFactory.tripExportRequest();
        UUID activityId = request.getDestinations().getFirst().getActivities().getFirst().getActivityId();
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        bookingService.createBookingFromExport(request);

        verifyNoInteractions(emailService);
    }

    @Test
    void createBookingFromExport_withEmailFailure_stillReturnsBooking() {
        ReflectionTestUtils.setField(bookingService, "emailEnabled", true);
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
}
