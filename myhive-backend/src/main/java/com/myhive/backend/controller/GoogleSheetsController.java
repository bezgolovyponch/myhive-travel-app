package com.myhive.backend.controller;

import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.service.GoogleSheetsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/google-sheets")
@RequiredArgsConstructor
@Slf4j
public class GoogleSheetsController {

    private final GoogleSheetsService googleSheetsService;

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

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "spreadsheetId", spreadsheetId,
                    "message", "Trip exported successfully to Google Sheets"
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
