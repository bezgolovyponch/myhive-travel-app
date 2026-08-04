package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.TripLeadCreateResponse;
import com.myhive.backend.dto.TripLeadRestoreResponse;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.service.TripLeadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, TripLeadControllerTest.MockConfig.class})
class TripLeadControllerTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        public TripLeadService tripLeadService() {
            return mock(TripLeadService.class);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private TripLeadService tripLeadService;

    @BeforeEach
    void setUp() {
        reset(tripLeadService);
    }

    @Test
    void create_returns201WithTokens() throws Exception {
        UUID expectedId = UUID.randomUUID();
        UUID expectedToken = UUID.randomUUID();
        when(tripLeadService.create(any())).thenReturn(new TripLeadCreateResponse(expectedId, expectedToken));

        String requestJson = """
                {
                    "email": "lead@example.com",
                    "destinationId": "%s",
                    "numberOfTravelers": 6,
                    "startDate": "2026-09-01",
                    "endDate": "2026-09-03"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(expectedId.toString()))
                .andExpect(jsonPath("$.restoreToken").value(expectedToken.toString()));
    }

    @Test
    void create_rejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_rejectsBlankEmail() throws Exception {
        mockMvc.perform(post("/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sync_returns204() throws Exception {
        UUID leadId = UUID.randomUUID();
        String requestJson = """
                {
                    "restoreToken": "%s",
                    "numberOfTravelers": 4,
                    "items": [{"activityId": "%s", "sortOrder": 0}]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(patch("/leads/" + leadId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNoContent());

        verify(tripLeadService).sync(eq(leadId), any());
    }

    @Test
    void restore_returns404ForUnknownToken() throws Exception {
        UUID token = UUID.randomUUID();
        when(tripLeadService.restore(token)).thenThrow(new ResourceNotFoundException("Trip lead not found"));

        mockMvc.perform(get("/leads/restore/" + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void restore_returnsSnapshot() throws Exception {
        UUID token = UUID.randomUUID();
        TripLeadRestoreResponse response = new TripLeadRestoreResponse(
                UUID.randomUUID(), "lead@example.com", UUID.randomUUID(), "prague", "Prague",
                6, null, null, null, null, List.of());
        when(tripLeadService.restore(token)).thenReturn(response);

        mockMvc.perform(get("/leads/restore/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationSlug").value("prague"))
                .andExpect(jsonPath("$.email").value("lead@example.com"));
    }

    @Test
    void unsubscribe_returns204AndDelegates() throws Exception {
        UUID expectedToken = UUID.randomUUID();

        mockMvc.perform(post("/leads/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + expectedToken + "\"}"))
                .andExpect(status().isNoContent());

        verify(tripLeadService).unsubscribe(expectedToken);
    }

    @Test
    void unsubscribeOneClick_returns200AndDelegates() throws Exception {
        UUID expectedToken = UUID.randomUUID();

        mockMvc.perform(post("/leads/unsubscribe/one-click").param("token", expectedToken.toString()))
                .andExpect(status().isOk());

        verify(tripLeadService).unsubscribe(expectedToken);
    }
}
