package com.myhive.backend.controller;

import com.myhive.backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void publicEndpoints_accessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/destinations")).andExpect(status().isOk());
        mockMvc.perform(get("/activities")).andExpect(status().isOk());
        mockMvc.perform(get("/blog")).andExpect(status().isOk());
        mockMvc.perform(get("/health/detailed")).andExpect(status().isOk());
    }

    @Test
    void adminEndpoints_withoutAuth_returnsForbidden() throws Exception {
        mockMvc.perform(get("/admin/bookings"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_withValidAdminJwt_returns200() throws Exception {
        String token = jwtUtil.generateToken("admin@test.com", "ADMIN");

        mockMvc.perform(get("/admin/bookings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpoints_withNonAdminRole_returns403() throws Exception {
        String token = jwtUtil.generateToken("user@test.com", "USER");

        mockMvc.perform(get("/admin/bookings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookingStatusPatch_withoutAuth_returnsForbidden() throws Exception {
        UUID fakeId = UUID.randomUUID();

        mockMvc.perform(patch("/bookings/" + fakeId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"PAID\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookingStatusPatch_withAdminAuth_passesSecurityCheck() throws Exception {
        String token = jwtUtil.generateToken("admin@test.com", "ADMIN");
        UUID fakeId = UUID.randomUUID();

        // Will get 404 (booking not found) but NOT 401/403 — proves security passes
        mockMvc.perform(patch("/bookings/" + fakeId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\": \"PAID\"}"))
                .andExpect(status().isNotFound());
    }
}
