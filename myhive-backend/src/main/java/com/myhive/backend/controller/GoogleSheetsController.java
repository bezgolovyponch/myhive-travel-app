package com.myhive.backend.controller;

import com.myhive.backend.dto.BookingDTO;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.service.BookingService;
import com.myhive.backend.service.EmailService;
import com.myhive.backend.service.GoogleSheetsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/google-sheets")
@RequiredArgsConstructor
@Slf4j
public class GoogleSheetsController {

    private final GoogleSheetsService googleSheetsService;
    private final BookingService bookingService;
    private final EmailService emailService;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("configured", googleSheetsService.isConfigured());
        response.put("message", googleSheetsService.isConfigured() ?
                "Google Sheets integration is ready" :
                "Google Sheets integration is not configured");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/export-trip")
    public ResponseEntity<Map<String, Object>> exportTrip(@Valid @RequestBody TripExportRequest request) {
        Map<String, Object> response = new HashMap<>();

        // 1. Always save booking to database
        try {
            BookingDTO booking = bookingService.createBookingFromExport(request);
            log.info("Booking saved to database: id={}, email={}", booking.getId(), request.getUserEmail());
            response.put("bookingId", booking.getId().toString());
        } catch (Exception e) {
            log.error("Failed to save booking to database for {}", request.getUserEmail(), e);
            response.put("bookingError", e.getMessage());
        }

        // 2. Export to Google Sheets (independent — skip if not configured)
        String googleSheetUrl = null;
        if (googleSheetsService.isConfigured()) {
            try {
                String spreadsheetId = googleSheetsService.exportTripToSheet(request);
                googleSheetUrl = "https://docs.google.com/spreadsheets/d/" + spreadsheetId;
                response.put("spreadsheetId", spreadsheetId);
                log.info("Trip exported to Google Sheets for {}", request.getUserEmail());
            } catch (Exception e) {
                log.warn("Failed to export to Google Sheets for {}", request.getUserEmail(), e);
            }
        } else {
            log.info("Google Sheets not configured — skipping export for {}", request.getUserEmail());
        }

        // 3. Send emails (independent — skip if disabled)
        if (emailEnabled) {
            try {
                emailService.sendItineraryConfirmation(request.getUserEmail(), request.getCustomerName(), request);
                log.info("Confirmation email sent to {}", request.getUserEmail());
            } catch (Exception e) {
                log.warn("Failed to send confirmation email to {}", request.getUserEmail(), e);
            }
            if (googleSheetUrl != null) {
                try {
                    emailService.sendBookingNotification("admin@myhive-travel.com", request.getCustomerName(), request, googleSheetUrl);
                    log.info("Booking notification sent to admin");
                } catch (Exception e) {
                    log.warn("Failed to send admin notification", e);
                }
            }
        }

        response.put("success", true);
        response.put("message", "Booking received successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/create-spreadsheet")
    public ResponseEntity<Map<String, Object>> createSpreadsheet(@RequestParam String title) {
        try {
            if (!googleSheetsService.isConfigured()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Google Sheets integration is not configured"
                ));
            }

            String spreadsheetId = googleSheetsService.createNewSpreadsheet(title);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "spreadsheetId", spreadsheetId,
                    "message", "Spreadsheet created successfully"
            ));

        } catch (Exception e) {
            log.error("Error creating spreadsheet", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Failed to create spreadsheet: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/spreadsheet/{spreadsheetId}/sheets")
    public ResponseEntity<Map<String, Object>> getSpreadsheetSheets(@PathVariable String spreadsheetId) {
        try {
            if (!googleSheetsService.isConfigured()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Google Sheets integration is not configured"
                ));
            }

            List<String> sheetNames = googleSheetsService.getSpreadsheetSheets(spreadsheetId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "sheets", sheetNames,
                    "message", "Retrieved sheet names successfully"
            ));

        } catch (Exception e) {
            log.error("Error retrieving spreadsheet sheets", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Failed to retrieve sheets: " + e.getMessage()
            ));
        }
    }
}
