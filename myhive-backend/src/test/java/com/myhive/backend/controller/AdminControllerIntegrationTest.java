package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static com.myhive.backend.util.JwtTestHelper.adminJwt;
import static com.myhive.backend.util.JwtTestHelper.managerJwt;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestSecurityConfig.class)
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DestinationRepository destinationRepository;

    @Autowired
    private ActivityRepository activityRepository;

    private UUID destinationId;
    private UUID activityId;

    @BeforeEach
    void setUp() {
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
                        .with(adminJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void getAllBookings_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getBookingStats_withAdminAuth_returnsStats() throws Exception {
        mockMvc.perform(get("/admin/bookings/stats")
                        .with(adminJwt()))
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
                        .with(adminJwt())
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
                        .with(adminJwt())
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
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Tour")));
    }

    @Test
    void deleteActivity_withAdminAuth_returns204() throws Exception {
        mockMvc.perform(delete("/admin/activities/" + activityId)
                        .with(adminJwt()))
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
                        .with(adminJwt())
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
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createActivity_withManagerAuth_returns201() throws Exception {
        String json = """
                {
                  "destinationId": "%s",
                  "name": "Seine River Cruise",
                  "description": "Scenic boat ride",
                  "price": 20.00
                }
                """.formatted(destinationId);

        mockMvc.perform(post("/admin/activities")
                        .with(managerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Seine River Cruise")));
    }

    @Test
    void getActivities_withManagerAuth_returns200() throws Exception {
        mockMvc.perform(get("/admin/activities")
                        .with(managerJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void getBookings_withManagerAuth_returns403() throws Exception {
        mockMvc.perform(get("/admin/bookings")
                        .with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBlog_withManagerAuth_returns200() throws Exception {
        mockMvc.perform(get("/admin/blog")
                        .with(managerJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void deleteActivity_nonexistent_returns404() throws Exception {
        mockMvc.perform(delete("/admin/activities/" + UUID.randomUUID())
                        .with(adminJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCreatePackage_withAdminAuth_returns201() throws Exception {
        String body = String.format("""
                {"destinationId":"%s","name":"New Pkg","discountPct":15.00,
                 "activities":[{"activityId":"%s","position":0}]}""",
                destinationId, activityId);

        mockMvc.perform(post("/admin/packages")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("new-pkg"));
    }

    @Test
    void adminListPackagesPaged_withAdminAuth_returns200() throws Exception {
        mockMvc.perform(get("/admin/packages/paged")
                        .with(adminJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void getActivitiesPaged_withoutDestinationFilter_returnsAll() throws Exception {
        Destination other = new Destination();
        other.setName("Madrid");
        other.setCountry("Spain");
        other = destinationRepository.save(other);

        Activity madridActivity = new Activity();
        madridActivity.setDestination(other);
        madridActivity.setName("Prado Museum");
        madridActivity.setPrice(new BigDecimal("12.00"));
        activityRepository.save(madridActivity);

        mockMvc.perform(get("/admin/activities/paged")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    void getActivitiesPaged_withDestinationFilter_returnsOnlyMatchingActivities() throws Exception {
        Destination other = new Destination();
        other.setName("Madrid");
        other.setCountry("Spain");
        other = destinationRepository.save(other);

        Activity madridActivity = new Activity();
        madridActivity.setDestination(other);
        madridActivity.setName("Prado Museum");
        madridActivity.setPrice(new BigDecimal("12.00"));
        activityRepository.save(madridActivity);

        String expectedName = "Eiffel Tour";

        mockMvc.perform(get("/admin/activities/paged")
                        .with(adminJwt())
                        .param("destinationId", destinationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].name", is(expectedName)));
    }
}
