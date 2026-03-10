package com.myhive.backend.controller;

import com.myhive.backend.dto.BookingDTO;
import com.myhive.backend.dto.BookingStatsDTO;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final BookingService bookingService;

    @GetMapping("/bookings")
    public ResponseEntity<List<BookingDTO>> getAllBookings() {
        List<BookingDTO> bookings = bookingService.getAllBookings();
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<BookingDTO> getBookingById(@PathVariable UUID id) {
        BookingDTO booking = bookingService.getBookingById(id);
        return ResponseEntity.ok(booking);
    }

    @GetMapping("/bookings/stats")
    public ResponseEntity<BookingStatsDTO> getBookingStats() {
        List<BookingDTO> bookings = bookingService.getAllBookings();

        BookingStatsDTO stats = new BookingStatsDTO();
        stats.setTotal(bookings.size());
        stats.setPending(bookings.stream().filter(b -> BookingStatus.PENDING.name().equals(b.getStatus())).count());
        stats.setConfirmed(bookings.stream().filter(b -> BookingStatus.CONFIRMED.name().equals(b.getStatus())).count());
        stats.setPaid(bookings.stream().filter(b -> BookingStatus.PAID.name().equals(b.getStatus())).count());

        return ResponseEntity.ok(stats);
    }
}
