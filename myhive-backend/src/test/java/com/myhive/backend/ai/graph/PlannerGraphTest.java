package com.myhive.backend.ai.graph;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogSnapshotter;
import com.myhive.backend.ai.graph.nodes.PersistResultNode;
import com.myhive.backend.ai.graph.nodes.SelectNode;
import com.myhive.backend.ai.llm.ChatTurnResult;
import com.myhive.backend.ai.llm.FakeLlmGateway;
import com.myhive.backend.ai.llm.LlmUsage;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlannerGraphTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final CatalogSnapshotter snapshotter = mock(CatalogSnapshotter.class);
    private final RecordingSinks sinks = new RecordingSinks();
    private final List<CatalogActivity> catalog = new ArrayList<>();
    private PlannerGraph graph;

    static class RecordingSinks implements PersistResultNode.GenerationResultSink, SelectNode.SelectionSink {
        private ComposedPlan lastPlan;
        private boolean lastDegraded;
        private UUID lastGeneration;
        private Tier lastSelected;

        @Override
        public void ready(UUID generationId, ComposedPlan plan, boolean degraded, LlmUsage usage, int attempt) {
            lastGeneration = generationId;
            lastPlan = plan;
            lastDegraded = degraded;
        }

        @Override
        public void selected(UUID generationId, Tier key) {
            lastSelected = key;
        }
    }

    @BeforeEach
    void setUp() {
        for (int i = 0; i < 6; i++) {
            catalog.add(new CatalogActivity(UUID.randomUUID(), "a" + i, "Activity " + i, "line", 90, true,
                    new BigDecimal(20 + i * 10), null, null, List.of("nightlife")));
        }
        when(snapshotter.snapshot(any(), any(), any())).thenReturn(catalog);
        graph = TestPlannerGraphs.inMemory(llm, snapshotter, sinks, sinks);
    }

    private static ChatTurnResult turn(String reply, Brief update) {
        return new ChatTurnResult(reply, update, List.of(), LlmUsage.none());
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

        graph.update(token, Map.of(PlannerState.GENERATION_ID, expectedGenerationId.toString()));
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
        graph.update(token, Map.of(PlannerState.GENERATION_ID, UUID.randomUUID().toString()));

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
        graph.update(token, Map.of(PlannerState.GENERATION_ID, UUID.randomUUID().toString()));

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
        graph.update(token, Map.of(PlannerState.GENERATION_ID, UUID.randomUUID().toString()));
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
        graph.update(token, Map.of(PlannerState.GENERATION_ID, UUID.randomUUID().toString()));
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
        graph.update(token, Map.of(PlannerState.GENERATION_ID, UUID.randomUUID().toString()));
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
        graph.update(token, Map.of(PlannerState.GENERATION_ID, UUID.randomUUID().toString()));

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
        graph.runUntilInterrupt(token);

        assertThat(graph.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
        assertThat(sinks.lastPlan).isNull();
        assertThat(sinks.lastGeneration).isNull();
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
