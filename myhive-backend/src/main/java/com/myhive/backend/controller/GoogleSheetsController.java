package com.myhive.backend.controller;

import com.myhive.backend.dto.TripExportRequest;
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
        try {
            if (!googleSheetsService.isConfigured()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Google Sheets integration is not configured"
                ));
            }

            String spreadsheetId = googleSheetsService.exportTripToSheet(request);
            String googleSheetUrl = "https://docs.google.com/spreadsheets/d/" + spreadsheetId;

            // Send confirmation email to customer (if enabled)
            if (emailEnabled) {
                try {
                    emailService.sendItineraryConfirmation(
                            request.getUserEmail(),
                            request.getCustomerName(),
                            request
                    );
                    log.info("Confirmation email sent to customer: {}", request.getUserEmail());
                } catch (Exception emailError) {
                    log.warn("Failed to send confirmation email to customer: {}", request.getUserEmail(), emailError);
                    // Don't fail the whole operation if email fails
                }
            } else {
                log.info("Email sending is disabled. Skipping confirmation email to: {}", request.getUserEmail());
            }

            // Send notification email to admin (if enabled)
            if (emailEnabled) {
                try {
                    emailService.sendBookingNotification(
                            "admin@myhive-travel.com", // Replace with actual admin email
                            request.getCustomerName(),
                            request,
                            googleSheetUrl
                    );
                    log.info("Booking notification sent to admin");
                } catch (Exception emailError) {
                    log.warn("Failed to send booking notification to admin", emailError);
                    // Don't fail the whole operation if email fails
                }
            } else {
                log.info("Email sending is disabled. Skipping admin notification.");
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "spreadsheetId", spreadsheetId,
                    "message", "Trip exported successfully" + (emailEnabled ? " and confirmation emails sent" : " (email sending disabled)")
            ));

        } catch (Exception e) {
            log.error("Error exporting trip to Google Sheets", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Failed to export trip: " + e.getMessage()
            ));
        }
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
