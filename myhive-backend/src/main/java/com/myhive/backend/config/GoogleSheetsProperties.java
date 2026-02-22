package com.myhive.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "google.sheets")
public class GoogleSheetsProperties {

    private String credentialsPath;
    private String spreadsheetId;
    private String applicationName = "MyHive Travel App";
    private boolean enabled = false;

    public boolean isConfigured() {
        return enabled && credentialsPath != null && !credentialsPath.trim().isEmpty()
                && spreadsheetId != null && !spreadsheetId.trim().isEmpty();
    }
}
