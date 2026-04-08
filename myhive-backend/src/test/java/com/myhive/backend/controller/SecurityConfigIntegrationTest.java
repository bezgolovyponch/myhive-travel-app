package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
                        .with(jwt().jwt(j -> j
                                .subject("admin@test.com")
                                .claim("email", "admin@test.com")
                                .claim("https://trivlu.com/roles", List.of("ADMIN"))
                        ).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpoints_withNonAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/admin/bookings")
                        .with(jwt().jwt(j -> j
                                .subject("user@test.com")
                                .claim("email", "user@test.com")
                                .claim("https://trivlu.com/roles", List.of("USER"))
                        ).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookingStatusPatch_withoutAuth_returnsUnauthorized() throws Exception {
        UUID fakeId = UUID.randomUUID();

        mockMvc.perform(patch("/bookings/" + fakeId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"PAID\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bookingStatusPatch_withAdminAuth_passesSecurityCheck() throws Exception {
        UUID fakeId = UUID.randomUUID();

        // Will get 404 (booking not found) but NOT 401/403 — proves security passes
        mockMvc.perform(patch("/bookings/" + fakeId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(jwt().jwt(j -> j
                                .subject("admin@test.com")
                                .claim("email", "admin@test.com")
                                .claim("https://trivlu.com/roles", List.of("ADMIN"))
                        ).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .content("{\"status\": \"PAID\"}"))
                .andExpect(status().isNotFound());
    }
}
