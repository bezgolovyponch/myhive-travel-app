package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.ContactRequest;
import com.myhive.backend.service.EmailService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, ContactControllerTest.MockEmailConfig.class})
class ContactControllerTest {

    @TestConfiguration
    static class MockEmailConfig {
        @Bean
        @Primary
        public EmailService emailService() {
            return mock(EmailService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        reset(emailService);
    }

    @Test
    void submitContactForm_validRequest_returns200() throws Exception {
        mockMvc.perform(post("/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "John Doe",
                                    "email": "john@example.com",
                                    "subject": "Group Trip Inquiry",
                                    "message": "I'd like to plan a trip for 10 people."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Message sent successfully"));

        verify(emailService, times(1)).sendContactNotification(any(ContactRequest.class));
    }

    @Test
    void submitContactForm_missingName_returns400() throws Exception {
        mockMvc.perform(post("/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "",
                                    "email": "john@example.com",
                                    "subject": "Support",
                                    "message": "Help me"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(emailService, never()).sendContactNotification(any());
    }

    @Test
    void submitContactForm_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "John",
                                    "email": "not-an-email",
                                    "subject": "Support",
                                    "message": "Help me"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(emailService, never()).sendContactNotification(any());
    }

    @Test
    void submitContactForm_missingMessage_returns400() throws Exception {
        mockMvc.perform(post("/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "John",
                                    "email": "john@example.com",
                                    "subject": "Support",
                                    "message": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(emailService, never()).sendContactNotification(any());
    }

    @Test
    void submitContactForm_emailServiceFails_returns500() throws Exception {
        doThrow(new RuntimeException("SMTP error"))
                .when(emailService).sendContactNotification(any());

        mockMvc.perform(post("/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "John",
                                    "email": "john@example.com",
                                    "subject": "Support",
                                    "message": "Help me"
                                }
                                """))
                .andExpect(status().isInternalServerError());
    }
}
