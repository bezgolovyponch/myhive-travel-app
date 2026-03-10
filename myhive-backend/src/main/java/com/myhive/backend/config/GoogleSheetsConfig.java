package com.myhive.backend.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Configuration
@Getter
@Setter
@Slf4j
@ConfigurationProperties(prefix = "google.sheets")
public class GoogleSheetsConfig {

    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);

    private boolean enabled = false;
    private String credentialsPath;
    private String credentialsJson;
    private String spreadsheetId;
    private String applicationName = "MyHive Travel App";

    @Bean
    @Profile("prod")
    public Sheets sheetsService() throws Exception {
        if (!isConfigured()) {
            log.warn("Google Sheets is enabled in prod but missing credentials or spreadsheet ID. Bean will not be created.");
            return null;
        }

        log.info("Initializing Google Sheets service...");
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        GoogleCredentials credentials = createCredentials();

        log.info("Google Sheets service initialized successfully");
        return new Sheets.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                .setApplicationName(applicationName)
                .build();
    }

    public boolean isConfigured() {
        boolean hasCredentials = hasText(credentialsJson) || hasText(credentialsPath);
        return enabled && hasCredentials && hasText(spreadsheetId);
    }

    public GoogleCredentials createCredentials() throws IOException {
        if (hasText(credentialsJson)) {
            log.info("Using Google Sheets credentials from JSON property");
            return GoogleCredentials.fromStream(
                    new ByteArrayInputStream(credentialsJson.getBytes())
            ).createScoped(SCOPES);
        }

        if (hasText(credentialsPath)) {
            log.info("Using Google Sheets credentials from file: {}", credentialsPath);
            return GoogleCredentials.fromStream(
                    new FileInputStream(credentialsPath)
            ).createScoped(SCOPES);
        }

        throw new IllegalStateException(
                "No Google Sheets credentials configured. Set google.sheets.credentials-json or google.sheets.credentials-path");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
