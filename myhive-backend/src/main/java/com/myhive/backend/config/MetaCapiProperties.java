package com.myhive.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MetaCapiProperties {

    private final String pixelId;
    private final String accessToken;
    private final String testEventCode;
    private final String apiUrl;

    public MetaCapiProperties(
            @Value("${meta.capi.pixel-id:}") String pixelId,
            @Value("${meta.capi.access-token:}") String accessToken,
            @Value("${meta.capi.test-event-code:}") String testEventCode,
            @Value("${meta.capi.api-url:https://graph.facebook.com/v21.0}") String apiUrl) {
        this.pixelId = pixelId;
        this.accessToken = accessToken;
        this.testEventCode = testEventCode;
        this.apiUrl = apiUrl;
    }

    public String getPixelId() { return pixelId; }
    public String getAccessToken() { return accessToken; }
    public String getTestEventCode() { return testEventCode; }
    public String getApiUrl() { return apiUrl; }

    public boolean isConfigured() {
        return pixelId != null && !pixelId.isBlank()
                && accessToken != null && !accessToken.isBlank();
    }
}
