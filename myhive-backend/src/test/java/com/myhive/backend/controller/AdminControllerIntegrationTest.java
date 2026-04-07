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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private DestinationRepository destinationRepository;

    @Autowired
    private ActivityRepository activityRepository;

    private String adminToken;
    private UUID destinationId;
    private UUID activityId;

    @BeforeEach
    void setUp() {
        adminToken = jwtUtil.generateToken("admin@test.com", "ADMIN");

        Destination dest = new Destination();
        dest.setName("Paris");
        dest.setCountry("France");
        dest = destinationRepository.save(dest);
        destinationId = dest.getId();

        Activity activity = new Activity();
        activity.setDestination(dest);
        activity.setName("Eiffel Tour");
        activity.setPrice(new BigDecimal("25.00"));
        activity = activityRepository.save(activity);
        activityId = activity.getId();
    }

    @Test
    void getAllBookings_withAdminAuth_returns200() throws Exception {
        mockMvc.perform(get("/admin/bookings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void getAllBookings_withoutAuth_returnsForbidden() throws Exception {
        mockMvc.perform(get("/admin/bookings"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBookingStats_withAdminAuth_returnsStats() throws Exception {
        mockMvc.perform(get("/admin/bookings/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", notNullValue()))
                .andExpect(jsonPath("$.pending", notNullValue()))
                .andExpect(jsonPath("$.confirmed", notNullValue()))
                .andExpect(jsonPath("$.paid", notNullValue()));
    }

    @Test
    void createActivity_withAdminAuth_returns201() throws Exception {
        String json = """
                {
                  "destinationId": "%s",
                  "name": "Louvre Museum",
                  "description": "Visit the famous museum",
                  "price": 15.00
                }
                """.formatted(destinationId);

        mockMvc.perform(post("/admin/activities")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Louvre Museum")));
    }

    @Test
    void createActivity_withInvalidData_returns400() throws Exception {
        String json = """
                {
                  "destinationId": "%s",
                  "description": "Missing name and price"
                }
                """.formatted(destinationId);

        mockMvc.perform(post("/admin/activities")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateActivity_withAdminAuth_returns200() throws Exception {
        String json = """
                {
                  "destinationId": "%s",
                  "name": "Updated Tour",
                  "price": 30.00
                }
                """.formatted(destinationId);

        mockMvc.perform(put("/admin/activities/" + activityId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Tour")));
    }

    @Test
    void deleteActivity_withAdminAuth_returns204() throws Exception {
        mockMvc.perform(delete("/admin/activities/" + activityId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void createBlogPost_withAdminAuth_returns201() throws Exception {
        String json = """
                {
                  "title": "Travel Tips",
                  "excerpt": "Best tips",
                  "content": "Full content here",
                  "category": "Tips"
                }
                """;

        mockMvc.perform(post("/admin/blog")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Travel Tips")));
    }

    @Test
    void createBlogPost_withInvalidData_returns400() throws Exception {
        String json = """
                {
                  "content": "Missing title"
                }
                """;

        mockMvc.perform(post("/admin/blog")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteActivity_nonexistent_returns404() throws Exception {
        mockMvc.perform(delete("/admin/activities/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
