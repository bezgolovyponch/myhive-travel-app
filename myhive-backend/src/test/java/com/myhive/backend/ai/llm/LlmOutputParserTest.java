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
    void parseChatTurn_rejectsMissingReplyOrBadEnumInBrief() {
        assertThatThrownBy(() -> parser.parseChatTurn("{\"brief\":{}}"))
                .isInstanceOf(LlmOutputException.class).hasMessageContaining("reply");
        assertThatThrownBy(() -> parser.parseChatTurn("{\"reply\":\"x\",\"brief\":{\"budget\":\"HUGE\"}}"))
                .isInstanceOf(LlmOutputException.class).hasMessageContaining("brief");
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
