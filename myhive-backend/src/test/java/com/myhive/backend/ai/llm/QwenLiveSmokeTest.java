package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Talks to the real DashScope endpoint. Skipped unless QWEN_API_KEY is exported, so it never runs
 * in the normal suite; run it by hand after changing a prompt or bumping a model.
 */
@SpringBootTest(properties = {"spring.ai.openai.api-key=${QWEN_API_KEY}",
        "spring.ai.openai.base-url=${QWEN_BASE_URL:https://dashscope-intl.aliyuncs.com/compatible-mode/v1}"})
@EnabledIfEnvironmentVariable(named = "QWEN_API_KEY", matches = ".+")
class QwenLiveSmokeTest {

    @Autowired
    private SpringAiLlmGateway gateway;

    @Test
    void chatTurnAndComposeReturnParseableJson() {
        int expectedGroupSize = 8;
        int expectedPackageCount = 3;

        ChatTurnResult turn = gateway.chatTurn(new ChatTurnRequest("en", "Prague", List.of("nightlife", "driving"),
                Brief.empty(), List.of(new ChatMessage(ChatMessage.USER,
                        expectedGroupSize + " of us for 2 days, we love karting", "t"))));
        assertThat(turn.reply()).isNotBlank();
        assertThat(turn.briefUpdate().groupSize()).isEqualTo(expectedGroupSize);

        List<CatalogActivity> catalog = List.of(
                new CatalogActivity(UUID.randomUUID(), "karting", "Karting", "Indoor track", 90, true,
                        new BigDecimal("45"), null, null, List.of("driving")),
                new CatalogActivity(UUID.randomUUID(), "beer-bike", "Beer Bike", "Pedal and drink", 120, true,
                        new BigDecimal("35"), new BigDecimal("280"), null, List.of("nightlife")),
                new CatalogActivity(UUID.randomUUID(), "club", "VIP Club Night", "Table + bottles", 240, true,
                        new BigDecimal("80"), null, null, List.of("nightlife")),
                new CatalogActivity(UUID.randomUUID(), "shooting", "Shooting Range", "Real guns", 120, true,
                        new BigDecimal("70"), null, null, List.of("action")));
        Brief brief = new Brief(2, expectedGroupSize, List.of("driving", "nightlife"), "loud", null, null,
                DayEdge.AFTERNOON, DayEdge.AFTERNOON, null);

        PlanDraftResult plan = gateway.composePlan(new PlanRequest("en", "Prague", brief, catalog, List.of()));

        assertThat(plan.draft().packages()).hasSize(expectedPackageCount);
    }
}
