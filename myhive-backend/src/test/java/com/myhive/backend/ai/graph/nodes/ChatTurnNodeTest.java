package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogSnapshotter;
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
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;

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
        // The reply was written before anyone checked the edit and the edit's own line says what happened:
        // "On it!" is not said at all.
        assertThat(update.get(PlannerState.PENDING_REPLY)).isEqualTo("");
        assertThat(update).doesNotContainKey(PlannerState.MESSAGES);
    }

    /**
     * The model is told to announce a build once the brief is complete, and the brief stays complete: in
     * live chats it said "building your three options now" under a draft the organizer was editing.
     * On a turn that builds nothing that line never reaches the chat.
     */
    @Test
    void underADraft_theChatNeverSaysItIsBuilding() {
        String staleLine = "On it - building your three options now.";
        llm.queueChat(turn(staleLine, Brief.empty(), List.of()));
        llm.queueChat(new ChatTurnResult(staleLine, Brief.empty(), List.of(), List.of(), LlmUsage.none(), List.of(),
                List.of(REPLACEMENT_NAME)));
        llm.queueChat(turn(staleLine, Brief.empty(), List.of(replaceEdit())));

        Map<String, Object> plain = node.apply(stateWithPackages(readyBrief()));
        Map<String, Object> recommending = node.apply(stateWithPackages(readyBrief()));
        Map<String, Object> editing = node.apply(stateWithPackages(readyBrief()));

        assertThat(messagesOf(plain)).singleElement()
                .satisfies(message -> assertThat(message.get("content")).startsWith("Your trip plan stays as it is"));
        assertThat(messagesOf(recommending)).singleElement().satisfies(message ->
                assertThat(message.get("content")).isEqualTo("Here is what fits - tap a name to see it, or add it."));
        // Before an edit's own line the announcement is simply dropped.
        assertThat(editing.get(PlannerState.PENDING_REPLY)).isEqualTo("");
    }

    /**
     * A change of taste rebuilds the three options while they are only options. Once one is the organizer's
     * own plan it does not: a rebuild would throw away what they added, removed and moved. A different
     * trip length still does.
     */
    @Test
    void aChangeOfTaste_rebuildsTheOptions_butNotTheOrganizersOwnPlan() {
        Brief tasteChanged = new Brief(null, null, List.of("extreme"), "more adrenaline", null, null, null, null, null);
        Brief longerTrip = new Brief(3, null, List.of(), null, null, null, null, null, null);
        llm.queueChat(turn("On it.", tasteChanged, List.of()));
        llm.queueChat(turn("On it.", tasteChanged, List.of()));
        llm.queueChat(turn("On it.", longerTrip, List.of()));
        Map<String, Object> ownPlan = stateMapWithPackages(readyBrief());
        ownPlan.put(PlannerState.WORKING_PACKAGE, "MEDIUM");

        Map<String, Object> options = node.apply(stateWithPackages(readyBrief()));
        Map<String, Object> theirs = node.apply(new PlannerState(ownPlan));
        Map<String, Object> theirsLonger = node.apply(new PlannerState(ownPlan));

        assertThat(options.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_GENERATE);
        assertThat(theirs.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_NONE);
        // A longer trip would rebuild their plan: said first, with the plan and its brief left as they are.
        assertThat(theirsLonger.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_NONE);
        assertThat(theirsLonger).containsEntry(PlannerState.REBUILD_WARNED, true);
        assertThat(theirsLonger.get(PlannerState.BRIEF)).isEqualTo(JsonCodec.write(readyBrief()));
        assertThat(messagesOf(theirsLonger)).singleElement().satisfies(message ->
                assertThat(message.get("content")).asString().contains("rebuild the plan from scratch"));
        assertThat(theirsLonger.get(PlannerState.SUGGESTED_REPLIES)).asList().hasSize(2);
    }

    /** Asked for again on the turn after the warning, the rebuild goes ahead. */
    @Test
    void aLongerTrip_askedForAgainAfterTheWarning_rebuildsTheOrganizersPlan() {
        llm.queueChat(turn("On it.", new Brief(3, null, List.of(), null, null, null, null, null, null), List.of()));
        Map<String, Object> warned = stateMapWithPackages(readyBrief());
        warned.put(PlannerState.WORKING_PACKAGE, "MEDIUM");
        warned.put(PlannerState.REBUILD_WARNED, true);

        Map<String, Object> rebuilt = node.apply(new PlannerState(warned));

        assertThat(rebuilt.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_GENERATE);
        assertThat(rebuilt).containsEntry(PlannerState.REBUILD_WARNED, false);
    }

    /**
     * A typed add is offered on a card rather than carried out - but only a catalog row can be offered. When
     * nothing the model named resolves, the chat says it could not find it instead of pointing at an empty row.
     */
    @Test
    void aTypedAdd_theCatalogCannotAnswer_saysSo_insteadOfPointingAtAnEmptyRow() {
        String expectedMissing = "Bungee Jumping";
        llm.queueChat(turn("Adding it now.", Brief.empty(),
                List.of(new EditRequest(EditOp.ADD, expectedMissing, null, null, null, null))));
        llm.queueChat(turn("Adding it now.", Brief.empty(),
                List.of(new EditRequest(EditOp.ADD, expectedMissing, null, null, null, null, List.of(REPLACEMENT_NAME)))));

        Map<String, Object> nothingOnOffer = node.apply(stateWithPackages(readyBrief()));
        Map<String, Object> alternativeOnOffer = node.apply(stateWithPackages(readyBrief()));

        assertThat(nothingOnOffer.get(PlannerState.RECOMMENDATIONS)).isEqualTo(List.of());
        assertThat(messagesOf(nothingOnOffer)).singleElement().satisfies(message ->
                assertThat(message.get("content")).contains(expectedMissing).doesNotContain("top match"));
        // The alternative the model named is on offer: the chat says it is the closest thing to what was
        // asked for, not that it could not find it.
        assertThat(alternativeOnOffer.get(PlannerState.RECOMMENDATIONS)).isEqualTo(List.of(REPLACEMENT_NAME));
        assertThat(messagesOf(alternativeOnOffer)).singleElement().satisfies(message ->
                assertThat(message.get("content")).contains(expectedMissing)
                        .startsWith("Here is what comes closest to").doesNotContain("could not find"));
    }

    /** "beer" is two catalog rows: that is a question back, never "not in the catalog". */
    @Test
    void aTypedAdd_thatMatchesTwoCatalogRows_asksWhichOne_insteadOfSayingItIsMissing() {
        Map<String, Object> state = stateMapWithPackages(readyBrief());
        List<CatalogActivity> twoBeers = new ArrayList<>(catalog());
        twoBeers.add(new CatalogActivity(UUID.nameUUIDFromBytes("Beer Spa".getBytes()), "beer-spa", "Beer Spa",
                "Soak in it", 90, true, new BigDecimal("40.00"), null, null, List.of("wellness")));
        state.put(PlannerState.CATALOG, JsonCodec.write(twoBeers));
        llm.queueChat(turn("Adding it now.", Brief.empty(),
                List.of(new EditRequest(EditOp.ADD, "beer", null, null, null, null))));

        Map<String, Object> update = node.apply(new PlannerState(state));

        assertThat(update.get(PlannerState.RECOMMENDATIONS)).isEqualTo(List.of());
        assertThat(messagesOf(update)).singleElement().satisfies(message ->
                assertThat(message.get("content")).contains("which one").doesNotContain("could not find"));
    }

    /** The model volunteers names on its own; one the catalog lacks is dropped, and the reply is the model's. */
    @Test
    void aModelRecommendation_theCatalogLacks_isDropped_andTheReplyIsKept() {
        String expectedReply = "Saturday night is open - a club or a cruise would fit.";
        llm.queueChat(new ChatTurnResult(expectedReply, Brief.empty(), List.of(), List.of(), LlmUsage.none(), List.of(),
                List.of("Pub Quiz Night")));

        Map<String, Object> update = node.apply(stateWithPackages(readyBrief()));

        assertThat(update.get(PlannerState.RECOMMENDATIONS)).isEqualTo(List.of());
        assertThat(messagesOf(update)).singleElement()
                .satisfies(message -> assertThat(message.get("content")).isEqualTo(expectedReply));
    }

    /** The catalog is a JSON blob in the checkpoint and the state parses it on every call: once per turn. */
    @Test
    void aChatTurn_parsesTheCatalogOnce() {
        PlannerState state = Mockito.spy(stateWithPackages(readyBrief()));
        llm.queueChat(turn("Anything else?", Brief.empty(), List.of()));

        node.apply(state);

        verify(state, times(1)).catalog();
    }

    /** A reply held back on an earlier edit turn must never surface on a turn that edits nothing. */
    @Test
    void aTurnWithoutEdits_clearsAnyHeldBackReply_andSaysItsReplyNow() {
        llm.queueChat(turn("Anything else?", Brief.empty(), List.of()));
        Map<String, Object> state = stateMapWithPackages(readyBrief());
        state.put(PlannerState.PENDING_REPLY, "Swapping it now.");

        Map<String, Object> update = node.apply(new PlannerState(state));

        assertThat(update.get(PlannerState.PENDING_REPLY)).isEqualTo("");
        assertThat(messagesOf(update)).singleElement()
                .satisfies(message -> assertThat(message.get("content")).isEqualTo("Anything else?"));
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

    /**
     * A message that changes the brief and asks for a swap in the same breath: the regeneration rebuilds
     * every package, so the ops are moot rather than rejected — but they used to vanish without a word,
     * with the reply cheerfully confirming a change that was never applied.
     */
    @Test
    void briefChangeAndEdits_regenerationWins_andSaysSo() {
        String expectedNote = EditMessages.rebuildingFirst("en");
        llm.queueChat(turn("Rebuilding!", readyBrief(), List.of(replaceEdit())));
        Map<String, Object> initData = stateMapWithPackages(Brief.empty());
        initData.remove(PlannerState.LAST_GENERATED_BRIEF);

        Map<String, Object> update = node.apply(new PlannerState(initData));

        assertThat(update.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_GENERATE);
        assertThat(update).doesNotContainKey(PlannerState.EDITS);
        // no per-op report either: nothing was attempted, so there is nothing to report op by op
        assertThat(update.get(PlannerState.EDIT_REPORT)).isEqualTo("");
        assertThat(messagesOf(update)).hasSize(2);
        assertThat(messagesOf(update).get(1).get("content")).isEqualTo(expectedNote);
    }

    /** The same turn without ops says nothing extra: the note belongs to the dropped edits, not to a rebuild. */
    @Test
    void briefChangeWithoutEdits_regeneratesWithoutTheNote() {
        llm.queueChat(turn("Rebuilding!", readyBrief(), List.of()));
        Map<String, Object> initData = stateMapWithPackages(Brief.empty());
        initData.remove(PlannerState.LAST_GENERATED_BRIEF);

        Map<String, Object> update = node.apply(new PlannerState(initData));

        assertThat(update.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_GENERATE);
        assertThat(messagesOf(update)).hasSize(1);
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

    /** Before any packages the chat still sees what is on offer, so a follow-up names real variants. */
    @Test
    void requestBeforePackages_carriesTheCatalogNames_andTheTurnStoresItsSuggestedReplies() {
        List<String> expectedReplies = List.of("Just steak and beers", "With a show");
        llm.queueChat(new ChatTurnResult("Dinner - just steak, or with a show?", Brief.empty(), List.of(), List.of(),
                LlmUsage.none(), expectedReplies));
        Map<String, Object> state = baseState(Brief.empty());
        state.put(PlannerState.CATALOG, JsonCodec.write(catalog()));

        Map<String, Object> update = node.apply(new PlannerState(state));

        ChatTurnRequest request = llm.chatRequests.get(0);
        assertThat(request.packagesView()).isNull();
        assertThat(request.catalogNames()).containsExactly(ACTIVITY_NAME, REPLACEMENT_NAME);
        assertThat(update.get(PlannerState.SUGGESTED_REPLIES)).isEqualTo(expectedReplies);
    }

    /** The build starts on this turn and every message is refused until it lands: nobody could answer. */
    @Test
    void generationStart_replacesAReplyThatAsksSomething_andDropsItsChips() {
        String expectedReply = "On it - building your three options now.";
        llm.queueChat(new ChatTurnResult("Got it. When do you land on day 1 and leave on the last day?", readyBrief(),
                List.of(), List.of(), LlmUsage.none(), List.of("Arrive evening", "Arrive morning")));
        Map<String, Object> state = baseState(Brief.empty());
        state.put(PlannerState.CATALOG, JsonCodec.write(catalog()));

        Map<String, Object> update = node.apply(new PlannerState(state));

        assertThat(update.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_GENERATE);
        assertThat(messagesOf(update)).singleElement()
                .satisfies(message -> assertThat(message.get("content")).isEqualTo(expectedReply));
        assertThat(update.get(PlannerState.SUGGESTED_REPLIES)).isEqualTo(List.of());
        assertThat(update).doesNotContainKey(PlannerState.PAIRING_ASKED);
    }

    @Test
    void generationStart_keepsAReplyThatAsksNothing_andAnswersInGermanWhenItHasToReplaceOne() {
        String expectedReply = "Perfect, building three options now.";
        String expectedGermanReply = "Alles klar - ich baue jetzt eure drei Optionen.";
        llm.queueChat(new ChatTurnResult(expectedReply, readyBrief(), List.of(), List.of(), LlmUsage.none(),
                        List.of("Whatever")),
                new ChatTurnResult("Wann landet ihr?", readyBrief(), List.of(), List.of(), LlmUsage.none(), List.of()));
        Map<String, Object> german = baseState(Brief.empty());
        german.put(PlannerState.LOCALE, "de");

        Map<String, Object> kept = node.apply(new PlannerState(baseState(Brief.empty())));
        Map<String, Object> replaced = node.apply(new PlannerState(german));

        assertThat(messagesOf(kept).get(0).get("content")).isEqualTo(expectedReply);
        assertThat(kept.get(PlannerState.SUGGESTED_REPLIES)).isEqualTo(List.of());
        assertThat(messagesOf(replaced).get(0).get("content")).isEqualTo(expectedGermanReply);
    }

    /**
     * No either/or about activities before the first build: with the brief complete, a question whose
     * chips name catalog activities does not hold the options back.
     */
    @Test
    void aQuestionAboutCatalogVariants_doesNotHoldTheFirstBuildBack() {
        llm.queueChat(new ChatTurnResult("Beer bike or karting?", readyBrief(), List.of(), List.of(),
                LlmUsage.none(), List.of(ACTIVITY_NAME, REPLACEMENT_NAME)));
        Map<String, Object> state = baseState(Brief.empty());
        state.put(PlannerState.CATALOG, JsonCodec.write(catalog()));

        Map<String, Object> built = node.apply(new PlannerState(state));

        assertThat(built.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_GENERATE);
        assertThat(built.get(PlannerState.SUGGESTED_REPLIES)).isEqualTo(List.of());
    }

    /** What the catalog can deliver, not what is assigned to the destination: nightlife and karting here. */
    @Test
    void seedTurn_offersTheOpeningChipsTheCatalogCanDeliver() {
        CatalogSnapshotter snapshotter = mock(CatalogSnapshotter.class);
        when(snapshotter.snapshot(any(), any(), any())).thenReturn(catalog());
        ChatTurnNode seeding = new ChatTurnNode(llm, snapshotter);
        Map<String, Object> state = baseState(Brief.empty());
        state.put(PlannerState.CATEGORY_SLUGS, List.of());
        state.put(PlannerState.ACTION, PlannerState.ACTION_SEED);

        Map<String, Object> update = seeding.apply(new PlannerState(state));

        assertThat(update.get(PlannerState.SUGGESTED_REPLIES))
                .isEqualTo(List.of("Bar crawl + club night", "Karting by day, club by night"));
    }

    /** A catalog that cannot be read costs the chips and the follow-ups, never the chat that was just opened. */
    @Test
    void seedTurn_whenTheCatalogCannotBeRead_stillParksTheThread() {
        CatalogSnapshotter snapshotter = mock(CatalogSnapshotter.class);
        when(snapshotter.snapshot(any(), any(), any())).thenThrow(new IllegalStateException("database down"));
        ChatTurnNode seeding = new ChatTurnNode(llm, snapshotter);
        Map<String, Object> state = baseState(Brief.empty());
        state.put(PlannerState.ACTION, PlannerState.ACTION_SEED);

        Map<String, Object> update = seeding.apply(new PlannerState(state));

        assertThat(update).containsEntry(PlannerState.ACTION, PlannerState.ACTION_NONE)
                .doesNotContainKey(PlannerState.CATALOG).doesNotContainKey(PlannerState.SUGGESTED_REPLIES);
    }

    /**
     * The session that got stuck live: the entry screen had set everything but the taste, the model filed
     * "likes beer, karting" under the notes and announced a build Java never started.
     */
    @Test
    void tasteFiledUnderTheNotes_startsTheBuild_onceTheModelHasStoppedAsking() {
        String expectedTaste = "likes beer, karting";
        String expectedReply = "Building three options right now.";
        llm.queueChat(turn(expectedReply, notes(expectedTaste), List.of()));

        Map<String, Object> update = node.apply(new PlannerState(baseState(everythingButTaste())));

        Brief brief = JsonCodec.read((String) update.get(PlannerState.BRIEF), Brief.class);
        assertThat(update.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_GENERATE);
        assertThat(brief.vibe()).isEqualTo(expectedTaste);
        assertThat(brief.notes()).isNull();
        assertThat(update.get(PlannerState.MISSING_FIELDS)).isEqualTo(List.of());
        assertThat(messagesOf(update)).singleElement()
                .satisfies(message -> assertThat(message.get("content")).isEqualTo(expectedReply));
    }

    /**
     * The taste is asked once. Days and people were known before this message, so the question has been
     * put: an answer with no taste in it still builds, open to anything, and a second question is not asked.
     */
    @Test
    void onceTheTasteWasAsked_anAnswerWithoutTaste_buildsOpenToAnything() {
        String expectedNote = "his brother's stag";
        llm.queueChat(turn("Nice one. What is the group into?", notes(expectedNote), List.of()));

        Map<String, Object> update = node.apply(new PlannerState(baseState(everythingButTaste())));

        Brief brief = JsonCodec.read((String) update.get(PlannerState.BRIEF), Brief.class);
        assertThat(update.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_GENERATE);
        assertThat(brief.vibe()).isEqualTo(Brief.OPEN_TO_ANYTHING);
        assertThat(brief.notes()).isEqualTo(expectedNote);
        assertThat(update.get(PlannerState.MISSING_FIELDS)).isEqualTo(List.of());
    }

    /** Days and people arrive in this message, nothing about taste: that is when the one question is put. */
    @Test
    void aBuildAnnouncedBeforeTheTasteWasAsked_isReplacedWithTheQuestionForIt_andItsChips() {
        String expectedQuestion = "What is the group into - beer, action, a big night out?";
        List<String> expectedChips = List.of("Bar crawl + club night", "Karting by day, club by night");
        llm.queueChat(turn("Building three options right now.", everythingButTaste(), List.of()));
        Map<String, Object> state = baseState(Brief.empty());
        state.put(PlannerState.CATALOG, JsonCodec.write(catalog()));

        Map<String, Object> update = node.apply(new PlannerState(state));

        assertThat(update.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_NONE);
        assertThat(messagesOf(update)).singleElement()
                .satisfies(message -> assertThat(message.get("content")).isEqualTo(expectedQuestion));
        assertThat(update.get(PlannerState.SUGGESTED_REPLIES)).isEqualTo(expectedChips);
    }

    /** The model knew of the gap and answered something else: its answer stays and the question follows it. */
    @Test
    void aReplyThatAsksNothing_fromAModelThatKnowsOfTheGap_isFollowedByTheQuestion() {
        String expectedReply = "No karting in Prague right now, sorry.";
        String expectedQuestion = "How many of you are coming?";
        Brief noGroupSize = new Brief(3, null, List.of("nightlife"), null, null, null, DayEdge.EVENING,
                DayEdge.MORNING, null);
        llm.queueChat(new ChatTurnResult(expectedReply, Brief.empty(), List.of(Brief.FIELD_GROUP_SIZE), List.of(),
                LlmUsage.none(), List.of()));

        Map<String, Object> update = node.apply(new PlannerState(baseState(noGroupSize)));

        assertThat(messagesOf(update)).extracting(message -> message.get("content"))
                .containsExactly(expectedReply, expectedQuestion);
        assertThat(update.get(PlannerState.SUGGESTED_REPLIES)).isEqualTo(List.of());
    }

    /** Travel times are never asked: days, head-count and a taste are enough to build. */
    @Test
    void withoutTravelTimes_theTurnBuildsInsteadOfAsking() {
        Brief noEdges = new Brief(3, 8, List.of("nightlife"), null, null, null, null, null, null);
        llm.queueChat(turn("Building three options right now.", Brief.empty(), List.of()));

        Map<String, Object> update = node.apply(new PlannerState(baseState(noEdges)));

        assertThat(update.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_GENERATE);
        assertThat(messagesOf(update)).singleElement()
                .satisfies(message -> assertThat(message.get("content")).doesNotContain("land", "tickets"));
        assertThat(update.get(PlannerState.SUGGESTED_REPLIES)).isEqualTo(List.of());
    }

    /** Packages exist, so the brief has no gap: a reply that asks nothing is just a reply. */
    @Test
    void aReplyThatAsksNothing_isLeftAlone_whenTheBriefIsComplete() {
        String expectedReply = "Glad you like it!";
        llm.queueChat(turn(expectedReply, Brief.empty(), List.of()));

        Map<String, Object> update = node.apply(stateWithPackages(readyBrief()));

        assertThat(messagesOf(update)).singleElement()
                .satisfies(message -> assertThat(message.get("content")).isEqualTo(expectedReply));
    }

    private static Brief everythingButTaste() {
        return new Brief(2, 6, List.of(), null, null, null, DayEdge.MORNING, DayEdge.EVENING, null);
    }

    private static Brief notes(String notes) {
        return new Brief(null, null, List.of(), null, null, null, null, null, notes);
    }

    @Test
    void seedTurn_snapshotsTheCatalogWithoutCallingTheModel() {
        CatalogSnapshotter snapshotter = mock(CatalogSnapshotter.class);
        when(snapshotter.snapshot(any(), any(), any())).thenReturn(catalog());
        ChatTurnNode seeding = new ChatTurnNode(llm, snapshotter);
        Map<String, Object> state = baseState(Brief.empty());
        state.put(PlannerState.ACTION, PlannerState.ACTION_SEED);

        Map<String, Object> update = seeding.apply(new PlannerState(state));

        assertThat(update).containsEntry(PlannerState.ACTION, PlannerState.ACTION_NONE);
        assertThat(new PlannerState(Map.of(PlannerState.CATALOG, update.get(PlannerState.CATALOG))).catalog())
                .extracting(CatalogActivity::name).containsExactly(ACTIVITY_NAME, REPLACEMENT_NAME);
        assertThat(llm.chatRequests).isEmpty();
    }
}
