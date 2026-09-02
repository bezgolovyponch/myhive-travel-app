package com.myhive.backend.config;

import com.myhive.backend.service.EmailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Replaces the real mailer in Spring tests that verify *which* emails a flow hands off.
 * Shared so those tests hit the same cached context; call {@code reset(emailService)}
 * in {@code @BeforeEach} because the mock is a context-wide singleton.
 */
@TestConfiguration
public class MockEmailServiceConfig {

    @Bean
    @Primary
    public EmailService emailService() {
        return mock(EmailService.class);
    }
}
