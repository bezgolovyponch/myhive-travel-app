package com.myhive.backend.service;

import com.myhive.backend.dto.*;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.BookingItem;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ActivityRepository activityRepository;
    private final EmailService emailService;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Transactional
    public BookingDTO createBooking(CreateBookingRequest request) {
        Booking booking = new Booking();
        booking.setUserEmail(request.getUserEmail());
        booking.setStatus(BookingStatus.PENDING);

        List<BookingItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

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
            totalAmount = totalAmount.add(activity.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        booking.setBookingItems(items);
        booking.setTotalAmount(totalAmount);

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
                .collect(Collectors.toList());
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
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (TripExportRequest.DestinationExport dest : request.getDestinations()) {
            if (booking.getStartDate() == null && dest.getStartDate() != null) {
                booking.setStartDate(LocalDate.parse(dest.getStartDate()));
            }
            if (booking.getEndDate() == null && dest.getEndDate() != null) {
                booking.setEndDate(LocalDate.parse(dest.getEndDate()));
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
                item.setQuantity(1);
                items.add(item);
                totalAmount = totalAmount.add(item.getPrice());
            }
        }

        booking.setBookingItems(items);
        booking.setTotalAmount(totalAmount);

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
                .collect(Collectors.toList());
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
                    .collect(Collectors.toList()));
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
        return dto;
    }
}
