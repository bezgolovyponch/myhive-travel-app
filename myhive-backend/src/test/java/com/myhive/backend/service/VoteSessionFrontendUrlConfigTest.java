package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the regression where vote emails built their links from the phantom
 * {@code app.site.url} property (never defined → always defaulted to the prod
 * frontend) instead of the configured {@code app.frontend.url} / FRONTEND_URL.
 * Links must follow the per-environment frontend URL so a locally created
 * session yields a localhost link, not a trivlu.com one.
 */
@SpringBootTest
@Import(TestSecurityConfig.class)
@TestPropertySource(properties = "app.frontend.url=http://localhost:7777")
class VoteSessionFrontendUrlConfigTest {

    @Autowired
    private VoteSessionService voteSessionService;

    @Test
    void emailLinkBaseUrlBindsToFrontendUrlProperty() {
        Object frontendUrl = ReflectionTestUtils.getField(voteSessionService, "frontendUrl");
        assertThat(frontendUrl).isEqualTo("http://localhost:7777");
    }
}
