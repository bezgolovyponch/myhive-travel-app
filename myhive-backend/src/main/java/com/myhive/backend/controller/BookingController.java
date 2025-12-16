package com.myhive.backend.controller;

import com.myhive.backend.dto.BookingDTO;
import com.myhive.backend.dto.CreateBookingRequest;
import com.myhive.backend.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingDTO> createBooking(@RequestBody CreateBookingRequest request) {
        BookingDTO booking = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingDTO> getBookingById(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    @GetMapping
    public ResponseEntity<List<BookingDTO>> getBookingsByEmail(@RequestParam String email) {
        return ResponseEntity.ok(bookingService.getBookingsByEmail(email));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<BookingDTO> updateBookingStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> statusUpdate) {
        
        String status = statusUpdate.get("status");
        String stripeSessionId = statusUpdate.get("stripeSessionId");
        
        BookingDTO updatedBooking = bookingService.updateBookingStatus(id, status, stripeSessionId);
        return ResponseEntity.ok(updatedBooking);
    }
}
