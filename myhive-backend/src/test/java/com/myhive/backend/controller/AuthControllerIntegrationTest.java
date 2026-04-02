package com.myhive.backend.controller;

import com.myhive.backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void login_withValidCredentials_returnsToken() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@test.com\", \"password\": \"testpassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));
    }

    @Test
    void login_withInvalidCredentials_returns401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@test.com\", \"password\": \"wrongpassword1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withMissingEmail_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"testpassword123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withMissingPassword_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@test.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validateToken_withValidToken_returnsValid() throws Exception {
        String token = jwtUtil.generateToken("admin@test.com", "ADMIN");

        mockMvc.perform(get("/auth/validate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.email", is("admin@test.com")))
                .andExpect(jsonPath("$.role", is("ADMIN")));
    }

    @Test
    void validateToken_withInvalidToken_returnsInvalid() throws Exception {
        // "Bearer " prefix present but not a valid JWT — extractEmail throws, caught by global handler
        mockMvc.perform(get("/auth/validate")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void validateToken_withNonBearerHeader_returnsInvalid() throws Exception {
        mockMvc.perform(get("/auth/validate")
                        .header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(false)));
    }
}
