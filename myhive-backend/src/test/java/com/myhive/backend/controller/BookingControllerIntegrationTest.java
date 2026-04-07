package com.myhive.backend.controller;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private DestinationRepository destinationRepository;

    @Autowired
    private ActivityRepository activityRepository;

    private UUID activityId;

    @BeforeEach
    void setUp() {
        Destination dest = new Destination();
        dest.setName("Bali");
        dest.setCountry("Indonesia");
        dest = destinationRepository.save(dest);

        Activity activity = new Activity();
        activity.setDestination(dest);
        activity.setName("Surfing");
        activity.setPrice(new BigDecimal("50.00"));
        activity = activityRepository.save(activity);
        activityId = activity.getId();
    }

    @Test
    void createBooking_validRequest_returns201() throws Exception {
        String json = """
                {
                  "userEmail": "user@test.com",
                  "activities": [{"activityId": "%s", "quantity": 2}]
                }
                """.formatted(activityId);

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.totalAmount").value(100.00))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void createBooking_missingEmail_returns400() throws Exception {
        String json = """
                {
                  "activities": [{"activityId": "%s", "quantity": 2}]
                }
                """.formatted(activityId);

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.userEmail", notNullValue()));
    }

    @Test
    void createBooking_emptyActivities_returns400() throws Exception {
        String json = """
                {
                  "userEmail": "user@test.com",
                  "activities": []
                }
                """;

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getBookingById_nonexistent_returns404() throws Exception {
        mockMvc.perform(get("/bookings/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBookingsByEmail_validEmail_returnsEmpty() throws Exception {
        mockMvc.perform(get("/bookings")
                        .param("email", "nobody@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void createBookingFromTrip_validRequest_returns201() throws Exception {
        String json = """
                {
                  "tripName": "Summer Trip",
                  "userEmail": "user@test.com",
                  "customerName": "Test User",
                  "destinations": [{
                    "destinationName": "Bali",
                    "country": "Indonesia",
                    "startDate": "2026-06-01",
                    "endDate": "2026-06-10",
                    "activities": [{
                      "activityId": "%s",
                      "activityName": "Surfing",
                      "price": 50.0,
                      "duration": 60
                    }]
                  }]
                }
                """.formatted(activityId);

        mockMvc.perform(post("/bookings/trip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName", is("Test User")))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }
}
