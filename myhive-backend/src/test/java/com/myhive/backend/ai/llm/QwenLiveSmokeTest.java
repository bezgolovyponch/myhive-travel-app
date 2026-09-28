package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * Package edits (V8) call the chat model a second time to rewrite the copy of what an edit touched.
     * No content assertions - a garbled or empty answer is a valid, best-effort outcome for
     * {@link com.myhive.backend.ai.edit.TextRefresher}; this only proves the live response still parses
     * into {@link TextRefreshResult}.
     */
    @Test
    void refreshTextsOnATinyTwoItemPackageReturnsParseableJson() {
        UUID kartingId = UUID.randomUUID();
        UUID beerBikeId = UUID.randomUUID();
        ComposedPlan.ItemResult kartingItem = new ComposedPlan.ItemResult(Slot.AFTERNOON, "14:00", kartingId,
                "karting", "Karting", null, 90, new BigDecimal("45"), null, new BigDecimal("45"), false,
                "Gets everyone's adrenaline going before the evening.");
        ComposedPlan.ItemResult beerBikeItem = new ComposedPlan.ItemResult(Slot.EVENING, "19:00", beerBikeId,
                "beer-bike", "Beer Bike", null, 120, new BigDecimal("35"), new BigDecimal("280"),
                new BigDecimal("280"), true, "Gets the group loose for the night ahead.");
        ComposedPlan.DayResult day = new ComposedPlan.DayResult(1, "Day 1", "Karting then a beer bike crawl.",
                List.of(kartingItem, beerBikeItem));
        ComposedPlan.PackageResult basicPackage = new ComposedPlan.PackageResult(Tier.BASIC, "Prague Basics",
                "Karting and beers", "A tight two-activity day for the whole crew.", new BigDecimal("80"),
                new BigDecimal("640"), "EUR", 210, List.of(kartingId, beerBikeId), List.of(day));

        TextRefreshRequest request = new TextRefreshRequest("en", "Prague", List.of(basicPackage),
                Map.of(Tier.BASIC, Set.of(beerBikeId)), Map.of(Tier.BASIC, Set.of(1)));

        TextRefreshResult result = gateway.refreshTexts(request);

        assertThat(result).isNotNull();
        assertThat(result.texts()).isNotNull();
    }
}
