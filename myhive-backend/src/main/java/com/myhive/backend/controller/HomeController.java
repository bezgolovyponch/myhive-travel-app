package com.myhive.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HomeController {

    private final DataSource dataSource;

    @GetMapping("/")
    public Map<String, Object> home() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "Trivlu Travel Backend API");
        response.put("status", "running");
        response.put("version", "1.0.0");
        response.put("endpoints", Map.of(
                "health", "/actuator/health",
                "destinations", "/destinations",
                "activities", "/activities",
                "detailed-health", "/health/detailed"
        ));
        return response;
    }

    @GetMapping("/health/detailed")
    public Map<String, Object> detailedHealth() {
        Map<String, Object> health = new HashMap<>();

        boolean dbUp;
        try (Connection _ = dataSource.getConnection()) {
            dbUp = true;
        } catch (Exception e) {
            dbUp = false;
        }

        health.put("database", Map.of("status", dbUp ? "UP" : "DOWN"));
        health.put("overall", dbUp ? "UP" : "DOWN");

        return health;
    }
}
