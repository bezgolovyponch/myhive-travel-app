package com.myhive.backend.ai.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link AiProperties}. The {@code ChatModel} bean itself comes from Spring AI's OpenAI
 * autoconfiguration (pointed at DashScope via {@code spring.ai.openai.base-url}), so there is
 * deliberately no client bean here.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiClientConfig {
}
