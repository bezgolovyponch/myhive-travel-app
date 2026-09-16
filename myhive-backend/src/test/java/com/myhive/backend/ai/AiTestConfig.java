package com.myhive.backend.ai;

import com.myhive.backend.ai.llm.FakeLlmGateway;
import com.myhive.backend.ai.llm.LlmGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Imported by any Spring test that must not reach the real model. */
@TestConfiguration
public class AiTestConfig {

    /**
     * Registered under the production gateway's own bean name so it <em>replaces</em> that definition
     * (the test classpath enables {@code spring.main.allow-bean-definition-overriding}, as it does for
     * {@code TestSecurityConfig}'s JwtDecoder). Adding a second bean would not do: SpringAiLlmGateway
     * is itself {@code @Primary}, so two primaries would leave every injection point ambiguous.
     */
    @Bean(name = "springAiLlmGateway")
    @Primary
    public LlmGateway fakeLlmGateway() {
        return new FakeLlmGateway();
    }
}
