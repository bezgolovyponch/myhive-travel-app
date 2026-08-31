package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.myhive.backend.util.JwtTestHelper.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicEndpoints_accessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("running"));
        mockMvc.perform(get("/destinations")).andExpect(status().isOk());
        mockMvc.perform(get("/activities")).andExpect(status().isOk());
        mockMvc.perform(get("/blog")).andExpect(status().isOk());
        mockMvc.perform(get("/health/detailed")).andExpect(status().isOk());
    }

    @Test
    void adminEndpoints_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoints_withValidAdminJwt_returns200() throws Exception {
        mockMvc.perform(get("/admin/bookings")
                        .with(adminJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpoints_withNonAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/admin/bookings")
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerRole_canAccessAdminActivities() throws Exception {
        mockMvc.perform(get("/admin/activities")
                        .with(managerJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void managerRole_canAccessAdminBookings() throws Exception {
        // Managers now have bookings access (to view a booking and create a payment link).
        mockMvc.perform(get("/admin/bookings")
                        .with(managerJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void managerRole_canAccessAdminBlog() throws Exception {
        mockMvc.perform(get("/admin/blog")
                        .with(managerJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void bookingStatusPatch_withoutAuth_returnsUnauthorized() throws Exception {
        UUID fakeId = UUID.randomUUID();

        mockMvc.perform(patch("/admin/bookings/" + fakeId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bookingStatusPatch_withManagerAuth_returnsForbidden() throws Exception {
        UUID fakeId = UUID.randomUUID();

        // The status matcher narrows /admin/bookings/*/status to ADMIN before the
        // ADMIN|MANAGER rule for /admin/bookings/**.
        mockMvc.perform(patch("/admin/bookings/" + fakeId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(managerJwt())
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookingStatusPatch_withAdminAuth_passesSecurityCheck() throws Exception {
        UUID fakeId = UUID.randomUUID();

        // Will get 404 (booking not found) but NOT 401/403 — proves security passes
        mockMvc.perform(patch("/admin/bookings/" + fakeId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(adminJwt())
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void votesExport_fallsUnderAdminOnlyCatchAll() throws Exception {
        // /admin/votes/export has no dedicated matcher, so it falls to the /admin/** ADMIN-only rule.
        mockMvc.perform(get("/admin/votes/export").with(managerJwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/votes/export").with(adminJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void firstTouchReport_fallsUnderAdminBookingsRule_allowsManager() throws Exception {
        // /admin/bookings/first-touch-report matches /admin/bookings/** (ADMIN or MANAGER),
        // not the PATCH-only /admin/bookings/*/status ADMIN-only rule.
        mockMvc.perform(get("/admin/bookings/first-touch-report"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/bookings/first-touch-report").with(managerJwt()))
                .andExpect(status().isOk());
    }
}
