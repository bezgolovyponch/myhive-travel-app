package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.edit.EditRequest;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.PlanDraft;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

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
    void parseChatTurn_readsEditOperations_andDropsInvalidOnes() throws IOException {
        String expectedReply = "Swapping it now.";
        String expectedRemovedActivity = "Karting";
        String expectedReplacementActivity = "Beer Bike";
        String expectedAddedActivity = "Shooting Range";
        Tier expectedPackageKey = Tier.PREMIUM;
        int expectedDayNumber = 2;
        Slot expectedSlot = Slot.AFTERNOON;

        ChatTurnResult result = parser.parseChatTurn(fixture("chat-turn-with-edits.json"));

        assertThat(result.reply()).isEqualTo(expectedReply);
        assertThat(result.edits()).hasSize(2);
        EditRequest replace = result.edits().get(0);
        assertThat(replace.op()).isEqualTo(EditOp.REPLACE);
        assertThat(replace.activity()).isEqualTo(expectedRemovedActivity);
        assertThat(replace.replacement()).isEqualTo(expectedReplacementActivity);
        assertThat(replace.packageKey()).isNull();
        assertThat(replace.dayNumber()).isNull();
        assertThat(replace.slot()).isNull();
        EditRequest add = result.edits().get(1);
        assertThat(add.op()).isEqualTo(EditOp.ADD);
        assertThat(add.activity()).isEqualTo(expectedAddedActivity);
        assertThat(add.packageKey()).isEqualTo(expectedPackageKey);
        assertThat(add.dayNumber()).isEqualTo(expectedDayNumber);
        assertThat(add.slot()).isEqualTo(expectedSlot);
    }

    @Test
    void parseChatTurn_withoutEditsField_hasEmptyEdits() throws IOException {
        ChatTurnResult result = parser.parseChatTurn(fixture("chat-turn-valid.json"));

        assertThat(result.edits()).isEmpty();
    }

    @Test
    void parseChatTurn_editsNotAnArray_isTreatedAsEmpty() {
        String expectedReply = "hi";

        ChatTurnResult result = parser.parseChatTurn(
                "{\"reply\":\"" + expectedReply + "\",\"brief\":{},\"missingFields\":[],\"edits\":\"karting\"}");

        assertThat(result.reply()).isEqualTo(expectedReply);
        assertThat(result.edits()).isEmpty();
    }

    /**
     * A vague "change everything" can have the model answer with an op per activity, and each one fans
     * out to a validation pass per package. The reply is still worth keeping, so the tail is dropped.
     */
    @Test
    void parseChatTurn_withMoreEditsThanOneTurnMayCarry_keepsTheFirstOnes() {
        String expectedFirstActivity = "Activity 0";
        String expectedLastActivity = "Activity " + (LlmOutputParser.MAX_EDITS_PER_TURN - 1);
        StringBuilder edits = new StringBuilder();
        for (int i = 0; i < LlmOutputParser.MAX_EDITS_PER_TURN + 5; i++) {
            edits.append(i == 0 ? "" : ",").append("{\"op\":\"ADD\",\"activity\":\"Activity ").append(i).append("\"}");
        }

        ChatTurnResult result = parser.parseChatTurn(
                "{\"reply\":\"hi\",\"brief\":{},\"missingFields\":[],\"edits\":[" + edits + "]}");

        assertThat(result.edits()).hasSize(LlmOutputParser.MAX_EDITS_PER_TURN);
        assertThat(result.edits().get(0).activity()).isEqualTo(expectedFirstActivity);
        assertThat(result.edits().get(result.edits().size() - 1).activity()).isEqualTo(expectedLastActivity);
    }

    /**
     * An unresolvable name is stored in the edit report and read back into the chat, so a model that
     * pastes a paragraph into {@code activity} must not produce a paragraph-long rejection line.
     */
    @Test
    void parseChatTurn_capsOverlongActivityNames() {
        int expectedLength = 120;
        String overlong = "K".repeat(500);

        ChatTurnResult result = parser.parseChatTurn(
                "{\"reply\":\"hi\",\"brief\":{},\"missingFields\":[],\"edits\":["
                        + "{\"op\":\"REPLACE\",\"activity\":\"" + overlong + "\",\"replacement\":\"" + overlong
                        + "\"}]}");

        assertThat(result.edits()).singleElement().satisfies(edit -> {
            assertThat(edit.activity()).hasSize(expectedLength);
            assertThat(edit.replacement()).hasSize(expectedLength);
        });
    }

    @Test
    void parseChatTurn_replaceWithoutReplacement_isDropped() {
        ChatTurnResult result = parser.parseChatTurn(
                "{\"reply\":\"hi\",\"brief\":{},\"missingFields\":[],\"edits\":["
                        + "{\"op\":\"REPLACE\",\"activity\":\"Karting\",\"replacement\":null},"
                        + "{\"op\":\"REPLACE\",\"activity\":\"Karting\",\"replacement\":\"  \"}]}");

        assertThat(result.edits()).isEmpty();
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
    void parseTextRefresh_readsTextsAndSkipsInvalidEntries() {
        UUID expectedActivityId = UUID.randomUUID();
        String expectedDescription = "Two nights, one legend";
        String expectedWhy = "Loud, cheap, unforgettable";
        String expectedSummary = "Start slow, end loud";
        int expectedDayNumber = 2;
        String json = "{\"packages\":["
                + "{\"key\":\"BASIC\",\"description\":\"" + expectedDescription + "\","
                + "\"why\":[{\"activityId\":\"" + expectedActivityId + "\",\"text\":\"" + expectedWhy + "\"},"
                + "{\"activityId\":\"beer-bike\",\"text\":\"not a uuid\"}],"
                + "\"summaries\":[{\"dayNumber\":" + expectedDayNumber + ",\"text\":\"" + expectedSummary + "\"},"
                + "{\"dayNumber\":\"two\",\"text\":\"not an int\"}]},"
                + "{\"key\":\"DELUXE\",\"description\":\"unknown tier\"}]}";

        Map<Tier, PackageTexts> texts = parser.parseTextRefresh(json);

        assertThat(texts).containsOnlyKeys(Tier.BASIC);
        PackageTexts basic = texts.get(Tier.BASIC);
        assertThat(basic.description()).isEqualTo(expectedDescription);
        assertThat(basic.whyByActivityId()).containsExactly(entry(expectedActivityId, expectedWhy));
        assertThat(basic.summaryByDay()).containsExactly(entry(expectedDayNumber, expectedSummary));
    }

    @Test
    void parseTextRefresh_withoutPackages_isLlmOutputException() {
        assertThatThrownBy(() -> parser.parseTextRefresh("{\"texts\":[]}"))
                .isInstanceOf(LlmOutputException.class).hasMessageContaining("packages");
    }

    @Test
    void parsePlan_rejectsNonUuidActivityIdAndNonJson() {
        assertThatThrownBy(() -> parser.parsePlan("{\"packages\":[{\"key\":\"BASIC\",\"days\":[{\"dayNumber\":1,\"items\":[{\"slot\":\"EVENING\",\"activityId\":\"beer-bike\"}]}]}]}"))
                .isInstanceOf(LlmOutputException.class).hasMessageContaining("activityId");
        assertThatThrownBy(() -> parser.parsePlan("Sure! Here is your plan:"))
                .isInstanceOf(LlmOutputException.class);
    }
}
