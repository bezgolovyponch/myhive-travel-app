package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.exception.SessionFullException;
import com.myhive.backend.service.VoteSessionService;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, VoteSessionControllerTest.MockConfig.class})
class VoteSessionControllerTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        public VoteSessionService voteSessionService() {
            return mock(VoteSessionService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VoteSessionService voteSessionService;

    @BeforeEach
    void setUp() {
        reset(voteSessionService);
    }

    @Test
    void createSession_returns201WithShareToken() throws Exception {
        UUID expectedToken = UUID.randomUUID();
        VoteSessionResponse response = new VoteSessionResponse(
                expectedToken, "Bali", "bali", "ACTIVE",
                java.time.Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS), 0L, 2);

        when(voteSessionService.createSession(any())).thenReturn(response);

        String requestJson = """
                {
                    "destinationId": "%s",
                    "initiatorEmail": "alice@example.com",
                    "numberOfTravelers": 2,
                    "startDate": "2026-07-01",
                    "endDate": "2026-07-07",
                    "likedCategoryIds": ["%s"]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/vote/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shareToken").value(expectedToken.toString()));
    }

    @Test
    void getResult_returns404WhenActive() throws Exception {
        UUID shareToken = UUID.randomUUID();
        when(voteSessionService.getResult(shareToken))
                .thenThrow(new ResourceNotFoundException("Result not available yet"));

        mockMvc.perform(get("/vote/sessions/{shareToken}/result", shareToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void castVote_returns403WhenSessionFull() throws Exception {
        UUID shareToken = UUID.randomUUID();
        doThrow(new SessionFullException("Session is full"))
                .when(voteSessionService).castVote(any(), any());

        mockMvc.perform(post("/vote/sessions/{shareToken}/votes", shareToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voterToken\":\"" + UUID.randomUUID() + "\",\"activityId\":\"" + UUID.randomUUID() + "\",\"liked\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSession_returns200WithSessionInfo() throws Exception {
        UUID shareToken = UUID.randomUUID();
        VoteSessionResponse response = new VoteSessionResponse(
                shareToken, "Bali", "bali", "ACTIVE",
                java.time.Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS), 5L, 3);

        when(voteSessionService.getSession(shareToken)).thenReturn(response);

        mockMvc.perform(get("/vote/sessions/{shareToken}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.participantCount").value(5));
    }

    @Test
    void getParticipantCount_returns200WithCount() throws Exception {
        UUID shareToken = UUID.randomUUID();
        when(voteSessionService.getParticipantCount(shareToken)).thenReturn(3L);

        mockMvc.perform(get("/vote/sessions/{shareToken}/participant-count", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }
}
