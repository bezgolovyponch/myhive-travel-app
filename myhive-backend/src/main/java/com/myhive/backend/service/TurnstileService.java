package com.myhive.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@Slf4j
public class TurnstileService {

    private final RestClient restClient;
    private final String secretKey;

    public TurnstileService(
            @Value("${turnstile.secret-key}") String secretKey,
            @Value("${turnstile.verify-url}") String verifyUrl) {
        this.secretKey = secretKey;
        this.restClient = RestClient.builder()
                .baseUrl(verifyUrl)
                .build();
    }

    public boolean verifyToken(String token) {
        try {
            Map<String, String> body = Map.of(
                    "secret", secretKey,
                    "response", token
            );
            Map<String, Object> response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            boolean success = Boolean.TRUE.equals(response.get("success"));
            if (!success) {
                log.warn("Turnstile verification failed: {}", response);
            }
            return success;
        } catch (Exception e) {
            log.error("Turnstile verification error: {}", e.getMessage(), e);
            return false;
        }
    }
}
