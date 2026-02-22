package com.myhive.backend.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Configuration
@Data
@Slf4j
@ConfigurationProperties(prefix = "google.sheets")
public class GoogleSheetsConfig {

    // File-based credentials (fallback)
    private String credentialsPath;
    private String spreadsheetId;
    private String applicationName = "MyHive Travel App";
    private boolean enabled = false;

    // Environment variable credentials (preferred)
    @Value("${GOOGLE_SHEETS_CREDENTIALS_JSON:}")
    private String credentialsJson;

    @Value("${GOOGLE_SHEETS_SPREADSHEET_ID:}")
    private String envSpreadsheetId;

    @Value("${GOOGLE_SHEETS_ENABLED:false}")
    private boolean envEnabled;

    @Bean
    public Sheets sheetsService() throws IOException, GeneralSecurityException {
        if (!isConfigured()) {
            throw new IllegalStateException("Google Sheets is not properly configured");
        }

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        GoogleCredentials credentials = createCredentials();

        return new Sheets.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                .setApplicationName(applicationName)
                .build();
    }

    public GoogleCredentials createCredentials() throws IOException {
        // Method 1: Environment variable (preferred)
        if (credentialsJson != null && !credentialsJson.trim().isEmpty()) {
            log.info("Using Google Sheets credentials from environment variable");
            byte[] credentialsBytes = credentialsJson.getBytes();
            return GoogleCredentials.fromStream(
                    new ByteArrayInputStream(credentialsBytes)
            ).createScoped(SCOPES);
        }

        // Method 2: File-based (fallback)
        if (credentialsPath != null && !credentialsPath.trim().isEmpty()) {
            log.info("Using Google Sheets credentials from file: {}", credentialsPath);
            return GoogleCredentials.fromStream(
                    new java.io.FileInputStream(credentialsPath)
            ).createScoped(SCOPES);
        }

        throw new IllegalStateException("No Google Sheets credentials configured. Set GOOGLE_SHEETS_CREDENTIALS_JSON or google.sheets.credentials-path");
    }

    public boolean isConfigured() {
        boolean hasCredentials = (credentialsJson != null && !credentialsJson.trim().isEmpty()) ||
                (credentialsPath != null && !credentialsPath.trim().isEmpty());

        String effectiveSpreadsheetId = envSpreadsheetId != null && !envSpreadsheetId.trim().isEmpty() ?
                envSpreadsheetId : spreadsheetId;

        boolean effectiveEnabled = envEnabled || enabled;

        return effectiveEnabled && hasCredentials &&
                effectiveSpreadsheetId != null && !effectiveSpreadsheetId.trim().isEmpty();
    }

    public String getEffectiveSpreadsheetId() {
        return envSpreadsheetId != null && !envSpreadsheetId.trim().isEmpty() ?
                envSpreadsheetId : spreadsheetId;
    }

    public boolean isEffectivelyEnabled() {
        return envEnabled || enabled;
    }

    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
}
