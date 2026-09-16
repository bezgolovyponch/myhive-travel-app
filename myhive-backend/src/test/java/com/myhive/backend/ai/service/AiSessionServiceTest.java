package com.myhive.backend.ai.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.ai.catalog.CatalogSnapshotter;
import com.myhive.backend.ai.exception.AiConflictException;
import com.myhive.backend.ai.exception.AiDisabledException;
import com.myhive.backend.ai.exception.AiLimitException;
import com.myhive.backend.ai.exception.AiNotFoundException;
import com.myhive.backend.ai.exception.LlmCallFailedException;
import com.myhive.backend.ai.exception.TurnstileFailedException;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerGraph;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.graph.TestPlannerGraphs;
import com.myhive.backend.ai.llm.AiProperties;
import com.myhive.backend.ai.llm.ChatMessage;
import com.myhive.backend.ai.llm.ChatTurnResult;
import com.myhive.backend.ai.llm.FakeLlmGateway;
import com.myhive.backend.ai.llm.LlmUnavailableException;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.entity.AiGeneration;
import com.myhive.backend.entity.AiGenerationStatus;
import com.myhive.backend.entity.AiSession;
import com.myhive.backend.entity.AiSessionStatus;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.repository.AiGenerationRepository;
import com.myhive.backend.repository.AiSessionRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.service.TurnstileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSessionServiceTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final AiSessionRepository sessionRepository = mock(AiSessionRepository.class);
    private final AiGenerationRepository generationRepository = mock(AiGenerationRepository.class);
    private final DestinationRepository destinationRepository = mock(DestinationRepository.class);
    private final TurnstileService turnstile = mock(TurnstileService.class);
    private final AiProperties props = new AiProperties();
    /** Collects the jobs {@code enqueue} submits instead of running them; the graph stays parked. */
    private final List<Runnable> submittedJobs = new ArrayList<>();
    private final Executor executor = submittedJobs::add;
    private PlannerGraph graph;
    private PlanGenerationService generationService;
    private AiSessionService service;
    private Destination destination;

    @BeforeEach
    void setUp() {
        props.setEnabled(true);
        // The sinks are looked up lazily on purpose: in the application the graph and the generation
        // service depend on each other, and the ObjectProvider breaks the cycle the same way.
        graph = TestPlannerGraphs.inMemory(llm, mock(CatalogSnapshotter.class),
                (generationId, plan, degraded, usage, attempt) ->
                        generationService.ready(generationId, plan, degraded, usage, attempt),
                (generationId, key) -> generationService.selected(generationId, key));
        generationService = new PlanGenerationService(generationRepository, sessionRepository, graph, executor);
        service = new AiSessionService(props, graph, sessionRepository, generationRepository, destinationRepository,
                generationService, turnstile, new SessionLocks(), new DailySessionCap(props),
                new ClientIpHasher("salt"));
        destination = TestDataFactory.destination("Prague");
        destination.setId(UUID.randomUUID());
        destination.setSlug("prague");
        when(destinationRepository.findBySlugWithCategories("prague")).thenReturn(Optional.of(destination));
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            AiSession saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(generationRepository.save(any())).thenAnswer(inv -> {
            AiGeneration saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }

    private static ChatTurnResult turn(String reply, Brief update) {
        return new ChatTurnResult(reply, update, List.of(), LlmUsage.none());
    }

    private static Brief readyBrief() {
        return new Brief(2, 6, List.of("nightlife"), null, null, null, DayEdge.EVENING, DayEdge.MORNING, null);
    }

    private AiSession startedSession() {
        AiSession session = service.create("prague", "en", null, null, "1.2.3.4").session();
        when(sessionRepository.findByToken(session.getToken())).thenReturn(Optional.of(session));
        return session;
    }

    @Test
    void create_seedsGraphWithGreeting_andReturnsCollectingState() {
        AiSessionService.SessionView view = service.create("prague", "en", null, null, "1.2.3.4");

        assertThat(view.session().getStatus()).isEqualTo(AiSessionStatus.COLLECTING);
        assertThat(view.state().messages()).hasSize(1);
        assertThat(view.state().messages().get(0).role()).isEqualTo(ChatMessage.ASSISTANT);
        assertThat(view.next()).isEqualTo(PlannerGraph.AWAIT_USER);
        assertThat(view.latest()).isEmpty();
        assertThat(llm.chatRequests).isEmpty();
    }

    @Test
    void create_withInitialMessage_runsOneTurn() {
        String expectedLocale = "de";
        llm.queueChat(turn("How many days?", Brief.empty()));

        AiSessionService.SessionView view = service.create("prague", expectedLocale, null, "wir sind 8", "1.2.3.4");

        List<String> roles = view.state().messages().stream().map(ChatMessage::role).toList();
        assertThat(roles).containsExactly(ChatMessage.ASSISTANT, ChatMessage.USER, ChatMessage.ASSISTANT);
        assertThat(llm.chatRequests.get(0).locale()).isEqualTo(expectedLocale);
        assertThat(view.session().getLocale()).isEqualTo(expectedLocale);
    }

    @Test
    void create_whenDisabled_throws503() {
        props.setEnabled(false);

        assertThatThrownBy(() -> service.create("prague", "en", null, null, "1.2.3.4"))
                .isInstanceOf(AiDisabledException.class);
    }

    @Test
    void create_requiresTurnstileWhenConfigured() {
        props.setTurnstileRequired(true);
        when(turnstile.verifyToken(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.create("prague", "en", "bad", null, "1.2.3.4"))
                .isInstanceOf(TurnstileFailedException.class);
        assertThatThrownBy(() -> service.create("prague", "en", null, null, "1.2.3.4"))
                .isInstanceOf(TurnstileFailedException.class);
    }

    @Test
    void create_overDailyCap_isRejected() {
        props.setDailySessionsPerIp(1);
        service.create("prague", "en", null, null, "1.2.3.4");

        assertThatThrownBy(() -> service.create("prague", "en", null, null, "1.2.3.4"))
                .isInstanceOf(AiLimitException.class).hasFieldOrPropertyWithValue("code", "SESSION_DAILY_LIMIT");
    }

    @Test
    void create_withUnknownDestination_isBadRequest() {
        assertThatThrownBy(() -> service.create("atlantis", "en", null, null, "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void message_appendsUserMessage_runsTurn_andStartsGenerationWhenBriefBecomesReady() {
        AiSession session = startedSession();
        int expectedMessageCount = 1;
        llm.queueChat(turn("On it!", readyBrief()));

        AiSessionService.TurnOutcome outcome = service.message(session.getToken(), "2 days, 6 of us, bars");

        UUID expectedGenerationId = outcome.startedGeneration().orElseThrow().getId();
        assertThat(outcome.view().next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);
        assertThat(session.getMessageCount()).isEqualTo(expectedMessageCount);
        assertThat(session.getStatus()).isEqualTo(AiSessionStatus.GENERATING);
        assertThat(graph.snapshot(session.getToken()).state().generationId()).contains(expectedGenerationId);
        assertThat(submittedJobs).hasSize(1);
    }

    @Test
    void message_onUnknownToken_isNotFound() {
        assertThatThrownBy(() -> service.message(UUID.randomUUID(), "hello"))
                .isInstanceOf(AiNotFoundException.class).hasFieldOrPropertyWithValue("code", "SESSION_NOT_FOUND");
    }

    @Test
    void message_overTurnLimit_isRejected() {
        AiSession session = startedSession();
        session.setMessageCount(AiSessionService.MAX_MESSAGES);

        assertThatThrownBy(() -> service.message(session.getToken(), "more"))
                .isInstanceOf(AiLimitException.class).hasFieldOrPropertyWithValue("code", "SESSION_TURN_LIMIT");
    }

    @Test
    void message_whileAGenerationRuns_isConflict() {
        AiSession session = startedSession();
        when(generationRepository.existsBySessionIdAndStatusIn(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.message(session.getToken(), "are we there yet"))
                .isInstanceOf(AiConflictException.class)
                .hasFieldOrPropertyWithValue("code", "GENERATION_IN_PROGRESS");
    }

    @Test
    void message_whenTheModelIsUnreachable_isMappedToLlmCallFailed() {
        AiSession session = startedSession();
        // nothing queued on the fake gateway: chatTurn blows up inside the graph

        assertThatThrownBy(() -> service.message(session.getToken(), "hello"))
                .isInstanceOf(LlmCallFailedException.class)
                .hasFieldOrPropertyWithValue("code", "LLM_UNAVAILABLE");
    }

    @Test
    void message_identicalToLastStoredUserMessage_isNotAppendedTwice() {
        AiSession session = startedSession();
        String repeated = "same text";
        int expectedUserMessages = 2;
        llm.queueChat(turn("a", Brief.empty()), turn("b", Brief.empty()));
        service.message(session.getToken(), repeated);
        graph.update(session.getToken(), Map.of(PlannerState.MESSAGES,
                List.of(Map.of("role", ChatMessage.USER, "content", repeated, "at", "t"))));

        service.message(session.getToken(), repeated);

        long userMessages = graph.snapshot(session.getToken()).state().messages().stream()
                .filter(m -> ChatMessage.USER.equals(m.role())).count();
        assertThat(userMessages).isEqualTo(expectedUserMessages);
    }

    @Test
    void requestGeneration_beforeBriefReady_isConflict() {
        AiSession session = startedSession();

        assertThatThrownBy(() -> service.requestGeneration(session.getToken()))
                .isInstanceOf(AiConflictException.class).hasFieldOrPropertyWithValue("code", "BRIEF_INCOMPLETE");
    }

    @Test
    void requestGeneration_whileOneIsRunning_isConflict() {
        AiSession session = startedSession();
        graph.update(session.getToken(), Map.of(PlannerState.BRIEF, JsonCodec.write(readyBrief())));
        when(generationRepository.existsBySessionIdAndStatusIn(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.requestGeneration(session.getToken()))
                .isInstanceOf(AiConflictException.class)
                .hasFieldOrPropertyWithValue("code", "GENERATION_IN_PROGRESS");
    }

    @Test
    void requestGeneration_overGenerationLimit_isRejected() {
        AiSession session = startedSession();
        session.setGenerationCount(AiSessionService.MAX_GENERATIONS);
        graph.update(session.getToken(), Map.of(PlannerState.BRIEF, JsonCodec.write(readyBrief())));

        assertThatThrownBy(() -> service.requestGeneration(session.getToken()))
                .isInstanceOf(AiLimitException.class).hasFieldOrPropertyWithValue("code", "GENERATION_LIMIT");
    }

    @Test
    void requestGeneration_parksAtAwaitGeneration_andStampsTheGenerationIdBeforeTheJobIsQueued() {
        AiSession session = startedSession();
        graph.update(session.getToken(), Map.of(PlannerState.BRIEF, JsonCodec.write(readyBrief())));

        AiGeneration generation = service.requestGeneration(session.getToken());

        assertThat(generation.getStatus()).isEqualTo(AiGenerationStatus.QUEUED);
        assertThat(graph.snapshot(session.getToken()).next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);
        assertThat(graph.snapshot(session.getToken()).state().generationId()).contains(generation.getId());
        assertThat(session.getGenerationCount()).isEqualTo(1);
        assertThat(llm.chatRequests).isEmpty();
    }

    @Test
    void get_returnsTheLatestGeneration() {
        AiSession session = startedSession();
        AiGeneration expectedLatest = new AiGeneration();
        expectedLatest.setId(UUID.randomUUID());
        when(generationRepository.findFirstBySessionIdOrderByCreatedAtDesc(session.getId()))
                .thenReturn(Optional.of(expectedLatest));

        AiSessionService.SessionView view = service.get(session.getToken());

        assertThat(view.latest()).contains(expectedLatest);
        assertThat(view.next()).isEqualTo(PlannerGraph.AWAIT_USER);
    }

    /** A READY generation whose stored plan holds one package of the given tier. */
    private AiGeneration readyGeneration(AiSession session, Tier key, UUID activityId) {
        ComposedPlan plan = new ComposedPlan(List.of(new ComposedPlan.PackageResult(key, "Night out", "tag", "desc",
                new BigDecimal("80.00"), new BigDecimal("480.00"), ComposedPlan.CURRENCY, 240,
                List.of(activityId), List.of())), false);
        AiGeneration generation = new AiGeneration();
        generation.setId(UUID.randomUUID());
        generation.setSession(session);
        generation.setStatus(AiGenerationStatus.READY);
        generation.setBriefSnapshot(JsonCodec.write(readyBrief()));
        generation.setResult(JsonCodec.write(plan));
        when(generationRepository.findWithSessionById(generation.getId())).thenReturn(Optional.of(generation));
        when(generationRepository.findById(generation.getId())).thenReturn(Optional.of(generation));
        return generation;
    }

    @Test
    void select_recordsThePick_andResolvesTheActivityIds() {
        AiSession session = startedSession();
        Tier expectedKey = Tier.MEDIUM;
        UUID expectedActivityId = UUID.randomUUID();
        int expectedGroupSize = readyBrief().groupSize();
        AiGeneration generation = readyGeneration(session, expectedKey, expectedActivityId);

        AiSessionService.Selection selection = service.select(generation.getId(), expectedKey);

        assertThat(selection.key()).isEqualTo(expectedKey);
        assertThat(selection.groupSize()).isEqualTo(expectedGroupSize);
        assertThat(selection.activityIds()).containsExactly(expectedActivityId);
        // the pick lands even though the thread was parked at awaitUser, not at awaitSelection
        assertThat(generation.getSelectedPackageKey()).isEqualTo(expectedKey.name());
        assertThat(graph.snapshot(session.getToken()).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
        assertThat(llm.chatRequests).isEmpty();
    }

    @Test
    void select_beforeThePackagesAreReady_isConflict() {
        AiSession session = startedSession();
        AiGeneration generation = readyGeneration(session, Tier.BASIC, UUID.randomUUID());
        generation.setStatus(AiGenerationStatus.QUEUED);

        assertThatThrownBy(() -> service.select(generation.getId(), Tier.BASIC))
                .isInstanceOf(AiConflictException.class)
                .hasFieldOrPropertyWithValue("code", "GENERATION_NOT_READY");
    }

    @Test
    void select_onUnknownGeneration_isNotFound() {
        assertThatThrownBy(() -> service.select(UUID.randomUUID(), Tier.BASIC))
                .isInstanceOf(AiNotFoundException.class).hasFieldOrPropertyWithValue("code", "GENERATION_NOT_FOUND");
    }

    @Test
    void errorCodeOf_recognisesATimeoutAnywhereInTheCauseChain() {
        String expectedTimeoutCode = "LLM_TIMEOUT";
        RuntimeException wrapped = new IllegalStateException("node failed",
                new LlmUnavailableException("model call timed out after PT20S", new RuntimeException("socket")));

        assertThat(AiSessionService.errorCodeOf(wrapped)).isEqualTo(expectedTimeoutCode);
        assertThat(AiSessionService.errorCodeOf(new IllegalStateException("boom"))).isEqualTo("LLM_UNAVAILABLE");
    }
}
