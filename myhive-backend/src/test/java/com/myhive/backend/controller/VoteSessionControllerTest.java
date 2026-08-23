package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.dto.VoteTallyResponse;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        UUID expectedManagerToken = UUID.randomUUID();
        VoteSessionResponse response = new VoteSessionResponse(
                expectedToken, "Bali", "bali", "ACTIVE",
                java.time.Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS), 0L, 2,
                expectedManagerToken, "QUIZ");

        when(voteSessionService.createSession(any())).thenReturn(response);

        String requestJson = """
                {
                    "destinationId": "%s",
                    "initiatorEmail": "alice@example.com",
                    "numberOfTravelers": 2,
                    "startDate": "2026-07-01",
                    "endDate": "2026-07-07",
                    "voterToken": "%s",
                    "activityIds": ["%s"]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/vote/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shareToken").value(expectedToken.toString()));
    }

    @Test
    void createCartSession_returns201WithManagerToken() throws Exception {
        UUID expectedToken = UUID.randomUUID();
        UUID expectedManagerToken = UUID.randomUUID();
        VoteSessionResponse response = new VoteSessionResponse(
                expectedToken, "Prague", "prague", "ACTIVE",
                java.time.Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS), 0L, 4,
                expectedManagerToken, "CART");

        when(voteSessionService.createCartSession(any())).thenReturn(response);

        String requestJson = """
                {
                    "destinationId": "%s",
                    "initiatorEmail": "alice@example.com",
                    "numberOfTravelers": 4,
                    "startDate": "2026-08-01",
                    "endDate": "2026-08-03",
                    "activityIds": ["%s"]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/vote/sessions/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.managerToken").value(expectedManagerToken.toString()))
                .andExpect(jsonPath("$.voteMode").value("CART"));
    }

    @Test
    void getResult_returns404WhenActive() throws Exception {
        UUID shareToken = UUID.randomUUID();
        when(voteSessionService.getResult(eq(shareToken), isNull()))
                .thenThrow(new ResourceNotFoundException("Result not available yet"));

        mockMvc.perform(get("/vote/sessions/{shareToken}/result", shareToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void castVote_returns409WhenSessionFull() throws Exception {
        UUID shareToken = UUID.randomUUID();
        doThrow(new SessionFullException("Session is full"))
                .when(voteSessionService).castVote(any(), any());

        mockMvc.perform(post("/vote/sessions/{shareToken}/votes", shareToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voterToken\":\"" + UUID.randomUUID() + "\",\"activityId\":\"" + UUID.randomUUID() + "\",\"liked\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void getSession_returns200WithSessionInfo() throws Exception {
        UUID shareToken = UUID.randomUUID();
        VoteSessionResponse response = new VoteSessionResponse(
                shareToken, "Bali", "bali", "ACTIVE",
                java.time.Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS), 5L, 3,
                null, "QUIZ");

        when(voteSessionService.getSession(eq(shareToken), isNull())).thenReturn(response);

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

    @Test
    void closeSession_returns400WhenManagerTokenMissing() throws Exception {
        UUID shareToken = UUID.randomUUID();

        mockMvc.perform(post("/vote/sessions/{shareToken}/close", shareToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getParticipantQuiz_returns200WithQuestions() throws Exception {
        UUID shareToken = UUID.randomUUID();
        com.myhive.backend.dto.PublicQuizAnswerDTO answer = new com.myhive.backend.dto.PublicQuizAnswerDTO(
                UUID.randomUUID(), "Daytime");
        com.myhive.backend.dto.PublicQuizQuestionDTO question = new com.myhive.backend.dto.PublicQuizQuestionDTO(
                UUID.randomUUID(), "Daytime or night?", List.of(answer));
        com.myhive.backend.dto.PublicQuizDTO quiz = new com.myhive.backend.dto.PublicQuizDTO(List.of(question));
        when(voteSessionService.getParticipantQuiz(eq(shareToken), isNull())).thenReturn(quiz);

        mockMvc.perform(get("/vote/sessions/" + shareToken + "/quiz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].prompt", is("Daytime or night?")))
                .andExpect(jsonPath("$.questions[0].answers[0].weights").doesNotExist());
    }

    @Test
    void postParticipantQuiz_returns204_onSuccess() throws Exception {
        UUID shareToken = UUID.randomUUID();
        String body = """
                { "voterToken": "%s", "responses": [] }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/vote/sessions/" + shareToken + "/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    void postParticipantQuiz_propagatesConflict_returns409() throws Exception {
        UUID shareToken = UUID.randomUUID();
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Quiz already submitted"))
                .when(voteSessionService).submitParticipantQuiz(any(), any());

        String body = """
                { "voterToken": "%s", "responses": [] }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/vote/sessions/" + shareToken + "/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void getTally_returns200WithRows() throws Exception {
        UUID shareToken = UUID.randomUUID();
        VoteTallyResponse tally = new VoteTallyResponse("ACTIVE",
                java.time.Instant.now().plus(12, java.time.temporal.ChronoUnit.HOURS), 3L,
                List.of(new VoteTallyResponse.TallyRow(
                        UUID.randomUUID(), "Bar Crawl", new java.math.BigDecimal("45.00"), 2L)));

        when(voteSessionService.getTally(any(), any(), any(), any())).thenReturn(tally);

        mockMvc.perform(get("/vote/sessions/{shareToken}/tally", shareToken)
                        .param("voterToken", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(3))
                .andExpect(jsonPath("$.rows[0].name").value("Bar Crawl"))
                .andExpect(jsonPath("$.rows[0].likeCount").value(2));
    }
}
