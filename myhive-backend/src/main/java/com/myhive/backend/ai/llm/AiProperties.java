package com.myhive.backend.ai.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Everything the AI planner is tuned with: the kill switch, the two Qwen models and their budgets. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private boolean enabled = false;
    private String chatModel = "qwen3.7-plus";
    private String plannerModel = "qwen3.8-max";
    private Duration chatTimeout = Duration.ofSeconds(20);
    private Duration plannerTimeout = Duration.ofSeconds(60);
    private boolean turnstileRequired = false;
    private int sessionTtlDays = 30;
    private int dailySessionsPerIp = 20;
}
