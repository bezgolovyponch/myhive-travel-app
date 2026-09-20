package com.myhive.backend.ai.graph;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogSnapshotter;
import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.edit.EditRejectionReason;
import com.myhive.backend.ai.edit.EditReport;
import com.myhive.backend.ai.edit.EditRequest;
import com.myhive.backend.ai.edit.GenerationEditSink;
import com.myhive.backend.ai.graph.nodes.PersistResultNode;
import com.myhive.backend.ai.graph.nodes.SelectNode;
import com.myhive.backend.ai.llm.ChatMessage;
import com.myhive.backend.ai.llm.ChatTurnResult;
import com.myhive.backend.ai.llm.FakeLlmGateway;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.llm.PackageTexts;
import com.myhive.backend.ai.llm.TextRefreshResult;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlannerGraphTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final CatalogSnapshotter snapshotter = mock(CatalogSnapshotter.class);
    private final RecordingSinks sinks = new RecordingSinks();
    private final List<CatalogActivity> catalog = new ArrayList<>();
    private PlannerGraph graph;

    static class RecordingSinks
            implements PersistResultNode.GenerationResultSink, SelectNode.SelectionSink, GenerationEditSink {
        private ComposedPlan lastPlan;
        private boolean lastDegraded;
        private UUID lastGeneration;
        private Tier lastSelected;
        private UUID lastSelectedGeneration;
        private UUID lastEditParent;
        private UUID lastEditedGeneration;
        private EditReport lastEditReport;
        private RuntimeException nextEditFailure;
        /** Stands in for a generation row the sweep closed out while the run was still going. */
        private boolean storesPlans = true;

        @Override
        public boolean ready(UUID generationId, ComposedPlan plan, boolean degraded, LlmUsage usage, int attempt) {
            if (!storesPlans) {
                return false;
            }
            lastGeneration = generationId;
            lastPlan = plan;
            lastDegraded = degraded;
            return true;
        }

        @Override
        public void selected(UUID generationId, Tier key) {
            lastSelected = key;
            lastSelectedGeneration = generationId;
        }

        @Override
        public UUID edited(UUID parentGenerationId, ComposedPlan plan, EditReport report, LlmUsage usage) {
            lastEditParent = parentGenerationId;
            lastPlan = plan;
            lastEditReport = report;
            if (nextEditFailure != null) {
                RuntimeException failure = nextEditFailure;
                nextEditFailure = null;
                throw failure;
            }
            lastEditedGeneration = UUID.randomUUID();
            return lastEditedGeneration;
        }
    }

    @BeforeEach
    void setUp() {
        for (int i = 0; i < 6; i++) {
            catalog.add(new CatalogActivity(UUID.randomUUID(), "a" + i, "Activity " + i, "line", 90, true,
                    new BigDecimal(20 + i * 10), null, null, List.of("nightlife")));
        }
        when(snapshotter.snapshot(any(), any(), any())).thenReturn(catalog);
        graph = TestPlannerGraphs.inMemory(llm, snapshotter, sinks, sinks, sinks);
    }

    private static ChatTurnResult turn(String reply, Brief update) {
        return new ChatTurnResult(reply, update, List.of(), LlmUsage.none());
    }

    private static ChatTurnResult turn(String reply, Brief update, List<EditRequest> edits) {
        return new ChatTurnResult(reply, update, List.of(), edits, LlmUsage.none());
    }

    private static Brief readyBrief() {
        return new Brief(1, 4, List.of("nightlife"), null, null, null, DayEdge.AFTERNOON, DayEdge.EVENING, null);
    }

    private PlanDraft.PackageDraft pkg(Tier tier, int... idx) {
        List<PlanDraft.ItemDraft> items = new ArrayList<>();
        Slot[] slots = {Slot.AFTERNOON, Slot.EVENING, Slot.NIGHT};
        for (int i = 0; i < idx.length; i++) {
            items.add(new PlanDraft.ItemDraft(slots[i], null, catalog.get(idx[i]).id(), "why"));
        }
        return new PlanDraft.PackageDraft(tier, tier.name(), "t", "d", List.of(new PlanDraft.DayDraft(1, "Day", "s", items)));
    }

    private PlanDraft validDraft() {
        return new PlanDraft(List.of(pkg(Tier.BASIC, 0), pkg(Tier.MEDIUM, 2), pkg(Tier.PREMIUM, 4, 5)));
    }

    private PlanDraft brokenDraft() {
        return new PlanDraft(List.of(pkg(Tier.BASIC, 0), pkg(Tier.MEDIUM, 0), pkg(Tier.PREMIUM, 0)));
    }

    private static Map<String, Object> startInputs() {
        return Map.of(
                PlannerState.LOCALE, "en",
                PlannerState.DESTINATION_ID, UUID.randomUUID().toString(),
                PlannerState.DESTINATION_NAME, "Prague",
                PlannerState.CATEGORY_SLUGS, List.of("nightlife"),
                PlannerState.BRIEF, JsonCodec.write(Brief.empty()),
                PlannerState.MESSAGES, List.of(userMessage("hi")));
    }

    private static Map<String, String> userMessage(String content) {
        return Map.of("role", "USER", "content", content, "at", "t");
    }

    /**
     * Exactly what {@code PlanGenerationService.runJob} writes before resuming a thread parked at
     * awaitGeneration. GENERATE is the only reason that reaches the generation branch.
     */
    private static Map<String, Object> generationResume(UUID generationId) {
        return Map.of(PlannerState.GENERATION_ID, generationId.toString(),
                PlannerState.RESUME_REASON, ResumeReason.GENERATE.name());
    }

    @Test
    void chatParksAtAwaitUser_untilBriefIsReady_thenGeneratesAutomatically() {
        UUID token = UUID.randomUUID();
        String expectedReply = "How many days?";
        llm.queueChat(turn(expectedReply, Brief.empty()));
        graph.start(token, startInputs());

        PlannerGraph.PlannerStateSnapshot snap = graph.snapshot(token);
        assertThat(snap.next()).isEqualTo(PlannerGraph.AWAIT_USER);
        assertThat(snap.state().messages()).hasSize(2);
        assertThat(snap.state().messages().get(1).content()).isEqualTo(expectedReply);

        llm.queueChat(turn("Building it now!", readyBrief()));
        graph.update(token, Map.of(PlannerState.MESSAGES, List.of(userMessage("1 day, 4 of us, bars")),
                PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name()));
        graph.runUntilInterrupt(token);

        snap = graph.snapshot(token);
        assertThat(snap.next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);
        assertThat(snap.state().brief().isReady()).isTrue();
        assertThat(llm.chatRequests.get(1).brief().isReady()).isFalse();
        // user, assistant, user, assistant: updateState APPENDS to the messages channel, never replaces it
        assertThat(snap.state().messages()).hasSize(4);
    }

    @Test
    void generation_happyPath_persistsResultAndParksAtSelection() {
        UUID token = UUID.randomUUID();
        UUID expectedGenerationId = UUID.randomUUID();
        llm.queueChat(turn("go", readyBrief())).queuePlan(validDraft());
        graph.start(token, startInputs());
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);

        graph.update(token, generationResume(expectedGenerationId));
        graph.runUntilInterrupt(token);

        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
        assertThat(sinks.lastGeneration).isEqualTo(expectedGenerationId);
        assertThat(sinks.lastPlan.packages()).extracting(ComposedPlan.PackageResult::key)
                .containsExactly(Tier.BASIC, Tier.MEDIUM, Tier.PREMIUM);
        assertThat(sinks.lastDegraded).isFalse();
        assertThat(graph.snapshot(token).state().attempt()).isEqualTo(0);
    }

    @Test
    void invalidDraft_isRepairedOnce_thenFallsBack() {
        UUID token = UUID.randomUUID();
        llm.queueChat(turn("go", readyBrief())).queuePlan(brokenDraft()).queueRepair(brokenDraft());
        graph.start(token, startInputs());
        graph.update(token, generationResume(UUID.randomUUID()));

        graph.runUntilInterrupt(token);

        assertThat(llm.repairRequests).hasSize(1);
        assertThat(llm.repairRequests.get(0).violations()).isNotEmpty();
        assertThat(sinks.lastDegraded).isTrue();
        assertThat(sinks.lastPlan.packages()).hasSize(3);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
    }

    @Test
    void repairedDraft_isAcceptedWithoutFallback() {
        UUID token = UUID.randomUUID();
        int expectedAttempts = 1;
        llm.queueChat(turn("go", readyBrief())).queuePlan(brokenDraft()).queueRepair(validDraft());
        graph.start(token, startInputs());
        graph.update(token, generationResume(UUID.randomUUID()));

        graph.runUntilInterrupt(token);

        assertThat(sinks.lastDegraded).isFalse();
        assertThat(graph.snapshot(token).state().attempt()).isEqualTo(expectedAttempts);
    }

    @Test
    void select_thenRegenerate_thenChatAgain_allResumeFromSelection() {
        UUID token = UUID.randomUUID();
        Tier expectedSelection = Tier.MEDIUM;
        Tier expectedLateSelection = Tier.BASIC;
        llm.queueChat(turn("go", readyBrief())).queuePlan(validDraft(), validDraft());
        graph.start(token, startInputs());
        graph.update(token, generationResume(UUID.randomUUID()));
        graph.runUntilInterrupt(token);

        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.SELECT.name(),
                PlannerState.SELECTED_PACKAGE_KEY, expectedSelection.name()));
        graph.runUntilInterrupt(token);
        assertThat(sinks.lastSelected).isEqualTo(expectedSelection);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);

        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.GENERATE.name(),
                PlannerState.GENERATION_ID, UUID.randomUUID().toString()));
        graph.runUntilInterrupt(token);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);
        graph.runUntilInterrupt(token);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
        assertThat(llm.planRequests).hasSize(2);

        llm.queueChat(turn("Sure, what would you change?", Brief.empty()));
        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name(),
                PlannerState.MESSAGES, List.of(userMessage("less bars"))));
        graph.runUntilInterrupt(token);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_USER);

        // the packages are still on the page, so a pick must land even while the thread waits for chat
        int chatTurnsBeforeLateSelection = llm.chatRequests.size();
        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.SELECT.name(),
                PlannerState.SELECTED_PACKAGE_KEY, expectedLateSelection.name()));
        graph.runUntilInterrupt(token);
        assertThat(sinks.lastSelected).isEqualTo(expectedLateSelection);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
        assertThat(llm.chatRequests).hasSize(chatTurnsBeforeLateSelection);
    }

    @Test
    void chatAfterGeneration_regeneratesOnlyWhenTheBriefChanges() {
        UUID token = UUID.randomUUID();
        int expectedGroupSize = 6;
        llm.queueChat(turn("go", readyBrief())).queuePlan(validDraft(), validDraft());
        graph.start(token, startInputs());
        graph.update(token, generationResume(UUID.randomUUID()));
        graph.runUntilInterrupt(token);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);

        // small talk: the model extracts nothing new -> no regeneration
        llm.queueChat(turn("Glad you like it!", Brief.empty()));
        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name(),
                PlannerState.MESSAGES, List.of(userMessage("nice"))));
        graph.runUntilInterrupt(token);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_USER);
        assertThat(llm.planRequests).hasSize(1);

        // a real change (6 people instead of 4) -> regenerate automatically
        llm.queueChat(turn("Rebuilding for 6!",
                new Brief(null, expectedGroupSize, null, null, null, null, null, null, null)));
        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name(),
                PlannerState.MESSAGES, List.of(userMessage("actually 6 of us"))));
        graph.runUntilInterrupt(token);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);
        graph.update(token, generationResume(UUID.randomUUID()));
        graph.runUntilInterrupt(token);
        assertThat(llm.planRequests).hasSize(2);
        assertThat(llm.planRequests.get(1).brief().groupSize()).isEqualTo(expectedGroupSize);
    }

    @Test
    void llmFailureDuringCompose_marksLastErrorAndStillFallsBack() {
        UUID token = UUID.randomUUID();
        int expectedRepairAttempts = 1;
        // compose blows up and no repair answer is queued either, so both model legs fail and the
        // greedy composer has to carry the generation on its own
        llm.queueChat(turn("go", readyBrief())).failNextPlan(new RuntimeException("boom"));
        graph.start(token, startInputs());
        graph.update(token, generationResume(UUID.randomUUID()));

        graph.runUntilInterrupt(token);

        assertThat(llm.repairRequests).hasSize(expectedRepairAttempts);
        assertThat(sinks.lastDegraded).isTrue();
        assertThat(graph.snapshot(token).state().lastError()).contains("INTERNAL");
    }

    @Test
    void generationWithoutGenerationId_finishesButPersistsNothing() {
        UUID token = UUID.randomUUID();
        llm.queueChat(turn("go", readyBrief())).queuePlan(validDraft());
        graph.start(token, startInputs());
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);

        // the service forgot to stamp GENERATION_ID: the run must still complete, but store nothing
        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.GENERATE.name()));
        graph.runUntilInterrupt(token);

        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
        assertThat(sinks.lastPlan).isNull();
        assertThat(sinks.lastGeneration).isNull();
    }

    /** Parks a fresh thread at awaitGeneration with a ready brief, without running the generation. */
    private UUID threadParkedAtAwaitGeneration() {
        UUID token = UUID.randomUUID();
        llm.queueChat(turn("go", readyBrief()));
        graph.start(token, startInputs());
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);
        return token;
    }

    @Test
    void awaitGeneration_resumedWithAUserMessage_answersInChatInsteadOfBuildingAPlan() {
        UUID token = threadParkedAtAwaitGeneration();
        String expectedReply = "Sure - what would you change?";
        llm.queueChat(turn(expectedReply, Brief.empty()));

        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name(),
                PlannerState.MESSAGES, List.of(userMessage("actually, hold on"))));
        graph.runUntilInterrupt(token);

        // an unconditional awaitGeneration -> snapshotCatalog edge would have run a whole generation
        // on the caller's thread and never answered the message
        assertThat(llm.planRequests).isEmpty();
        List<ChatMessage> messages = graph.snapshot(token).state().messages();
        assertThat(messages.get(messages.size() - 1).content()).isEqualTo(expectedReply);
    }

    @Test
    void awaitGeneration_resumedWithASelection_recordsThePickInsteadOfBuildingAPlan() {
        UUID token = threadParkedAtAwaitGeneration();
        Tier expectedSelection = Tier.PREMIUM;

        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.SELECT.name(),
                PlannerState.SELECTED_PACKAGE_KEY, expectedSelection.name(),
                PlannerState.GENERATION_ID, UUID.randomUUID().toString()));
        graph.runUntilInterrupt(token);

        assertThat(llm.planRequests).isEmpty();
        assertThat(sinks.lastSelected).isEqualTo(expectedSelection);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
    }

    @Test
    void awaitGeneration_resumedWithGenerate_isTheOnlyWayIntoTheGenerationBranch() {
        UUID token = threadParkedAtAwaitGeneration();
        UUID expectedGenerationId = UUID.randomUUID();
        llm.queuePlan(validDraft());

        graph.update(token, generationResume(expectedGenerationId));
        graph.runUntilInterrupt(token);

        assertThat(llm.planRequests).hasSize(1);
        assertThat(sinks.lastGeneration).isEqualTo(expectedGenerationId);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
    }

    /** A thread whose packages already exist: parked at awaitSelection, BASIC holding catalog activity 0. */
    private UUID threadWithPackages(UUID generationId) {
        UUID token = UUID.randomUUID();
        llm.queueChat(turn("go", readyBrief())).queuePlan(validDraft());
        graph.start(token, startInputs());
        graph.update(token, generationResume(generationId));
        graph.runUntilInterrupt(token);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
        return token;
    }

    /** What an edit turn looks like from the outside: a user message and a chat answer carrying ops. */
    private void editTurn(UUID token, String message, List<EditRequest> edits) {
        llm.queueChat(turn("On it!", Brief.empty(), edits));
        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name(),
                PlannerState.MESSAGES, List.of(userMessage(message))));
        graph.runUntilInterrupt(token);
    }

    private EditRequest swapFirstForSecond() {
        return new EditRequest(EditOp.REPLACE, catalog.get(0).name(), catalog.get(1).name(), null, null, null);
    }

    private void queueRefresh(String description) {
        llm.queueRefresh(new TextRefreshResult(
                Map.of(Tier.BASIC, new PackageTexts(description, Map.of(), Map.of())),
                new LlmUsage("fake-chat", 5, 7, 3L)));
    }

    @Test
    void editTurn_afterGeneration_appliesEdits_andParksAtAwaitSelection() {
        UUID expectedParent = UUID.randomUUID();
        String expectedDescription = "Rebuilt around the swap";
        String expectedActivity = catalog.get(1).name();
        UUID token = threadWithPackages(expectedParent);
        queueRefresh(expectedDescription);

        editTurn(token, "swap activity 0 for activity 1", List.of(swapFirstForSecond()));

        PlannerGraph.PlannerStateSnapshot snap = graph.snapshot(token);
        assertThat(snap.next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
        assertThat(sinks.lastEditParent).isEqualTo(expectedParent);
        assertThat(sinks.lastEditReport.applied()).singleElement()
                .satisfies(applied -> assertThat(applied.packageKey()).isEqualTo(Tier.BASIC));
        assertThat(snap.state().generationId()).contains(sinks.lastEditedGeneration);
        // an edit is not a generation: the planner model is never asked for a new draft
        assertThat(llm.planRequests).hasSize(1);
        assertThat(namesIn(snap.state().result().orElseThrow(), Tier.BASIC)).containsExactly(expectedActivity);
        assertThat(packageOf(snap.state().result().orElseThrow(), Tier.BASIC).description())
                .isEqualTo(expectedDescription);
        assertThat(snap.state().editReport()).hasValueSatisfying(report -> {
            assertThat(report.textsRefreshed()).isTrue();
            assertThat(report.rejected()).isEmpty();
        });
        assertThat(snap.state().edits()).isEmpty();
    }

    @Test
    void editTurn_thenSelect_selectsFromTheEditedPlan() {
        Tier expectedSelection = Tier.BASIC;
        UUID token = threadWithPackages(UUID.randomUUID());
        queueRefresh("Rebuilt around the swap");
        editTurn(token, "swap activity 0 for activity 1", List.of(swapFirstForSecond()));
        UUID expectedGeneration = sinks.lastEditedGeneration;

        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.SELECT.name(),
                PlannerState.SELECTED_PACKAGE_KEY, expectedSelection.name()));
        graph.runUntilInterrupt(token);

        assertThat(sinks.lastSelected).isEqualTo(expectedSelection);
        assertThat(sinks.lastSelectedGeneration).isEqualTo(expectedGeneration);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
    }

    @Test
    void editTurn_thenBriefChange_regenerates() {
        int expectedGroupSize = 6;
        UUID token = threadWithPackages(UUID.randomUUID());
        queueRefresh("Rebuilt around the swap");
        editTurn(token, "swap activity 0 for activity 1", List.of(swapFirstForSecond()));
        assertThat(llm.planRequests).hasSize(1);

        // the same turn carries edits and a changed brief: the regeneration wins and the edits are dropped
        llm.queueChat(turn("Rebuilding for 6!",
                        new Brief(null, expectedGroupSize, null, null, null, null, null, null, null),
                        List.of(swapFirstForSecond())))
                .queuePlan(validDraft());
        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name(),
                PlannerState.MESSAGES, List.of(userMessage("actually 6 of us"))));
        graph.runUntilInterrupt(token);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);
        graph.update(token, generationResume(UUID.randomUUID()));
        graph.runUntilInterrupt(token);

        assertThat(llm.planRequests).hasSize(2);
        assertThat(llm.planRequests.get(1).brief().groupSize()).isEqualTo(expectedGroupSize);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
    }

    @Test
    void editsBeforeAnyGeneration_parkAtAwaitUser_withNoPackagesYetReport() {
        UUID token = UUID.randomUUID();
        int expectedMessageCount = 3;
        llm.queueChat(turn("Sure - what would you like?", Brief.empty(), List.of(swapFirstForSecond())));

        graph.start(token, startInputs());

        PlannerGraph.PlannerStateSnapshot snap = graph.snapshot(token);
        assertThat(snap.next()).isEqualTo(PlannerGraph.AWAIT_USER);
        assertThat(sinks.lastEditParent).isNull();
        assertThat(llm.refreshRequests).isEmpty();
        assertThat(snap.state().editReport()).hasValueSatisfying(report ->
                assertThat(report.rejected()).singleElement().satisfies(rejected ->
                        assertThat(rejected.reason()).isEqualTo(EditRejectionReason.NO_PACKAGES_YET)));
        // the opening user message, the reply, and the "let us build the packages first" note
        assertThat(snap.state().messages()).hasSize(expectedMessageCount);
    }

    @Test
    void editTurn_twice_appliesBoth_andChainsTheParentIds() {
        UUID expectedFirstParent = UUID.randomUUID();
        String expectedActivity = catalog.get(2).name();
        UUID token = threadWithPackages(expectedFirstParent);
        queueRefresh("Rebuilt around the swap");
        queueRefresh("Rebuilt again");

        editTurn(token, "swap activity 0 for activity 1", List.of(swapFirstForSecond()));
        UUID expectedSecondParent = sinks.lastEditedGeneration;
        editTurn(token, "actually make it activity 2", List.of(
                new EditRequest(EditOp.REPLACE, catalog.get(1).name(), expectedActivity, null, null, null)));

        PlannerGraph.PlannerStateSnapshot snap = graph.snapshot(token);
        assertThat(snap.next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
        // the second edit hangs off the row the first one created, not off the generation both started from
        assertThat(expectedSecondParent).isNotEqualTo(expectedFirstParent);
        assertThat(sinks.lastEditParent).isEqualTo(expectedSecondParent);
        assertThat(snap.state().generationId()).contains(sinks.lastEditedGeneration);
        assertThat(llm.planRequests).hasSize(1);
        assertThat(namesIn(snap.state().result().orElseThrow(), Tier.BASIC)).containsExactly(expectedActivity);
    }

    /**
     * The job stamps its generation id into the state before the run produces anything, so a generation
     * that dies between that stamp and persistResult - the case the STALE sweep exists for - leaves a
     * FAILED id over a state whose packages still belong to the last good row. The edit has to file under
     * <em>that</em> row: hanging it off the failed one would copy a brief snapshot from a generation that
     * never produced a plan, and name a failure as the source of the packages the group is looking at.
     */
    @Test
    void editAfterAGenerationThatNeverProducedAPlan_hangsOffTheOneThatDid() {
        UUID expectedParent = UUID.randomUUID();
        UUID failedGeneration = UUID.randomUUID();
        UUID token = threadWithPackages(expectedParent);
        // exactly what runJob writes before it resumes; this job then loses its thread and never persists
        graph.update(token, generationResume(failedGeneration));
        queueRefresh("Rebuilt around the swap");

        editTurn(token, "swap activity 0 for activity 1", List.of(swapFirstForSecond()));

        PlannerGraph.PlannerStateSnapshot snap = graph.snapshot(token);
        assertThat(snap.next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
        assertThat(sinks.lastEditParent).isEqualTo(expectedParent);
        assertThat(sinks.lastEditParent).isNotEqualTo(failedGeneration);
        assertThat(snap.state().generationId()).contains(sinks.lastEditedGeneration);
        assertThat(snap.state().resultGenerationId()).contains(sinks.lastEditedGeneration);
    }

    @Test
    void editTurn_overTheAllowance_rejectsEverythingWithoutStoringAnything() {
        UUID expectedGeneration = UUID.randomUUID();
        String expectedActivity = catalog.get(0).name();
        UUID token = threadWithPackages(expectedGeneration);
        llm.queueChat(turn("On it!", Brief.empty(), List.of(swapFirstForSecond())));

        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name(),
                PlannerState.EDITS_LEFT, 0,
                PlannerState.MESSAGES, List.of(userMessage("swap activity 0 for activity 1"))));
        graph.runUntilInterrupt(token);

        PlannerGraph.PlannerStateSnapshot snap = graph.snapshot(token);
        assertThat(snap.next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
        assertThat(sinks.lastEditParent).isNull();
        assertThat(llm.refreshRequests).isEmpty();
        assertThat(snap.state().generationId()).contains(expectedGeneration);
        assertThat(namesIn(snap.state().result().orElseThrow(), Tier.BASIC)).containsExactly(expectedActivity);
        assertThat(snap.state().editReport()).hasValueSatisfying(report ->
                assertThat(report.rejected()).singleElement().satisfies(rejected ->
                        assertThat(rejected.reason()).isEqualTo(EditRejectionReason.EDIT_LIMIT)));
    }

    @Test
    void editSaveFailure_reportsInternal_andTheNextTurnStillRuns() {
        UUID expectedGeneration = UUID.randomUUID();
        String expectedActivity = catalog.get(0).name();
        String expectedReply = "Anything else?";
        UUID token = threadWithPackages(expectedGeneration);
        queueRefresh("Rebuilt around the swap");
        sinks.nextEditFailure = new IllegalStateException("generation " + expectedGeneration + " is gone");

        editTurn(token, "swap activity 0 for activity 1", List.of(swapFirstForSecond()));

        PlannerGraph.PlannerStateSnapshot snap = graph.snapshot(token);
        assertThat(snap.next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
        assertThat(snap.state().generationId()).contains(expectedGeneration);
        assertThat(namesIn(snap.state().result().orElseThrow(), Tier.BASIC)).containsExactly(expectedActivity);
        assertThat(snap.state().editReport()).hasValueSatisfying(report ->
                assertThat(report.rejected()).singleElement().satisfies(rejected ->
                        assertThat(rejected.reason()).isEqualTo(EditRejectionReason.INTERNAL)));

        // an exception escaping the node would have left the checkpoint parked before applyEdits with the
        // batch still in state, and every later message would re-enter it: the chat has to simply carry on
        llm.queueChat(turn(expectedReply, Brief.empty()));
        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name(),
                PlannerState.MESSAGES, List.of(userMessage("never mind then"))));
        graph.runUntilInterrupt(token);

        PlannerGraph.PlannerStateSnapshot afterwards = graph.snapshot(token);
        assertThat(afterwards.next()).isEqualTo(PlannerGraph.AWAIT_USER);
        List<ChatMessage> messages = afterwards.state().messages();
        assertThat(messages.get(messages.size() - 1).content()).isEqualTo(expectedReply);
        assertThat(afterwards.state().editReport()).isEmpty();
    }

    private static ComposedPlan.PackageResult packageOf(ComposedPlan plan, Tier tier) {
        return plan.packages().stream().filter(result -> result.key() == tier).findFirst().orElseThrow();
    }

    private static List<String> namesIn(ComposedPlan plan, Tier tier) {
        return packageOf(plan, tier).days().stream().flatMap(day -> day.items().stream())
                .map(ComposedPlan.ItemResult::name).toList();
    }

    /**
     * Drives a generation into the branch and kills it there, the way a database blip in
     * {@code snapshotCatalog}, a lost job thread or a restart does. What is left behind is a
     * checkpoint whose {@code next} is a generation-branch node.
     */
    private UUID threadKilledInsideTheGenerationBranch() {
        UUID token = threadParkedAtAwaitGeneration();
        graph.update(token, generationResume(UUID.randomUUID()));
        assertThatThrownBy(() -> graph.runUntilInterrupt(token)).isInstanceOf(RuntimeException.class);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.SNAPSHOT_CATALOG);
        return token;
    }

    /**
     * The failure was transient, so resuming the dead run would succeed - which is exactly the
     * damage: the planner model runs on the caller's (HTTP) thread, {@code persistResult} stores a
     * plan for a generation the sweeper has already failed, and the user's message is never answered.
     */
    @Test
    void aGenerationThatDiedMidBranch_doesNotHijackTheNextChatTurn() {
        String expectedReply = "Sure - what would you change?";
        when(snapshotter.snapshot(any(), any(), any()))
                .thenThrow(new IllegalStateException("catalog unavailable"))
                .thenReturn(catalog);
        UUID token = threadKilledInsideTheGenerationBranch();
        llm.queuePlan(validDraft()).queueChat(turn(expectedReply, Brief.empty()));

        assertThat(graph.ensureParked(token, () -> null)).contains(PlannerGraph.SNAPSHOT_CATALOG);
        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name(),
                PlannerState.MESSAGES, List.of(userMessage("actually, hold on"))));
        graph.runUntilInterrupt(token);

        assertThat(llm.planRequests).isEmpty();
        assertThat(sinks.lastPlan).isNull();
        List<ChatMessage> messages = graph.snapshot(token).state().messages();
        assertThat(messages.get(messages.size() - 1).content()).isEqualTo(expectedReply);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);
    }

    /** The same node fails every time, so re-entering it answers every later message with a 502. */
    @Test
    void aGenerationThatDiesOnEveryAttempt_doesNotWedgeTheChat() {
        String expectedReply = "Let's talk it over first.";
        when(snapshotter.snapshot(any(), any(), any())).thenThrow(new IllegalStateException("catalog unavailable"));
        UUID token = threadKilledInsideTheGenerationBranch();
        llm.queueChat(turn(expectedReply, Brief.empty()));

        graph.ensureParked(token, () -> null);
        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name(),
                PlannerState.MESSAGES, List.of(userMessage("actually, hold on"))));
        graph.runUntilInterrupt(token);

        List<ChatMessage> messages = graph.snapshot(token).state().messages();
        assertThat(messages.get(messages.size() - 1).content()).isEqualTo(expectedReply);
    }

    /** Parks a thread on three finished packages, then kills a regeneration inside the branch. */
    private UUID threadWithPackagesAndARegenerationKilledMidBranch(UUID deadGenerationId) {
        UUID token = UUID.randomUUID();
        llm.queueChat(turn("go", readyBrief())).queuePlan(validDraft());
        graph.start(token, startInputs());
        graph.update(token, generationResume(UUID.randomUUID()));
        graph.runUntilInterrupt(token);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);

        when(snapshotter.snapshot(any(), any(), any())).thenThrow(new IllegalStateException("catalog unavailable"));
        graph.update(token, generationResume(deadGenerationId));
        // awaitSelection -> awaitGeneration first; only the next resume enters the branch and dies
        graph.runUntilInterrupt(token);
        assertThatThrownBy(() -> graph.runUntilInterrupt(token)).isInstanceOf(RuntimeException.class);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.SNAPSHOT_CATALOG);
        return token;
    }

    /** The conversation and the packages an earlier generation delivered are not part of the dead run. */
    @Test
    void reParkingADeadRun_keepsTheConversationAndThePackagesItAlreadyDelivered() {
        UUID expectedGenerationId = UUID.randomUUID();
        UUID token = threadWithPackagesAndARegenerationKilledMidBranch(expectedGenerationId);
        int expectedMessages = graph.snapshot(token).state().messages().size();

        graph.ensureParked(token, () -> null);

        PlannerGraph.PlannerStateSnapshot snap = graph.snapshot(token);
        assertThat(snap.next()).isEqualTo(PlannerGraph.AWAIT_USER);
        assertThat(snap.state().messages()).hasSize(expectedMessages);
        assertThat(snap.state().brief().isReady()).isTrue();
        assertThat(snap.state().result()).isPresent();
        assertThat(snap.state().generationId()).contains(expectedGenerationId);
        // the dead attempt's own scribbles are gone
        assertThat(snap.state().action()).isEqualTo(PlannerState.ACTION_NONE);
        assertThat(snap.state().resumeReason()).isEmpty();
        assertThat(snap.state().violations()).isEmpty();
        assertThat(snap.state().draft()).isEmpty();
        assertThat(snap.state().attempt()).isEqualTo(0);
    }

    /**
     * snapshotCatalog stamps LAST_GENERATED_BRIEF before the packages exist, so a run that dies after
     * it leaves the chat believing it has already built this brief - it would never regenerate again.
     * Re-parking restores the brief of the generation that really delivered, so small talk still costs
     * nothing and a changed brief still rebuilds.
     */
    @Test
    void reParkingADeadRun_restoresTheBriefOfTheLastGenerationThatDelivered() {
        String expectedDeliveredBrief = JsonCodec.write(readyBrief());
        UUID token = threadWithPackagesAndARegenerationKilledMidBranch(UUID.randomUUID());

        graph.ensureParked(token, () -> expectedDeliveredBrief);

        assertThat(graph.snapshot(token).state().lastGeneratedBrief()).contains(expectedDeliveredBrief);
        // small talk after the recovery: the brief is unchanged, so nothing is rebuilt
        llm.queueChat(turn("Glad you like it!", Brief.empty()));
        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name(),
                PlannerState.MESSAGES, List.of(userMessage("nice"))));
        graph.runUntilInterrupt(token);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_USER);
        assertThat(llm.planRequests).hasSize(1);
    }

    /**
     * The recovery a job drives: re-park, then the GENERATE hop from awaitUser to awaitGeneration -
     * which executes nothing but the park nodes - and only then the generation itself.
     */
    @Test
    void aReParkedThread_isWalkedToAwaitGenerationAndThenGenerates() {
        UUID expectedGenerationId = UUID.randomUUID();
        when(snapshotter.snapshot(any(), any(), any()))
                .thenThrow(new IllegalStateException("catalog unavailable"))
                .thenReturn(catalog);
        UUID token = threadKilledInsideTheGenerationBranch();
        int expectedChatTurns = llm.chatRequests.size();
        graph.ensureParked(token, () -> null);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_USER);

        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.GENERATE.name()));
        graph.runUntilInterrupt(token);

        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);
        assertThat(llm.chatRequests).hasSize(expectedChatTurns);
        assertThat(llm.planRequests).isEmpty();

        llm.queuePlan(validDraft());
        graph.update(token, generationResume(expectedGenerationId));
        graph.runUntilInterrupt(token);

        assertThat(sinks.lastGeneration).isEqualTo(expectedGenerationId);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
    }

    /**
     * The sweeper closed the row out while this run was still building. Nobody will ever show the
     * plan, so the chat must not be left believing it has packages for this brief.
     */
    @Test
    void aPlanTheSinkRefuses_leavesTheChatFreeToRebuild() {
        UUID token = UUID.randomUUID();
        llm.queueChat(turn("go", readyBrief())).queuePlan(validDraft(), validDraft());
        sinks.storesPlans = false;
        graph.start(token, startInputs());
        graph.update(token, generationResume(UUID.randomUUID()));
        graph.runUntilInterrupt(token);

        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
        assertThat(graph.snapshot(token).state().lastGeneratedBrief()).isEmpty();

        // the next turn changes nothing about the brief, and still rebuilds: there is nothing to show
        llm.queueChat(turn("Still here?", Brief.empty()));
        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name(),
                PlannerState.MESSAGES, List.of(userMessage("where are my packages?"))));
        graph.runUntilInterrupt(token);

        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);
    }

    /** A thread waiting at any of the three park points is somebody's live conversation: never touch it. */
    @Test
    void ensureParked_leavesAThreadThatIsAlreadyWaitingAlone() {
        UUID token = threadParkedAtAwaitGeneration();

        assertThat(graph.ensureParked(token, () -> null)).isEmpty();

        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);
        assertThat(graph.ensureParked(UUID.randomUUID(), () -> null)).isEmpty();
    }

    /**
     * The checkpoint a thread that never reached {@code applyEdits} leaves behind: chatTurn's own
     * update replayed through its router, which is exactly what langgraph4j writes before the edit
     * node runs. The report stands in for anything an earlier turn left in state.
     */
    private void killOnTheWayToApplyEdits(UUID token) throws Exception {
        graph.compiled().updateState(graph.configFor(token), Map.of(
                PlannerState.ACTION, PlannerState.ACTION_EDIT,
                PlannerState.EDITS, JsonCodec.write(List.of(swapFirstForSecond())),
                PlannerState.EDIT_REPORT, JsonCodec.write(
                        EditReport.allRejected(List.of(swapFirstForSecond()), EditRejectionReason.INTERNAL))),
                PlannerGraph.CHAT_TURN);
        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.APPLY_EDITS);
    }

    /**
     * applyEdits is a request-thread node, not a park point, so a turn lost between chatTurn and the
     * edit node is a dead run like any other: it is re-parked at awaitUser. Its batch dies with it -
     * the request that carried it answered 502 long ago, and applying it behind the group's back a
     * turn later would change packages nobody asked to change again.
     */
    @Test
    void reParkingAThreadThatDiedOnItsWayToApplyEdits_retiresTheBatch() throws Exception {
        String expectedActivity = catalog.get(0).name();
        String expectedReply = "Sure, tell me more.";
        UUID token = threadWithPackages(UUID.randomUUID());
        killOnTheWayToApplyEdits(token);

        assertThat(graph.ensureParked(token, () -> JsonCodec.write(readyBrief())))
                .contains(PlannerGraph.APPLY_EDITS);

        PlannerGraph.PlannerStateSnapshot reparked = graph.snapshot(token);
        assertThat(reparked.next()).isEqualTo(PlannerGraph.AWAIT_USER);
        assertThat(reparked.state().action()).isEqualTo(PlannerState.ACTION_NONE);
        assertThat(reparked.state().edits()).isEmpty();
        assertThat(reparked.state().editReport()).isEmpty();

        // the next turn asks for nothing, so the retired batch must not ride along with it
        llm.queueChat(turn(expectedReply, Brief.empty()));
        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name(),
                PlannerState.MESSAGES, List.of(userMessage("never mind then"))));
        graph.runUntilInterrupt(token);

        PlannerGraph.PlannerStateSnapshot afterwards = graph.snapshot(token);
        assertThat(afterwards.next()).isEqualTo(PlannerGraph.AWAIT_USER);
        assertThat(sinks.lastEditParent).isNull();
        assertThat(llm.refreshRequests).isEmpty();
        assertThat(namesIn(afterwards.state().result().orElseThrow(), Tier.BASIC))
                .containsExactly(expectedActivity);
        List<ChatMessage> messages = afterwards.state().messages();
        assertThat(messages.get(messages.size() - 1).content()).isEqualTo(expectedReply);
    }

    @Test
    void seedParked_createsThreadWaitingForUserWithoutCallingTheModel() {
        UUID token = UUID.randomUUID();
        String expectedDestinationName = "Prague";

        graph.seedParked(token, Map.of(
                PlannerState.LOCALE, "en",
                PlannerState.DESTINATION_ID, UUID.randomUUID().toString(),
                PlannerState.DESTINATION_NAME, expectedDestinationName,
                PlannerState.CATEGORY_SLUGS, List.of("nightlife"),
                PlannerState.BRIEF, JsonCodec.write(Brief.empty())));

        PlannerGraph.PlannerStateSnapshot snap = graph.snapshot(token);
        assertThat(snap.next()).isEqualTo(PlannerGraph.AWAIT_USER);
        assertThat(snap.state().destinationName()).isEqualTo(expectedDestinationName);
        assertThat(snap.state().messages()).isEmpty();
        assertThat(llm.chatRequests).isEmpty();
    }
}
