package com.myhive.backend.service;

import com.myhive.backend.dto.BookingDTO;
import com.myhive.backend.dto.BookingItemDTO;
import com.myhive.backend.dto.CreateBookingRequest;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.BookingItem;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ActivityRepository activityRepository;

    @Transactional
    public BookingDTO createBooking(CreateBookingRequest request) {
        Booking booking = new Booking();
        booking.setUserEmail(request.getUserEmail());
        booking.setStatus("PENDING");
        
        List<BookingItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CreateBookingRequest.BookingActivityItem item : request.getActivities()) {
            Activity activity = activityRepository.findById(item.getActivityId())
                    .orElseThrow(() -> new RuntimeException("Activity not found: " + item.getActivityId()));

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
                .orElseThrow(() -> new RuntimeException("Booking not found with id: " + id));
        return convertToDTO(booking);
    }

    public List<BookingDTO> getBookingsByEmail(String email) {
        return bookingRepository.findByUserEmail(email).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public BookingDTO updateBookingStatus(UUID id, String status, String stripeSessionId) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Booking not found with id: " + id));
        
        booking.setStatus(status);
        if (stripeSessionId != null) {
            booking.setStripeSessionId(stripeSessionId);
        }
        if ("PAID".equals(status)) {
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
        dto.setStatus(booking.getStatus());
        dto.setCreatedAt(booking.getCreatedAt());
        dto.setPaidAt(booking.getPaidAt());
        
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
        dto.setActivityId(item.getActivity().getId());
        dto.setActivityName(item.getActivityName());
        dto.setDestinationName(item.getDestinationName());
        dto.setPrice(item.getPrice());
        dto.setQuantity(item.getQuantity());
        return dto;
    }
}
