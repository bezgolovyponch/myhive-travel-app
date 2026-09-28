package com.myhive.backend.ai.controller;

import com.myhive.backend.ai.AiTestConfig;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The {@code app.ai.enabled} kill switch, over the wire. Every planner endpoint has to answer
 * 503 AI_DISABLED before it touches a session, a generation or the model — including the two that
 * are addressed by generation id and never load a session of their own.
 */
@SpringBootTest(properties = "app.ai.enabled=false")
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, AiTestConfig.class})
class AiPlannerDisabledTest {

    private static final String EXPECTED_ERROR = "AI_DISABLED";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void everyPlannerEndpoint_is503AiDisabled() throws Exception {
        UUID token = UUID.randomUUID();
        UUID generationId = UUID.randomUUID();

        expectDisabled(post("/ai/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"destinationSlug": "prague", "locale": "en"}
                        """));
        expectDisabled(get("/ai/sessions/" + token));
        expectDisabled(post("/ai/sessions/" + token + "/messages").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"content": "hello"}
                        """));
        expectDisabled(post("/ai/sessions/" + token + "/generations"));
        expectDisabled(get("/ai/generations/" + generationId));
        expectDisabled(post("/ai/generations/" + generationId + "/select").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"packageKey": "%s"}
                        """.formatted(Tier.BASIC)));
    }

    private void expectDisabled(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error", is(EXPECTED_ERROR)));
    }
}
