package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.edit.EditRequest;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.PlanDraft;
import java.math.BigDecimal;
import java.util.List;
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
    void parseChatTurn_readsFlexibleTravelTimes() {
        String raw = "{\"reply\":\"ok\",\"brief\":{\"arrival\":\"FLEXIBLE\",\"departure\":\"FLEXIBLE\"},\"missingFields\":[]}";

        ChatTurnResult result = parser.parseChatTurn(raw);

        assertThat(result.briefUpdate().arrival()).isEqualTo(DayEdge.FLEXIBLE);
        assertThat(result.briefUpdate().departure()).isEqualTo(DayEdge.FLEXIBLE);
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

    /** Alternatives are the model's, so they get the same hygiene as any name it writes: blanks out, a cap on count. */
    @Test
    void parseChatTurn_readsAlternatives_dropsBlanksAndKeepsAtMostThree() {
        String expectedFirst = "Nightclub VIP Experience";
        String expectedSecond = "Rooftop Jazz Night";
        String expectedThird = "Beer Bike";

        ChatTurnResult result = parser.parseChatTurn(
                "{\"reply\":\"hi\",\"brief\":{},\"missingFields\":[],\"edits\":["
                        + "{\"op\":\"ADD\",\"activity\":\"strip shows\",\"alternatives\":[\"" + expectedFirst
                        + "\",\" \",\"" + expectedSecond + "\",\"" + expectedThird + "\",\"Karting\"]},"
                        + "{\"op\":\"REMOVE\",\"activity\":\"Karting\"}]}");

        assertThat(result.edits()).hasSize(2);
        assertThat(result.edits().get(0).alternatives()).containsExactly(expectedFirst, expectedSecond, expectedThird);
        assertThat(result.edits().get(1).alternatives()).isEmpty();
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
    void parsePackageTexts_readsTitlesTaglinesDayTitlesAndTheRest() {
        UUID expectedActivityId = UUID.randomUUID();
        String expectedTitle = "Beer, Bikes and Bad Decisions";
        String expectedTagline = "Two loud nights";
        String expectedDayTitle = "Landing day";
        String expectedWhy = "Because you asked for beer";
        String raw = """
                {"packages": [{"key": "BASIC", "title": "%s", "tagline": "%s", "description": "desc",
                  "dayTitles": [{"dayNumber": 1, "text": "%s"}, {"dayNumber": "two", "text": "skipped"}],
                  "summaries": [{"dayNumber": 1, "text": "Easy start"}],
                  "why": [{"activityId": "%s", "text": "%s"}]},
                 {"key": "GOLD", "title": "ignored"}]}""".formatted(expectedTitle, expectedTagline, expectedDayTitle,
                expectedActivityId, expectedWhy);

        Map<Tier, PackageTexts> texts = parser.parsePackageTexts(raw);

        assertThat(texts).containsOnlyKeys(Tier.BASIC);
        PackageTexts basic = texts.get(Tier.BASIC);
        assertThat(basic.title()).isEqualTo(expectedTitle);
        assertThat(basic.tagline()).isEqualTo(expectedTagline);
        assertThat(basic.description()).isEqualTo("desc");
        assertThat(basic.titleByDay()).containsExactly(Map.entry(1, expectedDayTitle));
        assertThat(basic.summaryByDay()).containsExactly(Map.entry(1, "Easy start"));
        assertThat(basic.whyByActivityId()).containsExactly(Map.entry(expectedActivityId, expectedWhy));
    }

    @Test
    void parseTextRefresh_withoutPackages_isLlmOutputException() {
        assertThatThrownBy(() -> parser.parseTextRefresh("{\"texts\":[]}"))
                .isInstanceOf(LlmOutputException.class).hasMessageContaining("packages");
    }

    /**
     * An id that is neither a UUID nor a code the catalog handed out is kept as the nil id, so the
     * validator names that one item (UNKNOWN_ACTIVITY) instead of the whole answer being thrown away.
     */
    @Test
    void parsePlan_mapsAnUnknownCodeToTheNilId_andRejectsNonJson() {
        UUID expectedNilId = new UUID(0L, 0L);

        PlanDraft draft = parser.parsePlan("{\"packages\":[{\"key\":\"BASIC\",\"days\":[{\"dayNumber\":1,\"items\":[{\"slot\":\"EVENING\",\"activityId\":\"beer-bike\"}]}]}]}");

        assertThat(draft.packages().get(0).days().get(0).items().get(0).activityId()).isEqualTo(expectedNilId);
        assertThatThrownBy(() -> parser.parsePlan("Sure! Here is your plan:"))
                .isInstanceOf(LlmOutputException.class);
    }

    /** The codes the prompt hands out (A1, A2, ... by catalog position) come back resolved; a UUID still passes. */
    @Test
    void parsePlan_resolvesCatalogCodesToIds_andKeepsUuids() {
        UUID expectedFirstId = UUID.randomUUID();
        UUID expectedSecondId = UUID.randomUUID();
        List<CatalogActivity> catalog = List.of(catalogActivity(expectedFirstId), catalogActivity(expectedSecondId));

        PlanDraft draft = parser.parsePlan("{\"packages\":[{\"key\":\"BASIC\",\"days\":[{\"dayNumber\":1,\"items\":["
                + "{\"slot\":\"AFTERNOON\",\"activityId\":\" a2 \"},{\"slot\":\"EVENING\",\"activityId\":\"" + expectedFirstId
                + "\"}]}]}]}", catalog);

        assertThat(draft.packages().get(0).days().get(0).items()).extracting(PlanDraft.ItemDraft::activityId)
                .containsExactly(expectedSecondId, expectedFirstId);
    }

    @Test
    void parsePackageTexts_resolvesCodesInWhyEntries() {
        UUID expectedActivityId = UUID.randomUUID();
        String expectedWhy = "Because you asked for beer";

        Map<Tier, PackageTexts> texts = parser.parsePackageTexts(
                "{\"packages\":[{\"key\":\"BASIC\",\"why\":[{\"activityId\":\"A1\",\"text\":\"" + expectedWhy
                        + "\"},{\"activityId\":\"A9\",\"text\":\"unknown code\"}]}]}",
                Map.of("A1", expectedActivityId));

        assertThat(texts.get(Tier.BASIC).whyByActivityId()).containsExactly(Map.entry(expectedActivityId, expectedWhy));
    }

    private static CatalogActivity catalogActivity(UUID id) {
        return new CatalogActivity(id, "slug", "Name", "line", 90, true, new BigDecimal("20"), null, null, List.of());
    }

    /** Chips are garnish: bad elements are dropped, not a reason to lose the turn, and the list is capped. */
    @Test
    void parseChatTurn_keepsShortDistinctSuggestedReplies_upToFour() {
        String raw = """
                {"reply": "Dinner - just steak, or with a show?", "brief": {}, "missingFields": [],
                 "suggestedReplies": ["Just steak", " With a show ", "", 7, "Just steak",
                   "A reply that is far too long to ever fit on a chip under the message", "Boat instead",
                   "Surprise us", "Fifth one"]}
                """;

        ChatTurnResult result = parser.parseChatTurn(raw);

        assertThat(result.suggestedReplies()).containsExactly("Just steak", "With a show", "Boat instead", "Surprise us");
    }

    @Test
    void parseChatTurn_withoutSuggestedReplies_hasNone() {
        ChatTurnResult result = parser.parseChatTurn("{\"reply\": \"Hi\", \"brief\": {}}");

        assertThat(result.suggestedReplies()).isEmpty();
    }

    @Test
    void parseChatTurn_keepsDistinctRecommendations_upToSix_bestMatchFirst() {
        String raw = """
                {"reply": "The top match is above.", "brief": {}, "missingFields": [],
                 "recommendations": ["AK-47 Shooting", " Clay Shooting ", "", 3, "AK-47 Shooting", "Paintball",
                   "Pistol and AK combo", "Fifth one", "Sixth one", "One too many"]}
                """;

        ChatTurnResult result = parser.parseChatTurn(raw);

        assertThat(result.recommendations())
                .containsExactly("AK-47 Shooting", "Clay Shooting", "Paintball", "Pistol and AK combo", "Fifth one",
                        "Sixth one");
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void parseChatTurn_withoutRecommendations_hasNone() {
        assertThat(parser.parseChatTurn("{\"reply\": \"Hi\", \"brief\": {}}").recommendations()).isEmpty();
    }

    @Test
    void parseChatTurn_showPackage_keepsATierOrAll_andDropsAnythingElse() {
        assertThat(parser.parseChatTurn("{\"reply\": \"Here.\", \"brief\": {}, \"showPackage\": \"premium\"}")
                .showPackage()).isEqualTo("PREMIUM");
        assertThat(parser.parseChatTurn("{\"reply\": \"Here.\", \"brief\": {}, \"showPackage\": \"ALL\"}")
                .showPackage()).isEqualTo("ALL");
        assertThat(parser.parseChatTurn("{\"reply\": \"Here.\", \"brief\": {}, \"showPackage\": \"GOLD\"}")
                .showPackage()).isNull();
        assertThat(parser.parseChatTurn("{\"reply\": \"Hi\", \"brief\": {}}").showPackage()).isNull();
    }
}
