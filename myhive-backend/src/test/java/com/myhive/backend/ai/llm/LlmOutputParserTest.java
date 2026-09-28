package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.PlanDraft;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmOutputParserTest {

    private final LlmOutputParser parser = new LlmOutputParser();

    private static String fixture(String name) throws IOException {
        try (var in = LlmOutputParserTest.class.getResourceAsStream("/ai/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parseChatTurn_readsBriefAndIgnoresUnknownFields() throws IOException {
        ChatTurnResult result = parser.parseChatTurn(fixture("chat-turn-valid.json"));

        assertThat(result.reply()).startsWith("8 lads");
        assertThat(result.briefUpdate().days()).isEqualTo(3);
        assertThat(result.briefUpdate().arrival()).isEqualTo(DayEdge.EVENING);
        assertThat(result.missingFields()).containsExactly("preferences");
    }

    @Test
    void parseChatTurn_toleratesMarkdownFenceAroundJson() {
        String expectedReply = "hi";
        String fenced = "```json\n{\"reply\":\"" + expectedReply + "\",\"brief\":{},\"missingFields\":[]}\n```";

        assertThat(parser.parseChatTurn(fenced).reply()).isEqualTo(expectedReply);
    }

    @Test
    void parseChatTurn_rejectsMissingReply() {
        assertThatThrownBy(() -> parser.parseChatTurn("{\"brief\":{}}"))
                .isInstanceOf(LlmOutputException.class).hasMessageContaining("reply");
    }

    /**
     * A hallucinated enum value ({@code budget: "MEDIUM"}, {@code arrival: "NIGHT"}) is one wrong field,
     * not a wrong turn: it reads as null so BriefMerger keeps the value the brief already had, and the
     * reply still reaches the user. Failing the parse instead spent a message and answered nothing.
     */
    @Test
    void parseChatTurn_readsAnUnknownEnumValueAsNull() {
        String expectedReply = "x";
        int expectedDays = 3;

        ChatTurnResult result = parser.parseChatTurn(
                "{\"reply\":\"" + expectedReply + "\",\"brief\":{\"days\":3,\"budget\":\"HUGE\",\"arrival\":\"NIGHT\"}}");

        assertThat(result.reply()).isEqualTo(expectedReply);
        assertThat(result.briefUpdate().days()).isEqualTo(expectedDays);
        assertThat(result.briefUpdate().budget()).isNull();
        assertThat(result.briefUpdate().arrival()).isNull();
    }

    @Test
    void parsePlan_readsTiersSlotsAndIds() throws IOException {
        UUID expectedActivityId = UUID.randomUUID();
        UUID otherActivityId = UUID.randomUUID();
        String json = fixture("plan-valid.json").replace("ID1", expectedActivityId.toString()).replace("ID2", otherActivityId.toString());

        PlanDraft draft = parser.parsePlan(json);

        assertThat(draft.packages()).extracting(PlanDraft.PackageDraft::key).containsExactly(Tier.BASIC, Tier.MEDIUM, Tier.PREMIUM);
        PlanDraft.ItemDraft item = draft.packages().get(2).days().get(0).items().get(0);
        assertThat(item.slot()).isEqualTo(Slot.AFTERNOON);
        assertThat(item.activityId()).isEqualTo(expectedActivityId);
    }

    @Test
    void parsePlan_rejectsNonUuidActivityIdAndNonJson() {
        assertThatThrownBy(() -> parser.parsePlan("{\"packages\":[{\"key\":\"BASIC\",\"days\":[{\"dayNumber\":1,\"items\":[{\"slot\":\"EVENING\",\"activityId\":\"beer-bike\"}]}]}]}"))
                .isInstanceOf(LlmOutputException.class).hasMessageContaining("activityId");
        assertThatThrownBy(() -> parser.parsePlan("Sure! Here is your plan:"))
                .isInstanceOf(LlmOutputException.class);
    }
}
