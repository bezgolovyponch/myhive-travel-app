package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.edit.EditMessages;
import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.edit.EditRejectionReason;
import com.myhive.backend.ai.edit.EditReport;
import com.myhive.backend.ai.edit.EditRequest;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.ChatTurnRequest;
import com.myhive.backend.ai.llm.ChatTurnResult;
import com.myhive.backend.ai.llm.FakeLlmGateway;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatTurnNodeTest {

    private static final String ACTIVITY_NAME = "Beer Bike";
    private static final String REPLACEMENT_NAME = "Karting";

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final ChatTurnNode node = new ChatTurnNode(llm);

    @Test
    void editsWithPackages_routeToEdit_andKeepTheBrief() {
        Brief expectedBrief = readyBrief();
        llm.queueChat(turn("On it!", Brief.empty(), List.of(replaceEdit())));

        Map<String, Object> update = node.apply(stateWithPackages(expectedBrief));

        assertThat(update.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_EDIT);
        assertThat(update.get(PlannerState.EDITS)).asString()
                .contains(ACTIVITY_NAME).contains(REPLACEMENT_NAME).contains(EditOp.REPLACE.name());
        assertThat(update.get(PlannerState.BRIEF)).isEqualTo(JsonCodec.write(expectedBrief));
        assertThat(update.get(PlannerState.EDIT_REPORT)).isEqualTo("");
        assertThat(messagesOf(update)).hasSize(1);
    }

    @Test
    void editsWithoutPackages_areRejectedNoPackagesYet_withANote() {
        String expectedNote = EditMessages.noPackagesYet("en");
        llm.queueChat(turn("On it!", Brief.empty(), List.of(replaceEdit())));

        Map<String, Object> update = node.apply(new PlannerState(baseState(Brief.empty())));

        assertThat(update.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_NONE);
        assertThat(update).doesNotContainKey(PlannerState.EDITS);
        EditReport report = JsonCodec.read((String) update.get(PlannerState.EDIT_REPORT), EditReport.class);
        assertThat(report.applied()).isEmpty();
        assertThat(report.rejected()).singleElement()
                .satisfies(rejected -> {
                    assertThat(rejected.reason()).isEqualTo(EditRejectionReason.NO_PACKAGES_YET);
                    assertThat(rejected.activityName()).isEqualTo(ACTIVITY_NAME);
                });
        assertThat(messagesOf(update)).hasSize(2);
        assertThat(messagesOf(update).get(1).get("content")).isEqualTo(expectedNote);
    }

    @Test
    void briefChangeAndEdits_regenerationWins() {
        llm.queueChat(turn("Rebuilding!", readyBrief(), List.of(replaceEdit())));
        Map<String, Object> initData = stateMapWithPackages(Brief.empty());
        initData.remove(PlannerState.LAST_GENERATED_BRIEF);

        Map<String, Object> update = node.apply(new PlannerState(initData));

        assertThat(update.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_GENERATE);
        assertThat(update).doesNotContainKey(PlannerState.EDITS);
        assertThat(update.get(PlannerState.EDIT_REPORT)).isEqualTo("");
    }

    @Test
    void noEdits_behavesAsBefore() {
        String expectedReply = "How many days?";
        llm.queueChat(turn(expectedReply, Brief.empty(), List.of()));

        Map<String, Object> update = node.apply(new PlannerState(baseState(Brief.empty())));

        assertThat(update.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_NONE);
        assertThat(update).doesNotContainKey(PlannerState.EDITS);
        assertThat(update.get(PlannerState.MISSING_FIELDS)).isEqualTo(Brief.empty().missingFields());
        assertThat(messagesOf(update)).singleElement()
                .satisfies(message -> assertThat(message.get("content")).isEqualTo(expectedReply));
    }

    @Test
    void everyTurnClearsLastTurnsEditReport_exceptTheSeedTurn() {
        llm.queueChat(turn("Glad you like it!", Brief.empty(), List.of()));
        Map<String, Object> withStaleReport = stateMapWithPackages(readyBrief());
        withStaleReport.put(PlannerState.EDIT_REPORT, JsonCodec.write(
                EditReport.allRejected(List.of(replaceEdit()), EditRejectionReason.NO_FREE_SLOT)));
        Map<String, Object> seed = baseState(Brief.empty());
        seed.put(PlannerState.ACTION, PlannerState.ACTION_SEED);

        Map<String, Object> update = node.apply(new PlannerState(withStaleReport));
        Map<String, Object> seeded = node.apply(new PlannerState(seed));

        // left standing, last turn's rejections would be served again with this turn's answer
        assertThat(update.get(PlannerState.EDIT_REPORT)).isEqualTo("");
        assertThat(seeded).doesNotContainKey(PlannerState.EDIT_REPORT);
    }

    @Test
    void requestCarriesPackagesViewOnlyWhenAResultExists() {
        llm.queueChat(turn("Sure", Brief.empty(), List.of()), turn("Sure", Brief.empty(), List.of()));

        node.apply(new PlannerState(baseState(Brief.empty())));
        node.apply(stateWithPackages(readyBrief()));

        ChatTurnRequest withoutPackages = llm.chatRequests.get(0);
        assertThat(withoutPackages.packagesView()).isNull();
        assertThat(withoutPackages.catalogNames()).isEmpty();
        ChatTurnRequest withPackages = llm.chatRequests.get(1);
        assertThat(withPackages.packagesView()).contains(Tier.BASIC + ": day 1 [EVENING " + ACTIVITY_NAME + "]");
        assertThat(withPackages.catalogNames()).containsExactly(ACTIVITY_NAME, REPLACEMENT_NAME);
    }

    private static ChatTurnResult turn(String reply, Brief update, List<EditRequest> edits) {
        return new ChatTurnResult(reply, update, List.of(), edits, LlmUsage.none());
    }

    private static EditRequest replaceEdit() {
        return new EditRequest(EditOp.REPLACE, ACTIVITY_NAME, REPLACEMENT_NAME, null, null, null);
    }

    private static Brief readyBrief() {
        return new Brief(1, 4, List.of("nightlife"), null, null, null, DayEdge.AFTERNOON, DayEdge.EVENING, null);
    }

    /** A thread that already generated: the packages are in RESULT and the brief is the one they came from. */
    private static PlannerState stateWithPackages(Brief brief) {
        return new PlannerState(stateMapWithPackages(brief));
    }

    private static Map<String, Object> stateMapWithPackages(Brief brief) {
        Map<String, Object> initData = baseState(brief);
        initData.put(PlannerState.RESULT, JsonCodec.write(plan()));
        initData.put(PlannerState.CATALOG, JsonCodec.write(catalog()));
        initData.put(PlannerState.LAST_GENERATED_BRIEF, JsonCodec.write(brief));
        return initData;
    }

    private static Map<String, Object> baseState(Brief brief) {
        Map<String, Object> initData = new HashMap<>();
        initData.put(PlannerState.LOCALE, "en");
        initData.put(PlannerState.DESTINATION_ID, UUID.randomUUID().toString());
        initData.put(PlannerState.DESTINATION_NAME, "Prague");
        initData.put(PlannerState.CATEGORY_SLUGS, List.of("nightlife"));
        initData.put(PlannerState.BRIEF, JsonCodec.write(brief));
        initData.put(PlannerState.MESSAGES, new ArrayList<>(List.of(
                Map.of("role", "USER", "content", "swap the beer bike for karting", "at", "t"))));
        return initData;
    }

    private static ComposedPlan plan() {
        ComposedPlan.ItemResult item = new ComposedPlan.ItemResult(Slot.EVENING, "19:00", catalog().get(0).id(),
                "beer-bike", ACTIVITY_NAME, null, 90, new BigDecimal("40.00"), null, new BigDecimal("160.00"), false,
                "why");
        ComposedPlan.DayResult day = new ComposedPlan.DayResult(1, "Day one", "summary", List.of(item));
        return new ComposedPlan(List.of(new ComposedPlan.PackageResult(Tier.BASIC, "title", "tagline", "description",
                new BigDecimal("40.00"), new BigDecimal("160.00"), ComposedPlan.CURRENCY, 90,
                List.of(item.activityId()), List.of(day))), false);
    }

    private static List<CatalogActivity> catalog() {
        return List.of(
                new CatalogActivity(UUID.nameUUIDFromBytes(ACTIVITY_NAME.getBytes()), "beer-bike", ACTIVITY_NAME,
                        "Pedal and drink", 90, true, new BigDecimal("40.00"), null, null, List.of("nightlife")),
                new CatalogActivity(UUID.nameUUIDFromBytes(REPLACEMENT_NAME.getBytes()), "karting", REPLACEMENT_NAME,
                        "Race it", 90, true, new BigDecimal("50.00"), null, null, List.of("driving")));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> messagesOf(Map<String, Object> update) {
        return (List<Map<String, String>>) update.get(PlannerState.MESSAGES);
    }
}
