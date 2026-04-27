package com.myhive.backend.service;

import com.myhive.backend.dto.BookingDTO;
import com.myhive.backend.dto.BookingItemDTO;
import com.myhive.backend.dto.BookingStatsDTO;
import com.myhive.backend.dto.CreateBookingRequest;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.BookingItem;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.BookingRepository;
import com.myhive.backend.repository.PackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BookingService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BookingRepository bookingRepository;
    private final ActivityRepository activityRepository;
    private final PackageRepository packageRepository;
    private final EmailService emailService;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Transactional
    public BookingDTO createBooking(CreateBookingRequest request) {
        Booking booking = new Booking();
        booking.setUserEmail(request.getUserEmail());
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
        return convertToDTO(booking);
    }

    public List<BookingDTO> getBookingsByEmail(String email) {
        return bookingRepository.findByUserEmail(email).stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Transactional
    public BookingDTO createBookingFromExport(TripExportRequest request) {
        Booking booking = new Booking();
        booking.setUserEmail(request.getUserEmail());
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
                if (act.getActivityId() != null) {
                    Activity activity = activityRepository.findById(act.getActivityId())
                            .orElseThrow(() -> new ResourceNotFoundException("Activity", act.getActivityId()));
                    item.setActivity(activity);
                }
                item.setActivityName(act.getActivityName());
                item.setDestinationName(dest.getDestinationName());
                item.setPrice(act.getPrice() != null ? BigDecimal.valueOf(act.getPrice()) : BigDecimal.ZERO);

                int travelers = request.getNumberOfTravelers() != null ? request.getNumberOfTravelers() : 1;
                item.setQuantity(travelers);

                if (act.getPackageId() != null) {
                    item.setPkg(packageRepository.findById(act.getPackageId()).orElse(null));
                    item.setPackageName(act.getPackageName());
                    item.setPackageDiscountPct(act.getPackageDiscountPct());
                }

                items.add(item);
            }
        }

        booking.setBookingItems(items);
        booking.setTotalAmount(calculateTotal(items));

        Booking saved = bookingRepository.save(booking);
        log.info("Booking created successfully: id={}, customer={}, email={}, items={}, total={}",
                saved.getId(), saved.getCustomerName(), saved.getUserEmail(),
                saved.getBookingItems().size(), saved.getTotalAmount());

        if (emailEnabled) {
            try {
                log.info("Email sending is enabled, attempting to send confirmation to: {}", saved.getUserEmail());
                emailService.sendItineraryConfirmation(saved.getUserEmail(), saved.getCustomerName(), request);
                log.info("Confirmation email sent successfully to: {}", saved.getUserEmail());
            } catch (Exception e) {
                log.error("Failed to send confirmation email to: {}. Error: {}", saved.getUserEmail(), e.getMessage(), e);
            }
        } else {
            log.info("Email sending is disabled (app.email.enabled=false), skipping confirmation email");
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
    public BookingDTO updateBookingStatus(UUID id, String status, String stripeSessionId) {
        BookingStatus bookingStatus;
        try {
            bookingStatus = BookingStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid booking status: " + status);
        }

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));

        booking.setStatus(bookingStatus);
        if (stripeSessionId != null) {
            booking.setStripeSessionId(stripeSessionId);
        }
        if (BookingStatus.PAID == bookingStatus) {
            booking.setPaidAt(LocalDateTime.now());
        }

        Booking updatedBooking = bookingRepository.save(booking);
        return convertToDTO(updatedBooking);
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

    private static @NonNull BigDecimal getGroupTotal(Map.Entry<UUID, List<BookingItem>> entry) {
        BigDecimal groupTotal = BigDecimal.ZERO;
        for (BookingItem it : entry.getValue()) {
            BigDecimal qty = BigDecimal.valueOf(it.getQuantity() == null ? 1 : it.getQuantity());
            groupTotal = groupTotal.add(it.getPrice().multiply(qty));
        }
        if (entry.getKey() != null) {
            BigDecimal pct = entry.getValue().getFirst().getPackageDiscountPct();
            if (pct == null) {
                pct = BigDecimal.ZERO;
            }
            groupTotal = groupTotal.multiply(HUNDRED.subtract(pct))
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }
        return groupTotal;
    }
}
