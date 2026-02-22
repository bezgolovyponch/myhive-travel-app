package com.myhive.backend.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.myhive.backend.config.GoogleSheetsConfig;
import com.myhive.backend.dto.SheetData;
import com.myhive.backend.dto.TripExportRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSheetsService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);

    private final GoogleSheetsConfig config;
    // private final EmailService emailService; // Temporarily commented out

    public boolean isConfigured() {
        return config.isConfigured();
    }

    public String exportTripToSheet(TripExportRequest request) throws IOException, GeneralSecurityException {
        if (!isConfigured()) {
            throw new IllegalStateException("Google Sheets is not properly configured");
        }

        Sheets sheetsService = getSheetsService();

        String spreadsheetId = request.getSpreadsheetId() != null ?
                request.getSpreadsheetId() : config.getEffectiveSpreadsheetId();

        String sheetName = request.getSheetName() != null ?
                request.getSheetName() : generateSheetName(request.getTripName());

        Object[][] tripData = formatTripData(request);
        SheetData sheetData = SheetData.fromTripExport(spreadsheetId, sheetName, tripData);

        String resultSpreadsheetId = writeDataToSheet(sheetsService, sheetData);

        // Temporarily disable master summary sheet to isolate the issue
        // updateMasterSummarySheet(sheetsService, resultSpreadsheetId, request, sheetName);

        // Debug logging
        log.info("Sheet created successfully. Sheet name: {}, Range: {}, Spreadsheet ID: {}",
                sheetName, sheetData.getRange(), resultSpreadsheetId);

        // Send email confirmation with itinerary
        // try {
        //     String customerName = extractCustomerName(request);
        //     String googleSheetUrl = generateSheetUrl(resultSpreadsheetId);
        //     
        //     emailService.sendItineraryConfirmation(request.getUserEmail(), customerName, request, googleSheetUrl);
        //     
        //     // Send notification to admin (optional - configure admin email)
        //     // emailService.sendBookingNotification("admin@myhive-travel.com", customerName, request);
        //     
        //     log.info("Itinerary confirmation email sent to customer: {}", request.getUserEmail());
        // } catch (Exception e) {
        //     log.error("Failed to send itinerary confirmation email, but Google Sheets export succeeded", e);
        //     // Don't throw exception here - the Google Sheets export was successful
        // }

        return resultSpreadsheetId;
    }

    public String createNewSpreadsheet(String title) throws IOException, GeneralSecurityException {
        if (!isConfigured()) {
            throw new IllegalStateException("Google Sheets is not properly configured");
        }

        Sheets sheetsService = getSheetsService();

        Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties()
                        .setTitle(title));

        spreadsheet = sheetsService.spreadsheets()
                .create(spreadsheet)
                .setFields("spreadsheetId")
                .execute();

        log.info("Created new spreadsheet with ID: {}", spreadsheet.getSpreadsheetId());
        return spreadsheet.getSpreadsheetId();
    }

    public List<String> getSpreadsheetSheets(String spreadsheetId) throws IOException, GeneralSecurityException {
        if (!isConfigured()) {
            throw new IllegalStateException("Google Sheets is not properly configured");
        }

        Sheets sheetsService = getSheetsService();
        Spreadsheet spreadsheet = sheetsService.spreadsheets()
                .get(spreadsheetId)
                .execute();

        List<String> sheetNames = new ArrayList<>();
        for (Sheet sheet : spreadsheet.getSheets()) {
            sheetNames.add(sheet.getProperties().getTitle());
        }

        return sheetNames;
    }

    private Sheets getSheetsService() throws IOException, GeneralSecurityException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        GoogleCredentials credentials = config.createCredentials();

        return new Sheets.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                .setApplicationName(config.getApplicationName())
                .build();
    }

    private Object[][] formatTripData(TripExportRequest request) {
        List<Object[]> rows = new ArrayList<>();

        // Extract contact info from notes
        String[] notesParts = request.getNotes() != null ? request.getNotes().split("\\|") : new String[0];
        String fullName = extractFromNotes(notesParts, "Full Name:") != null ?
                extractFromNotes(notesParts, "Full Name:") : "Customer";
        String phone = extractFromNotes(notesParts, "Phone:");
        String numberOfTravelers = extractFromNotes(notesParts, "Number of travelers:");
        String startDate = extractFromNotes(notesParts, "Start Date:");
        String endDate = extractFromNotes(notesParts, "End Date:");
        String specialRequirements = extractFromNotes(notesParts, "Special requirements:");

        // HEADER SECTION
        rows.add(new Object[]{"🌍 MYHIVE TRAVEL BOOKING"});
        rows.add(new Object[]{});
        rows.add(new Object[]{"📅 Booking Date:", java.time.LocalDate.now().toString()});
        rows.add(new Object[]{"🕐 Booking Time:", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))});
        rows.add(new Object[]{"📋 Status:", "🟡 Pending Confirmation"});
        rows.add(new Object[]{});

        // CUSTOMER INFORMATION
        rows.add(new Object[]{"👤 CUSTOMER INFORMATION"});
        rows.add(new Object[]{"", "", "", ""});
        rows.add(new Object[]{"Name:", fullName});
        rows.add(new Object[]{"Email:", request.getUserEmail()});
        rows.add(new Object[]{"Phone:", phone != null ? phone : "Not provided"});
        rows.add(new Object[]{"Travelers:", numberOfTravelers != null ? numberOfTravelers : "1"});
        rows.add(new Object[]{"Special Requirements:", specialRequirements != null && !specialRequirements.equals("None") ? specialRequirements : "None"});
        rows.add(new Object[]{});

        // TRAVEL DATES
        rows.add(new Object[]{"📅 TRAVEL DATES"});
        rows.add(new Object[]{"", "", "", ""});
        rows.add(new Object[]{"Start Date:", startDate != null ? startDate : "To be confirmed"});
        rows.add(new Object[]{"End Date:", endDate != null ? endDate : "To be confirmed"});
        rows.add(new Object[]{"Duration:", calculateDuration(startDate, endDate)});
        rows.add(new Object[]{});

        // ITINERARY SUMMARY
        rows.add(new Object[]{"🗺️ ITINERARY SUMMARY"});
        rows.add(new Object[]{"", "", "", ""});
        rows.add(new Object[]{"Destination", "Country", "Activities", "Duration"});
        rows.add(new Object[]{"─────────", "───────", "──────────", "────────"});

        double totalPrice = 0.0;
        for (TripExportRequest.DestinationExport dest : request.getDestinations()) {
            int activityCount = dest.getActivities() != null ? dest.getActivities().size() : 0;
            rows.add(new Object[]{
                    dest.getDestinationName(),
                    dest.getCountry() != null ? dest.getCountry() : "TBD",
                    activityCount + " activities",
                    dest.getDuration() + " days"
            });
        }
        rows.add(new Object[]{});

        // DETAILED ITINERARY
        rows.add(new Object[]{"🎯 DETAILED ITINERARY"});
        rows.add(new Object[]{});

        for (TripExportRequest.DestinationExport dest : request.getDestinations()) {
            rows.add(new Object[]{"📍 " + dest.getDestinationName() + ", " + (dest.getCountry() != null ? dest.getCountry() : "TBD")});
            rows.add(new Object[]{});

            if (dest.getActivities() != null && !dest.getActivities().isEmpty()) {
                rows.add(new Object[]{"Activity", "Category", "Price", "Duration", "Time"});
                rows.add(new Object[]{"────────", "────────", "─────", "────────", "────"});

                for (TripExportRequest.ActivityExport activity : dest.getActivities()) {
                    double activityPrice = activity.getPrice() != null ? activity.getPrice() : 0.0;
                    totalPrice += activityPrice;

                    rows.add(new Object[]{
                            "• " + activity.getActivityName(),
                            activity.getCategory() != null ? activity.getCategory() : "General",
                            "€" + String.format("%.2f", activityPrice),
                            activity.getDuration() != null ? activity.getDuration() + "h" : "N/A",
                            activity.getTimeOfDay() != null ? activity.getTimeOfDay() : "Flexible"
                    });
                }
                rows.add(new Object[]{});
            }
        }

        // PRICING SUMMARY
        int travelers = numberOfTravelers != null ? Integer.parseInt(numberOfTravelers.replaceAll("[^0-9]", "")) : 1;
        rows.add(new Object[]{"💰 PRICING SUMMARY"});
        rows.add(new Object[]{"", "", ""});
        rows.add(new Object[]{"Price per person:", "€" + String.format("%.2f", totalPrice)});
        rows.add(new Object[]{"Number of travelers:", travelers});
        rows.add(new Object[]{"────────────────", "─────────"});
        rows.add(new Object[]{"🎯 TOTAL PRICE:", "€" + String.format("%.2f", totalPrice * travelers)});
        rows.add(new Object[]{});

        // NEXT STEPS
        rows.add(new Object[]{"📋 NEXT STEPS"});
        rows.add(new Object[]{"", "", ""});
        rows.add(new Object[]{"□ Review customer requirements"});
        rows.add(new Object[]{"□ Check availability for all activities"});
        rows.add(new Object[]{"□ Prepare personalized itinerary"});
        rows.add(new Object[]{"□ Contact customer for confirmation"});
        rows.add(new Object[]{"□ Finalize booking and payment"});
        rows.add(new Object[]{});

        // NOTES
        rows.add(new Object[]{"📝 INTERNAL NOTES"});
        rows.add(new Object[]{"", "", ""});
        rows.add(new Object[]{"", "Add notes here during review process"});
        rows.add(new Object[]{"", ""});

        return rows.toArray(new Object[0][]);
    }

    private String calculateDuration(String startDate, String endDate) {
        if (startDate == null || endDate == null) return "To be confirmed";
        try {
            java.time.LocalDate start = java.time.LocalDate.parse(startDate);
            java.time.LocalDate end = java.time.LocalDate.parse(endDate);
            long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
            return days + " days";
        } catch (Exception e) {
            return "To be confirmed";
        }
    }

    private String extractFromNotes(String[] notesParts, String prefix) {
        for (String part : notesParts) {
            if (part.contains(prefix)) {
                return part.replace(prefix, "").trim();
            }
        }
        return null;
    }

    private String writeDataToSheet(Sheets sheetsService, SheetData sheetData) throws IOException, GeneralSecurityException {
        // First, ensure the sheet exists
        ensureSheetExists(sheetsService, sheetData.getSpreadsheetId(), sheetData.getSheetName());

        List<List<Object>> values = new ArrayList<>();
        for (Object[] row : sheetData.getValues()) {
            values.add(Arrays.asList(row));
        }

        ValueRange body = new ValueRange().setValues(values);

        // Debug logging
        log.info("Attempting to write to sheet. Spreadsheet ID: {}, Range: {}, Data rows: {}",
                sheetData.getSpreadsheetId(), sheetData.getRange(), values.size());

        UpdateValuesResponse result = sheetsService.spreadsheets().values()
                .update(sheetData.getSpreadsheetId(), sheetData.getRange(), body)
                .setValueInputOption("USER_ENTERED")
                .execute();

        log.info("Successfully wrote {} cells to sheet", result.getUpdatedCells());
        return sheetData.getSpreadsheetId();
    }

    private void ensureSheetExists(Sheets sheetsService, String spreadsheetId, String sheetName) throws IOException, GeneralSecurityException {
        try {
            // Check if sheet exists
            sheetsService.spreadsheets()
                    .get(spreadsheetId)
                    .execute();

            // Try to get the sheet to see if it exists
            try {
                sheetsService.spreadsheets()
                        .values()
                        .get(spreadsheetId, sheetName + "!A1")
                        .execute();
            } catch (Exception e) {
                // Sheet doesn't exist, create it
                log.info("Sheet '{}' does not exist, creating it", sheetName);

                // Create new sheet using AddSheetRequest
                BatchUpdateSpreadsheetRequest batchUpdate = new BatchUpdateSpreadsheetRequest()
                        .setRequests(Arrays.asList(
                                new Request()
                                        .setAddSheet(new AddSheetRequest()
                                                .setProperties(new SheetProperties()
                                                        .setTitle(sheetName)))
                        ));

                sheetsService.spreadsheets()
                        .batchUpdate(spreadsheetId, batchUpdate)
                        .execute();

                log.info("Created new sheet: {}", sheetName);
            }
        } catch (Exception e) {
            log.error("Error ensuring sheet exists: {}", e.getMessage());
            throw e;
        }
    }

    private String generateSheetName(String tripName) {
        try {
            // Get existing sheets to find the next available number
            Sheets sheetsService = getSheetsService();
            String spreadsheetId = config.getEffectiveSpreadsheetId();
            Spreadsheet spreadsheet = sheetsService.spreadsheets()
                    .get(spreadsheetId)
                    .execute();

            int maxNumber = 0;
            for (Sheet sheet : spreadsheet.getSheets()) {
                String sheetName = sheet.getProperties().getTitle();
                if (sheetName.startsWith("Sheet")) {
                    try {
                        String numberPart = sheetName.substring("Sheet".length());
                        int number = Integer.parseInt(numberPart);
                        maxNumber = Math.max(maxNumber, number);
                    } catch (NumberFormatException e) {
                        // Ignore non-numeric sheet names
                    }
                }
            }

            return "Sheet" + (maxNumber + 1);
        } catch (Exception e) {
            // Fallback to timestamp if we can't read existing sheets
            return "Sheet" + System.currentTimeMillis() % 10000;
        }
    }

    private String extractCustomerName(TripExportRequest request) {
        // Extract customer name from notes or use a default
        if (request.getNotes() != null) {
            String[] notesParts = request.getNotes().split("\\|");
            for (String part : notesParts) {
                if (part.contains("Full Name:")) {
                    return part.replace("Full Name:", "").trim();
                }
            }
        }

        // Fallback to trip name or generic name
        return request.getTripName() != null ? request.getTripName() : "Valued Customer";
    }

    private void updateMasterSummarySheet(Sheets sheetsService, String spreadsheetId, TripExportRequest request, String sheetName) throws IOException {
        try {
            // Check if master summary sheet exists, if not create it
            String masterSheetName = "BOOKING_SUMMARY";

            // Prepare summary data for this booking
            String[] notesParts = request.getNotes() != null ? request.getNotes().split("\\|") : new String[0];
            String fullName = extractFromNotes(notesParts, "Full Name:") != null ?
                    extractFromNotes(notesParts, "Full Name:") : "Customer";
            String numberOfTravelers = extractFromNotes(notesParts, "Number of travelers:");
            String startDate = extractFromNotes(notesParts, "Start Date:");

            double totalPrice = 0.0;
            for (TripExportRequest.DestinationExport dest : request.getDestinations()) {
                if (dest.getActivities() != null) {
                    for (TripExportRequest.ActivityExport activity : dest.getActivities()) {
                        totalPrice += activity.getPrice() != null ? activity.getPrice() : 0.0;
                    }
                }
            }

            int travelers = numberOfTravelers != null ? Integer.parseInt(numberOfTravelers.replaceAll("[^0-9]", "")) : 1;
            double totalCost = totalPrice * travelers;

            // Create summary row for this booking
            Object[] summaryRow = new Object[]{
                    java.time.LocalDate.now().toString(),
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                    fullName,
                    request.getUserEmail(),
                    extractFromNotes(notesParts, "Phone:"),
                    travelers,
                    startDate,
                    calculateDuration(startDate, extractFromNotes(notesParts, "End Date:")),
                    "€" + String.format("%.2f", totalCost),
                    "🟡 Pending",
                    sheetName,
                    "=HYPERLINK(\"https://docs.google.com/spreadsheets/d/" + spreadsheetId + "/gui#gid=0\", \"View Details\")"
            };

            // Try to append to existing summary sheet or create new one
            try {
                // Check if summary sheet exists by trying to read it
                ValueRange existingData = sheetsService.spreadsheets().values()
                        .get(spreadsheetId, masterSheetName + "!A1:M1")
                        .execute();

                // Sheet exists, append new row
                List<List<Object>> appendValues = new ArrayList<>();
                appendValues.add(Arrays.asList(summaryRow));
                ValueRange appendData = new ValueRange().setValues(appendValues);
                sheetsService.spreadsheets().values()
                        .append(spreadsheetId, masterSheetName + "!A:M", appendData)
                        .setValueInputOption("USER_ENTERED")
                        .execute();

            } catch (Exception e) {
                // Sheet doesn't exist, create it with headers
                Object[][] headerData = {
                        {"📋 BOOKING SUMMARY", "", "", "", "", "", "", "", "", "", "", ""},
                        {"Date", "Time", "Customer", "Email", "Phone", "Travelers", "Start Date", "Duration", "Total Price", "Status", "Sheet Name", "Link"},
                        {"─────", "────", "────────", "─────", "─────", "─────────", "──────────", "────────", "───────────", "──────", "──────────", "────"},
                        summaryRow
                };

                ValueRange headerRange = new ValueRange().setValues(
                        Arrays.stream(headerData).map(Arrays::asList).collect(java.util.stream.Collectors.toList())
                );

                sheetsService.spreadsheets().values()
                        .update(spreadsheetId, masterSheetName + "!A1", headerRange)
                        .setValueInputOption("USER_ENTERED")
                        .execute();
            }

            log.info("Updated master summary sheet with booking for: {}", fullName);

        } catch (Exception e) {
            log.error("Failed to update master summary sheet", e);
            // Don't throw exception - the main booking export was successful
        }
    }

    private String generateSheetUrl(String spreadsheetId) {
        return "https://docs.google.com/spreadsheets/d/" + spreadsheetId;
    }
}
