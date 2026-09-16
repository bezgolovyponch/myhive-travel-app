package com.myhive.backend.ai;

import com.myhive.backend.ai.llm.FakeLlmGateway;
import com.myhive.backend.ai.llm.LlmGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Imported by any Spring test that must not reach the real model. */
@TestConfiguration
public class AiTestConfig {

    @Bean
    @Primary
    public LlmGateway fakeLlmGateway() {
        return new FakeLlmGateway();
    }
}
