package com.myhive.backend.controller;

import com.myhive.backend.dto.BookingDTO;
import com.myhive.backend.dto.CreateBookingRequest;
import com.myhive.backend.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Validated
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingDTO> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        BookingDTO booking = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingDTO> getBookingById(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    @GetMapping
    public ResponseEntity<List<BookingDTO>> getBookingsByEmail(
            @RequestParam @NotBlank @Email String email) {
        return ResponseEntity.ok(bookingService.getBookingsByEmail(email));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<BookingDTO> updateBookingStatus(
            @PathVariable UUID id,
            @Valid @RequestBody Map<@Size(max = 50) String, @Size(max = 100) String> statusUpdate) {
        
        String status = statusUpdate.get("status");
        String stripeSessionId = statusUpdate.get("stripeSessionId");
        
        BookingDTO updatedBooking = bookingService.updateBookingStatus(id, status, stripeSessionId);
        return ResponseEntity.ok(updatedBooking);
    }
}
