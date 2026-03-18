package com.myhive.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HomeController {

    @Autowired
    private DataSource dataSource;

    @GetMapping("/")
    public Map<String, Object> home() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "MyHive Travel Backend API");
        response.put("status", "running");
        response.put("version", "1.0.0");
        response.put("endpoints", Map.of(
                "health", "/api/actuator/health",
                "destinations", "/api/destinations",
                "activities", "/api/activities",
                "detailed-health", "/api/health/detailed"
        ));
        return response;
    }

    @GetMapping("/health/detailed")
    public Map<String, Object> detailedHealth() {
        Map<String, Object> health = new HashMap<>();

        // Database health
        try (Connection connection = dataSource.getConnection()) {
            health.put("database", Map.of(
                    "status", "UP",
                    "url", connection.getMetaData().getURL()
            ));
        } catch (Exception e) {
            health.put("database", Map.of(
                    "status", "DOWN",
                    "error", e.getMessage()
            ));
        }

        // Overall status
        Map<String, String> dbStatus = (Map<String, String>) health.get("database");
        boolean allUp = dbStatus.get("status").equals("UP");
        health.put("overall", allUp ? "UP" : "DOWN");

        return health;
    }
}
