package com.myhive.backend.service;

import com.myhive.backend.dto.BookingDTO;
import com.myhive.backend.dto.BookingItemDTO;
import com.myhive.backend.dto.BookingStatsDTO;
import com.myhive.backend.dto.CreateBookingRequest;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.BookingItem;
import com.myhive.backend.entity.BookingPaymentShare;
import com.myhive.backend.entity.Package;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.model.PaymentShareType;
import com.myhive.backend.payment.StripeGateway;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.BookingPaymentShareRepository;
import com.myhive.backend.repository.BookingRepository;
import com.myhive.backend.repository.PackageRepository;
import com.myhive.backend.util.EmailMasker;
import com.myhive.backend.util.MoneyMath;
import com.myhive.backend.util.Translations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BookingService {

    // The only statuses an admin may set by hand; payment statuses are webhook-owned.
    private static final Set<BookingStatus> ADMIN_SETTABLE_STATUSES =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED);

    private final BookingRepository bookingRepository;
    private final ActivityRepository activityRepository;
    private final PackageRepository packageRepository;
    private final EmailService emailService;
    private final BookingPaymentShareRepository shareRepository;
    private final StripeGateway stripeGateway;
    private final MetaCapiService metaCapiService;

    @Transactional
    public BookingDTO createBooking(CreateBookingRequest request) {
        Booking booking = new Booking();
        booking.setUserEmail(request.getUserEmail());
        booking.setLocale(Translations.normalize(request.getLocale()));
        booking.setStatus(BookingStatus.PENDING);

        List<BookingItem> items = new ArrayList<>();

        for (CreateBookingRequest.BookingActivityItem item : request.getActivities()) {
            Activity activity = activityRepository.findById(item.getActivityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Activity", item.getActivityId()));

            BookingItem bookingItem = new BookingItem();
            bookingItem.setBooking(booking);
            bookingItem.setActivity(activity);
            bookingItem.setActivityName(activity.getName());
            bookingItem.setDestinationName(activity.getDestination().getName());
            bookingItem.setPrice(activity.getPrice());
            bookingItem.setMinPrice(activity.getMinPrice());
            bookingItem.setQuantity(item.getQuantity());

            items.add(bookingItem);
        }

        booking.setBookingItems(items);
        booking.setTotalAmount(calculateTotal(items));

        Booking savedBooking = bookingRepository.save(booking);
        return convertToDTO(savedBooking);
    }

    public BookingDTO getBookingById(UUID id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        BookingDTO dto = convertToDTO(booking);
        dto.setAmountPaid(booking.getAmountPaid());
        dto.setDepositAmount(booking.getDepositAmount());
        dto.setPaymentLinks(shareRepository.findByBookingId(booking.getId()).stream()
                .filter(s -> s.getType() == PaymentShareType.DEPOSIT
                        || (s.getType() == PaymentShareType.BALANCE && s.getPaymentUrl() != null))
                .map(s -> new BookingDTO.PaymentLinkDTO(s.getId(), s.getAmount(), s.isPaid(),
                        s.getPaymentUrl(), s.getType().name()))
                .toList());
        return dto;
    }

    @Transactional
    public Booking createBookingEntity(TripExportRequest request) {
        return createBookingEntity(request, false);
    }

    /**
     * @param enforceTrustedPricing when true (the paid Stripe flow), every line is priced exclusively
     *     from the catalog: each item must reference a resolvable {@code activityId}, package discounts
     *     come from the persisted {@link Package} (never the request body), the activity must actually
     *     belong to the package, and the computed total must be positive. Prevents a client from
     *     deflating the charged amount (C1). When false (the export/lead flow, no money charged) the
     *     historical lenient behavior is preserved.
     */
    @Transactional
    public Booking createBookingEntity(TripExportRequest request, boolean enforceTrustedPricing) {
        Booking booking = new Booking();
        booking.setUserEmail(request.getUserEmail());
        booking.setLocale(Translations.normalize(request.getLocale()));
        booking.setStatus(BookingStatus.PENDING);
        booking.setCustomerName(request.getCustomerName());
        booking.setPhone(request.getPhone());
        booking.setNumberOfTravelers(request.getNumberOfTravelers());
        booking.setNotes(request.getNotes());

        List<BookingItem> items = new ArrayList<>();

        for (TripExportRequest.DestinationExport dest : request.getDestinations()) {
            if (booking.getStartDate() == null && dest.getStartDate() != null) {
                try {
                    booking.setStartDate(LocalDate.parse(dest.getStartDate()));
                } catch (DateTimeParseException e) {
                    throw new BadRequestException("Invalid date format for startDate: " + dest.getStartDate() + ". Expected format: yyyy-MM-dd");
                }
            }
            if (booking.getEndDate() == null && dest.getEndDate() != null) {
                try {
                    booking.setEndDate(LocalDate.parse(dest.getEndDate()));
                } catch (DateTimeParseException e) {
                    throw new BadRequestException("Invalid date format for endDate: " + dest.getEndDate() + ". Expected format: yyyy-MM-dd");
                }
            }

            for (TripExportRequest.ActivityExport act : dest.getActivities()) {
                BookingItem item = new BookingItem();
                item.setBooking(booking);
                Activity activity = null;
                if (act.getActivityId() != null) {
                    activity = activityRepository.findById(act.getActivityId())
                            .orElseThrow(() -> new ResourceNotFoundException("Activity", act.getActivityId()));
                    item.setActivity(activity);
                    item.setPrice(activity.getPrice());           // trusted server-side price (SEC-1)
                    item.setMinPrice(activity.getMinPrice()); // trusted server-side floor (SEC-1)
                } else if (enforceTrustedPricing) {
                    // C1: a charged booking must price every line from the catalog, never the request body.
                    throw new BadRequestException(
                            "Each activity in a paid booking must reference a catalog activityId");
                } else {
                    item.setPrice(act.getPrice() != null ? BigDecimal.valueOf(act.getPrice()) : BigDecimal.ZERO);
                }
                item.setActivityName(act.getActivityName());
                // The catalog is the authority on where an activity belongs; the request's
                // destination grouping is a client-side display label (the Trip Builder sends
                // a "Custom Travel Package" placeholder).
                item.setDestinationName(activity != null
                        ? activity.getDestination().getName()
                        : dest.getDestinationName());

                int travelers = request.getNumberOfTravelers() != null ? request.getNumberOfTravelers() : 1;
                item.setQuantity(travelers);

                if (act.getPackageId() != null) {
                    Package pkg = packageRepository.findById(act.getPackageId()).orElse(null);
                    item.setPkg(pkg);
                    item.setPackageName(act.getPackageName());
                    if (enforceTrustedPricing) {
                        // C1: trust the persisted catalog discount, never the client value, and only
                        // after confirming this activity actually belongs to the package.
                        if (pkg == null) {
                            throw new BadRequestException("Unknown packageId: " + act.getPackageId());
                        }
                        if (activity != null && !packageContainsActivity(pkg, activity.getId())) {
                            throw new BadRequestException("Activity " + activity.getId()
                                    + " does not belong to package " + pkg.getId());
                        }
                        item.setPackageDiscountPct(pkg.getDiscountPct());
                    } else {
                        item.setPackageDiscountPct(act.getPackageDiscountPct());
                    }
                }

                items.add(item);
            }
        }

        String tripId = (request.getTripId() != null && !request.getTripId().isBlank())
                ? request.getTripId()
                : "TRV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        booking.setTripId(tripId);
        booking.setUtmSource(request.getUtmSource());
        booking.setUtmMedium(request.getUtmMedium());
        booking.setUtmCampaign(request.getUtmCampaign());
        booking.setUtmTerm(request.getUtmTerm());
        booking.setUtmContent(request.getUtmContent());
        booking.setRef(request.getRef());
        booking.setGclid(request.getGclid());
        booking.setFbclid(request.getFbclid());
        booking.setReferrer(request.getReferrer());
        booking.setFirstTouchAt(request.getFirstTouchAt());
        booking.setFirstUtmSource(request.getFirstUtmSource());
        booking.setFirstUtmCampaign(request.getFirstUtmCampaign());

        booking.setBookingItems(items);
        BigDecimal total = calculateTotal(items);
        if (enforceTrustedPricing && total.signum() <= 0) {
            throw new BadRequestException("Computed booking total must be positive");
        }
        booking.setTotalAmount(total);

        Booking saved = bookingRepository.save(booking);
        // Mask the customer email — PII must not land in logs unmasked (consistent with EmailService).
        log.info("Booking created successfully: id={}, email={}, items={}, total={}",
                saved.getId(), EmailMasker.mask(saved.getUserEmail()),
                saved.getBookingItems().size(), saved.getTotalAmount());
        return saved;
    }

    /**
     * C1 guard for charging money against a booking persisted by the lenient lead flow (the
     * success-screen deposit): every line must be catalog-anchored — an {@link Activity} FK whose
     * price was copied from the catalog at creation (SEC-1), a package discount snapshot that still
     * matches the persisted {@link Package}, actual package membership — and the stored total must
     * equal the total recomputed from those lines. Rejects hand-crafted bookings whose prices or
     * discounts came from the request body.
     */
    public void verifyChargeablePricing(Booking booking) {
        for (BookingItem item : booking.getBookingItems()) {
            if (item.getActivity() == null) {
                throw new BadRequestException("This booking cannot be paid online — please contact us");
            }
            if (item.getPkg() != null) {
                if (!packageContainsActivity(item.getPkg(), item.getActivity().getId())
                        || !discountMatchesCatalog(item.getPackageDiscountPct(), item.getPkg().getDiscountPct())) {
                    throw new BadRequestException("This booking cannot be paid online — please contact us");
                }
            }
        }
        BigDecimal recomputed = calculateTotal(booking.getBookingItems());
        if (booking.getTotalAmount() == null || recomputed.compareTo(booking.getTotalAmount()) != 0) {
            throw new BadRequestException("This booking cannot be paid online — please contact us");
        }
    }

    private static boolean discountMatchesCatalog(BigDecimal snapshot, BigDecimal catalog) {
        if (snapshot == null || catalog == null) {
            return snapshot == null && catalog == null;
        }
        return snapshot.compareTo(catalog) == 0;
    }

    /**
     * Rebuild a {@link TripExportRequest} from a persisted booking. The deposit flow persists the booking
     * up front but only sends the itinerary confirmation / inbox notification once the deposit is paid —
     * and at that point (the Stripe webhook) only the Booking entity is on hand, not the original request.
     * This inverts {@link #createBookingEntity} so those emails render the same itinerary.
     */
    public TripExportRequest toExportRequest(Booking booking) {
        TripExportRequest request = new TripExportRequest();
        request.setTripName("Booking");
        request.setUserEmail(booking.getUserEmail());
        request.setLocale(booking.getLocale());
        request.setCustomerName(booking.getCustomerName());
        request.setPhone(booking.getPhone());
        request.setNumberOfTravelers(booking.getNumberOfTravelers());
        request.setNotes(booking.getNotes());
        request.setTripId(booking.getTripId());

        String startDate = booking.getStartDate() != null ? booking.getStartDate().toString() : null;
        String endDate = booking.getEndDate() != null ? booking.getEndDate().toString() : null;

        // Group items under their destination, preserving booked order.
        Map<String, TripExportRequest.DestinationExport> byDestination = new LinkedHashMap<>();
        for (BookingItem item : booking.getBookingItems()) {
            TripExportRequest.DestinationExport dest = byDestination.computeIfAbsent(
                    item.getDestinationName(), name -> {
                        TripExportRequest.DestinationExport d = new TripExportRequest.DestinationExport();
                        d.setDestinationName(name);
                        d.setStartDate(startDate);
                        d.setEndDate(endDate);
                        d.setActivities(new ArrayList<>());
                        return d;
                    });

            TripExportRequest.ActivityExport activity = new TripExportRequest.ActivityExport();
            activity.setActivityId(item.getActivity() != null ? item.getActivity().getId() : null);
            activity.setActivityName(item.getActivityName());
            activity.setPrice(item.getPrice() != null ? item.getPrice().doubleValue() : null);
            activity.setMinPrice(item.getMinPrice());
            if (item.getPkg() != null) {
                activity.setPackageId(item.getPkg().getId());
                activity.setPackageName(item.getPackageName());
                activity.setPackageDiscountPct(item.getPackageDiscountPct());
            }
            dest.getActivities().add(activity);
        }
        request.setDestinations(new ArrayList<>(byDestination.values()));
        return request;
    }

    @Transactional
    public BookingDTO createBookingFromExport(TripExportRequest request) {
        Booking saved = createBookingEntity(request);

        try {
            emailService.sendItineraryConfirmation(saved.getUserEmail(), saved.getCustomerName(), request, saved.getTripId());
        } catch (Exception e) {
            log.error("Failed to send confirmation email to: {}. Error: {}", EmailMasker.mask(saved.getUserEmail()), e.getMessage(), e);
        }

        // Internal heads-up to the bookings inbox. Kept in its own try/catch so a failure here
        // never suppresses the customer confirmation above, nor fails the booking.
        try {
            emailService.sendBookingNotification(saved, request);
        } catch (Exception e) {
            log.error("Failed to send booking notification for booking: {}. Error: {}", saved.getId(), e.getMessage(), e);
        }

        try {
            metaCapiService.sendEvent(MetaCapiService.MetaCapiEvent.of(
                            "Lead",
                            request.getEventId() != null && !request.getEventId().isBlank()
                                    ? request.getEventId()
                                    : saved.getId().toString())
                    .value(saved.getTotalAmount(), "EUR")
                    .email(saved.getUserEmail())
                    .fbp(request.getFbp())
                    .fbc(MetaCapiService.fbcFrom(request.getFbc(), request.getFbclid())));
        } catch (Exception e) {
            log.error("Failed to send Lead CAPI event for booking {}: {}", saved.getId(), e.getMessage(), e);
        }

        return convertToDTO(saved);
    }

    public BookingStatsDTO getBookingStats() {
        long total = bookingRepository.count();
        long pending = bookingRepository.countByStatus(BookingStatus.PENDING);
        long confirmed = bookingRepository.countByStatus(BookingStatus.CONFIRMED);
        long paid = bookingRepository.countByStatus(BookingStatus.PAID);
        return new BookingStatsDTO(total, pending, confirmed, paid);
    }

    public List<BookingDTO> getAllBookings() {
        return bookingRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Transactional
    public BookingDTO updateBookingStatus(UUID id, String status) {
        BookingStatus bookingStatus;
        try {
            bookingStatus = BookingStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid booking status: " + status);
        }
        // Payment statuses are owned by the Stripe webhook (PaymentService); setting them
        // here would desync the booking from its payment-share bookkeeping.
        if (!ADMIN_SETTABLE_STATUSES.contains(bookingStatus)) {
            throw new BadRequestException(
                    "Status " + bookingStatus + " is managed by the Stripe webhook and cannot be set manually");
        }

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));

        // Once money is in (DEPOSIT_PAID/PARTIALLY_PAID/PAID/REFUNDED), the only legal manual
        // exit is cancellation — moving to PENDING/CONFIRMED would hide the collected payment.
        BookingStatus currentStatus = booking.getStatus();
        boolean leavingPaymentStatus = currentStatus != null && !ADMIN_SETTABLE_STATUSES.contains(currentStatus);
        if (leavingPaymentStatus && BookingStatus.CANCELLED != bookingStatus) {
            throw new BadRequestException(
                    "A booking in status " + currentStatus + " can only be cancelled manually");
        }

        if (BookingStatus.CANCELLED == bookingStatus) {
            deactivateOpenPaymentLinks(booking);
        }
        booking.setStatus(bookingStatus);

        Booking updatedBooking = bookingRepository.save(booking);
        return convertToDTO(updatedBooking);
    }

    /**
     * A cancelled booking must not remain payable: deactivate every unpaid Payment Link so a
     * customer holding an old URL cannot pay (the webhook would resurrect the booking).
     * Fails loud (before the status write) — a still-payable link on a cancelled booking is
     * worse than a failed cancel the admin can retry.
     */
    private void deactivateOpenPaymentLinks(Booking booking) {
        for (BookingPaymentShare share : shareRepository.findByBookingId(booking.getId())) {
            if (share.getStripePaymentLinkId() != null && !share.isPaid()) {
                stripeGateway.deactivatePaymentLink(share.getStripePaymentLinkId());
            }
        }
    }

    private BookingDTO convertToDTO(Booking booking) {
        BookingDTO dto = new BookingDTO();
        dto.setId(booking.getId());
        dto.setUserEmail(booking.getUserEmail());
        dto.setStripeSessionId(booking.getStripeSessionId());
        dto.setTotalAmount(booking.getTotalAmount());
        dto.setStatus(booking.getStatus() != null ? booking.getStatus().name() : null);
        dto.setCreatedAt(booking.getCreatedAt());
        dto.setPaidAt(booking.getPaidAt());
        dto.setCustomerName(booking.getCustomerName());
        dto.setPhone(booking.getPhone());
        dto.setNumberOfTravelers(booking.getNumberOfTravelers());
        dto.setStartDate(booking.getStartDate());
        dto.setEndDate(booking.getEndDate());
        dto.setNotes(booking.getNotes());
        dto.setTripId(booking.getTripId());
        dto.setUtmSource(booking.getUtmSource());
        dto.setUtmMedium(booking.getUtmMedium());
        dto.setUtmCampaign(booking.getUtmCampaign());
        dto.setUtmTerm(booking.getUtmTerm());
        dto.setUtmContent(booking.getUtmContent());
        dto.setRef(booking.getRef());
        dto.setGclid(booking.getGclid());
        dto.setFbclid(booking.getFbclid());
        dto.setReferrer(booking.getReferrer());
        dto.setFirstTouchAt(booking.getFirstTouchAt());
        dto.setFirstUtmSource(booking.getFirstUtmSource());
        dto.setFirstUtmCampaign(booking.getFirstUtmCampaign());

        if (booking.getBookingItems() != null) {
            dto.setItems(booking.getBookingItems().stream()
                    .map(this::convertItemToDTO)
                    .toList());
        }
        
        return dto;
    }

    private BookingItemDTO convertItemToDTO(BookingItem item) {
        BookingItemDTO dto = new BookingItemDTO();
        dto.setId(item.getId());
        dto.setActivityId(item.getActivity() != null ? item.getActivity().getId() : null);
        dto.setActivityName(item.getActivityName());
        dto.setDestinationName(item.getDestinationName());
        dto.setPrice(item.getPrice());
        dto.setQuantity(item.getQuantity());
        dto.setPackageId(item.getPkg() != null ? item.getPkg().getId() : null);
        dto.setPackageName(item.getPackageName());
        dto.setPackageDiscountPct(item.getPackageDiscountPct());
        return dto;
    }

    private BigDecimal calculateTotal(List<BookingItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        Map<UUID, List<BookingItem>> grouped = new LinkedHashMap<>();
        for (BookingItem it : items) {
            UUID key = it.getPkg() != null ? it.getPkg().getId() : null;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(it);
        }
        for (Map.Entry<UUID, List<BookingItem>> entry : grouped.entrySet()) {
            BigDecimal groupTotal = getGroupTotal(entry);
            total = total.add(groupTotal);
        }
        return total;
    }

    private static boolean packageContainsActivity(Package pkg, UUID activityId) {
        return pkg.getPackageActivities().stream()
                .anyMatch(pa -> pa.getActivity() != null && activityId.equals(pa.getActivity().getId()));
    }

    private static @NonNull BigDecimal getGroupTotal(Map.Entry<UUID, List<BookingItem>> entry) {
        BigDecimal groupTotal = BigDecimal.ZERO;
        for (BookingItem it : entry.getValue()) {
            groupTotal = groupTotal.add(lineTotal(it));
        }
        if (entry.getKey() != null) {
            groupTotal = MoneyMath.applyDiscountPct(groupTotal,
                    entry.getValue().getFirst().getPackageDiscountPct());
        }
        return groupTotal;
    }

    /** Group-minimum floor: a line never bills below the activity's minPrice snapshot. */
    private static BigDecimal lineTotal(BookingItem it) {
        BigDecimal qty = BigDecimal.valueOf(it.getQuantity() == null ? 1 : it.getQuantity());
        BigDecimal line = it.getPrice().multiply(qty);
        BigDecimal minPrice = it.getMinPrice();
        if (minPrice != null && line.compareTo(minPrice) < 0) {
            return minPrice;
        }
        return line;
    }
}
