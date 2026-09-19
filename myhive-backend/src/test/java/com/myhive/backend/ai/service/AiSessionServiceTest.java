package com.myhive.backend.ai.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogSnapshotter;
import com.myhive.backend.ai.dto.AiDtoMapper;
import com.myhive.backend.ai.edit.EditMessages;
import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.edit.EditRejectionReason;
import com.myhive.backend.ai.edit.EditReport;
import com.myhive.backend.ai.edit.EditRequest;
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
import com.myhive.backend.ai.llm.PackageTexts;
import com.myhive.backend.ai.llm.TextRefreshResult;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.entity.AiGeneration;
import com.myhive.backend.entity.AiGenerationKind;
import com.myhive.backend.entity.AiGenerationStatus;
import com.myhive.backend.entity.AiSession;
import com.myhive.backend.entity.AiSessionStatus;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.AiGenerationRepository;
import com.myhive.backend.repository.AiSessionRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.service.TurnstileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiSessionServiceTest {

    /** Short enough that a swap always fits the day's remaining minutes, whatever the tier's cap. */
    private static final int EDITED_DURATION_MINUTES = 90;

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final AiSessionRepository sessionRepository = mock(AiSessionRepository.class);
    private final AiGenerationRepository generationRepository = mock(AiGenerationRepository.class);
    private final DestinationRepository destinationRepository = mock(DestinationRepository.class);
    private final TurnstileService turnstile = mock(TurnstileService.class);
    private final AiProperties props = new AiProperties();
    /** Only for the limit test: the number the UI is shown has to come out of the real mapping. */
    private final AiDtoMapper mapper = new AiDtoMapper(mock(ActivityRepository.class));
    /** Collects the jobs {@code enqueue} submits instead of running them; the graph stays parked. */
    private final List<Runnable> submittedJobs = new ArrayList<>();
    /** Records the order of the calls a turn makes, to pin what happens before the job is submitted. */
    private final List<String> callOrder = new ArrayList<>();
    private boolean executorRejects = false;
    private final Executor executor = job -> {
        callOrder.add("submit");
        if (executorRejects) {
            throw new RejectedExecutionException();
        }
        submittedJobs.add(job);
    };
    private final List<AiGeneration> savedGenerations = new ArrayList<>();
    /** The edit budget as it stood at every session save; a turn spends it before its single save. */
    private final List<Integer> editCountsWhenSaved = new ArrayList<>();
    @SuppressWarnings("unchecked")
    private final ObjectProvider<PlanGenerationService> generationServiceSelf = mock(ObjectProvider.class);
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
                (generationId, key) -> generationService.selected(generationId, key),
                (parentId, plan, report, usage) -> generationService.edited(parentId, plan, report, usage));
        generationService = new PlanGenerationService(generationRepository, sessionRepository, graph, executor,
                generationServiceSelf);
        when(generationServiceSelf.getObject()).thenReturn(generationService);
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
            // Read at save time, not afterwards: a turn saves the session exactly once, so an edit that
            // landed has to have been counted by then or the budget is handed back for free.
            editCountsWhenSaved.add(saved.getEditCount());
            return saved;
        });
        when(generationRepository.save(any())).thenAnswer(inv -> {
            AiGeneration saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            savedGenerations.add(saved);
            return saved;
        });
        // view() is the last read a turn makes before it may submit a job, so it marks the boundary.
        when(generationRepository.findFirstBySessionIdOrderByCreatedAtDescIdDesc(any())).thenAnswer(inv -> {
            callOrder.add("view");
            return Optional.empty();
        });
    }

    private static ChatTurnResult turn(String reply, Brief update) {
        return new ChatTurnResult(reply, update, List.of(), LlmUsage.none());
    }

    private static Brief readyBrief() {
        return new Brief(2, 6, List.of("nightlife"), null, null, null, DayEdge.EVENING, DayEdge.MORNING, null);
    }

    /** One day, arriving in the afternoon and leaving in the evening: two slots, so an edit has room. */
    private static Brief editBrief() {
        return new Brief(1, 4, List.of("nightlife"), null, null, null, DayEdge.AFTERNOON, DayEdge.EVENING, null);
    }

    private static ChatTurnResult editTurn(String reply, String activity, String replacement) {
        return new ChatTurnResult(reply, Brief.empty(), List.of(),
                List.of(new EditRequest(EditOp.REPLACE, activity, replacement, null, null, null)), LlmUsage.none());
    }

    private static TextRefreshResult refreshedTexts(String description) {
        return new TextRefreshResult(Map.of(Tier.BASIC, new PackageTexts(description, Map.of(), Map.of())),
                new LlmUsage("fake-chat", 5, 7, 3L));
    }

    private static CatalogActivity catalogActivity(String name, String slug) {
        return new CatalogActivity(UUID.randomUUID(), slug, name, "one line", EDITED_DURATION_MINUTES, true,
                new BigDecimal("40.00"), null, "https://example.com/" + slug + ".jpg", List.of("nightlife"));
    }

    /** One BASIC package holding that activity on day 1, priced exactly the way the assembler would. */
    private static ComposedPlan planWith(CatalogActivity activity) {
        BigDecimal lineTotal = new BigDecimal("160.00");
        ComposedPlan.ItemResult item = new ComposedPlan.ItemResult(Slot.AFTERNOON, null, activity.id(),
                activity.slug(), activity.name(), activity.imageUrl(), activity.durationMinutes(), activity.price(),
                null, lineTotal, false, "why");
        ComposedPlan.DayResult day = new ComposedPlan.DayResult(1, "Day one", "summary", List.of(item));
        return new ComposedPlan(List.of(new ComposedPlan.PackageResult(Tier.BASIC, "Night out", "tag", "desc",
                activity.price(), lineTotal, ComposedPlan.CURRENCY, EDITED_DURATION_MINUTES,
                List.of(activity.id()), List.of(day))), false);
    }

    /**
     * A thread parked the way a finished generation leaves it, without running one: this class mocks the
     * catalog snapshotter away, so the packages, the catalog and the parent id are written straight into
     * the state. {@code LAST_GENERATED_BRIEF} matches the brief on purpose — a brief that looks changed
     * makes the chat turn regenerate, which drops the edits by design.
     */
    private void parkWithPackages(AiSession session, List<CatalogActivity> catalog, ComposedPlan plan,
            UUID parentGenerationId) {
        parkWithPackages(session, editBrief(), catalog, plan, parentGenerationId);
    }

    private void parkWithPackages(AiSession session, Brief brief, List<CatalogActivity> catalog, ComposedPlan plan,
            UUID parentGenerationId) {
        String briefJson = JsonCodec.write(brief);
        graph.update(session.getToken(), Map.of(
                PlannerState.BRIEF, briefJson,
                PlannerState.LAST_GENERATED_BRIEF, briefJson,
                PlannerState.CATALOG, JsonCodec.write(catalog),
                PlannerState.RESULT, JsonCodec.write(plan),
                PlannerState.GENERATION_ID, parentGenerationId.toString(),
                PlannerState.RESULT_GENERATION_ID, parentGenerationId.toString()));
    }

    /**
     * The GENERATED row an edit hangs off; {@code edited(...)} re-reads it to copy the brief snapshot, and
     * a selection re-reads its stored plan. It is put in {@code savedGenerations} so the lookups
     * {@link #answerEditedGenerationLookups()} installs can find it alongside the rows an edit creates.
     */
    private AiGeneration storedParent(AiSession session, ComposedPlan plan) {
        AiGeneration parent = new AiGeneration();
        parent.setId(UUID.randomUUID());
        parent.setSession(session);
        parent.setStatus(AiGenerationStatus.READY);
        parent.setBriefSnapshot(JsonCodec.write(editBrief()));
        parent.setResult(JsonCodec.write(plan));
        savedGenerations.add(parent);
        when(generationRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        return parent;
    }

    /** The edited row only exists once the sink has run, so the lookup has to answer from what was saved. */
    private void answerEditedGenerationLookups() {
        when(generationRepository.findWithSessionById(any())).thenAnswer(inv -> savedGenerations.stream()
                .filter(saved -> saved.getId().equals(inv.getArgument(0)))
                .findFirst());
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

    /**
     * The row, the checkpoint thread and one of the caller's twenty daily chats are spent by the time
     * the model call fails, and the documented retry needs the token a bodiless 502 never hands out.
     */
    @Test
    void create_withInitialMessage_whenTheModelIsUnreachable_stillReturnsTheSeededSession() {
        String expectedErrorCode = "LLM_UNAVAILABLE";
        // nothing queued on the fake gateway: chatTurn blows up inside the graph

        AiSessionService.SessionView view = service.create("prague", "en", null, "wir sind 8", "1.2.3.4");

        assertThat(view.firstTurnErrorCode()).isEqualTo(expectedErrorCode);
        assertThat(view.session().getStatus()).isEqualTo(AiSessionStatus.COLLECTING);
        // The greeting plus the user's own message: re-sending the same text is de-duped, not doubled.
        assertThat(view.state().messages().stream().map(ChatMessage::role).toList())
                .containsExactly(ChatMessage.ASSISTANT, ChatMessage.USER);
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
    void create_withUnknownDestination_isBadRequest_andCostsNoDailySlot() {
        props.setDailySessionsPerIp(1);

        assertThatThrownBy(() -> service.create("atlantis", "en", null, null, "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);

        // a typo in the slug must not spend one of the caller's chats for the day
        assertThatCode(() -> service.create("prague", "en", null, null, "1.2.3.4")).doesNotThrowAnyException();
    }

    @Test
    void message_thatHitsTheGenerationLimit_stillCountsTheTurn() {
        AiSession session = startedSession();
        int expectedMessageCount = 1;
        session.setGenerationCount(AiSessionService.MAX_GENERATIONS);
        llm.queueChat(turn("On it!", readyBrief()));
        clearInvocations(sessionRepository);

        assertThatThrownBy(() -> service.message(session.getToken(), "2 days, 6 of us, bars"))
                .isInstanceOf(AiLimitException.class).hasFieldOrPropertyWithValue("code", "GENERATION_LIMIT");

        // the model already answered, so the turn is spent whether or not the generation was allowed
        assertThat(session.getMessageCount()).isEqualTo(expectedMessageCount);
        verify(sessionRepository).save(session);
    }

    @Test
    void message_appendsUserMessage_runsTurn_andStartsGenerationWhenBriefBecomesReady() {
        AiSession session = startedSession();
        int expectedMessageCount = 1;
        llm.queueChat(turn("On it!", readyBrief()));

        AiSessionService.TurnOutcome outcome = service.message(session.getToken(), "2 days, 6 of us, bars");

        AiGeneration expectedGeneration = outcome.startedGeneration().orElseThrow();
        assertThat(outcome.view().next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);
        assertThat(session.getMessageCount()).isEqualTo(expectedMessageCount);
        assertThat(session.getStatus()).isEqualTo(AiSessionStatus.GENERATING);
        assertThat(expectedGeneration.getStatus()).isEqualTo(AiGenerationStatus.QUEUED);
        // The id is stamped by the job, right before it resumes the thread; enqueue never writes to
        // the graph, so a graph failure cannot strand a committed QUEUED row.
        assertThat(graph.snapshot(session.getToken()).state().generationId()).isEmpty();
        assertThat(submittedJobs).hasSize(1);
    }

    @Test
    void message_afterARejectedGeneration_isAnsweredInChatInsteadOfBuildingAPlanInline() {
        AiSession session = startedSession();
        String expectedReply = "Sure, tell me more.";
        // park the thread at awaitGeneration, then have the pool refuse the job
        llm.queueChat(turn("On it!", readyBrief()));
        executorRejects = true;
        assertThatThrownBy(() -> service.message(session.getToken(), "2 days, 6 of us, bars"))
                .isInstanceOf(AiLimitException.class).hasFieldOrPropertyWithValue("code", "AI_BUSY");
        AiGeneration failed = savedGenerations.get(savedGenerations.size() - 1);
        executorRejects = false;
        llm.queueChat(turn(expectedReply, Brief.empty()));

        AiSessionService.TurnOutcome outcome = service.message(session.getToken(), "anything happening?");

        // The user must get an answer. With an unconditional awaitGeneration edge this resume ran a
        // whole generation on this thread and flipped the FAILED row to READY instead.
        assertThat(llm.planRequests).isEmpty();
        List<ChatMessage> messages = outcome.view().state().messages();
        assertThat(messages.get(messages.size() - 1).content()).isEqualTo(expectedReply);
        assertThat(failed.getStatus()).isEqualTo(AiGenerationStatus.FAILED);
        assertThat(failed.getErrorCode()).isEqualTo("AI_BUSY");
    }

    @Test
    void message_buildsTheViewBeforeHandingTheGraphThreadToTheJob() {
        AiSession session = startedSession();
        llm.queueChat(turn("On it!", readyBrief()));
        callOrder.clear();

        service.message(session.getToken(), "2 days, 6 of us, bars");

        // Snapshotting after the submit would race the job's own update/resume on the same thread id.
        assertThat(callOrder).containsExactly("view", "submit");
    }

    @Test
    void select_whileAGenerationIsInFlight_isConflict() {
        AiSession session = startedSession();
        AiGeneration generation = readyGeneration(session, Tier.BASIC, UUID.randomUUID());
        when(generationRepository.existsBySessionIdAndStatusIn(any(), any())).thenReturn(true);

        // Resuming with SELECT under a queued job would stamp the old generation id over the new one
        // and hand the job a graph thread that has already left awaitGeneration.
        assertThatThrownBy(() -> service.select(generation.getId(), Tier.BASIC))
                .isInstanceOf(AiConflictException.class)
                .hasFieldOrPropertyWithValue("code", "GENERATION_IN_PROGRESS");
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
    void requestGeneration_parksAtAwaitGeneration_andQueuesTheJobWithoutWritingToTheGraph() {
        AiSession session = startedSession();
        int expectedGenerationCount = 1;
        graph.update(session.getToken(), Map.of(PlannerState.BRIEF, JsonCodec.write(readyBrief())));

        AiGeneration generation = service.requestGeneration(session.getToken());

        assertThat(generation.getStatus()).isEqualTo(AiGenerationStatus.QUEUED);
        assertThat(graph.snapshot(session.getToken()).next()).isEqualTo(PlannerGraph.AWAIT_GENERATION);
        // The generation id belongs to the job: it writes it together with RESUME_REASON=GENERATE
        // immediately before resuming, and nothing reads it in between.
        assertThat(graph.snapshot(session.getToken()).state().generationId()).isEmpty();
        assertThat(session.getGenerationCount()).isEqualTo(expectedGenerationCount);
        assertThat(submittedJobs).hasSize(1);
        assertThat(llm.chatRequests).isEmpty();
    }

    @Test
    void get_returnsTheLatestGeneration() {
        AiSession session = startedSession();
        AiGeneration expectedLatest = new AiGeneration();
        expectedLatest.setId(UUID.randomUUID());
        when(generationRepository.findFirstBySessionIdOrderByCreatedAtDescIdDesc(session.getId()))
                .thenReturn(Optional.of(expectedLatest));

        AiSessionService.SessionView view = service.get(session.getToken());

        assertThat(view.latest()).contains(expectedLatest);
        assertThat(view.next()).isEqualTo(PlannerGraph.AWAIT_USER);
    }

    /**
     * The newest row is not always the one with packages on it. After a failed or AI_BUSY
     * regeneration the organizer must still be able to get back to the packages from the token.
     */
    @Test
    void get_alsoReportsTheNewestReadyGeneration() {
        AiSession session = startedSession();
        AiGeneration expectedLatest = new AiGeneration();
        expectedLatest.setId(UUID.randomUUID());
        expectedLatest.setStatus(AiGenerationStatus.FAILED);
        AiGeneration expectedLatestReady = readyGeneration(session, Tier.BASIC, UUID.randomUUID());
        when(generationRepository.findFirstBySessionIdOrderByCreatedAtDescIdDesc(session.getId()))
                .thenReturn(Optional.of(expectedLatest));
        when(generationRepository.findFirstBySessionIdAndStatusOrderByCreatedAtDescIdDesc(session.getId(),
                AiGenerationStatus.READY)).thenReturn(Optional.of(expectedLatestReady));

        AiSessionService.SessionView view = service.get(session.getToken());

        assertThat(view.latest()).contains(expectedLatest);
        assertThat(view.latestReady()).contains(expectedLatestReady);
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

    /**
     * The graph's sink stores the edited row in its own transaction; the budget it spends belongs to the
     * session row this thread is holding, and a turn has exactly one save to spend it in.
     */
    @Test
    void editTurn_incrementsEditCountBeforeTheSessionSave_andReturnsTheEditedGeneration() {
        AiSession session = startedSession();
        int expectedEditCount = 1;
        CatalogActivity expectedReplaced = catalogActivity("Beer Bike", "beer-bike");
        CatalogActivity expectedReplacement = catalogActivity("Club Crawl", "club-crawl");
        ComposedPlan plan = planWith(expectedReplaced);
        AiGeneration parent = storedParent(session, plan);
        parkWithPackages(session, List.of(expectedReplaced, expectedReplacement), plan, parent.getId());
        llm.queueChat(editTurn("Swapped it.", expectedReplaced.name(), expectedReplacement.name()))
                .queueRefresh(refreshedTexts("Rebuilt around the swap"));
        answerEditedGenerationLookups();
        editCountsWhenSaved.clear();

        AiSessionService.TurnOutcome outcome = service.message(session.getToken(), "swap the bike for the crawl");

        AiGeneration edited = outcome.editedGeneration().orElseThrow();
        assertThat(edited.getKind()).isEqualTo(AiGenerationKind.EDITED);
        assertThat(edited.getParentId()).isEqualTo(parent.getId());
        assertThat(edited.getStatus()).isEqualTo(AiGenerationStatus.READY);
        assertThat(outcome.startedGeneration()).isEmpty();
        assertThat(outcome.editReport()).hasValueSatisfying(report -> {
            assertThat(report.rejected()).isEmpty();
            assertThat(report.textsRefreshed()).isTrue();
            assertThat(report.applied()).singleElement().satisfies(applied ->
                    assertThat(applied.replacementName()).isEqualTo(expectedReplacement.name()));
        });
        assertThat(session.getEditCount()).isEqualTo(expectedEditCount);
        assertThat(editCountsWhenSaved).containsExactly(expectedEditCount);
        // An edit parks where a finished generation parks: the packages are on the screen either way.
        assertThat(graph.snapshot(session.getToken()).next()).isEqualTo(PlannerGraph.AWAIT_SELECTION);
    }

    /** A batch that changed nothing creates no row, so it must not cost one of the twenty edit turns. */
    @Test
    void editTurn_withNothingApplied_doesNotCountAgainstTheLimit() {
        AiSession session = startedSession();
        int expectedEditCount = 0;
        CatalogActivity inThePlan = catalogActivity("Beer Bike", "beer-bike");
        ComposedPlan plan = planWith(inThePlan);
        AiGeneration parent = storedParent(session, plan);
        parkWithPackages(session, List.of(inThePlan), plan, parent.getId());
        llm.queueChat(editTurn("I could not find that one.", "Hot Air Balloon", inThePlan.name()));
        savedGenerations.clear();
        editCountsWhenSaved.clear();

        AiSessionService.TurnOutcome outcome = service.message(session.getToken(), "swap the balloon ride");

        assertThat(outcome.editedGeneration()).isEmpty();
        assertThat(outcome.editReport()).hasValueSatisfying(report -> {
            assertThat(report.applied()).isEmpty();
            assertThat(report.rejected()).singleElement().satisfies(rejected ->
                    assertThat(rejected.reason()).isEqualTo(EditRejectionReason.UNKNOWN_ACTIVITY));
        });
        assertThat(session.getEditCount()).isEqualTo(expectedEditCount);
        assertThat(editCountsWhenSaved).containsExactly(expectedEditCount);
        assertThat(savedGenerations).isEmpty();
        // Nothing landed, so there is no copy to rewrite and no reason to spend a model call on it.
        assertThat(llm.refreshRequests).isEmpty();
    }

    /**
     * A report belongs to the turn that produced it: without a reset, the one turn N produced would be
     * handed back again with the answer to turn N+1.
     *
     * <p>Two places clear it and both are meant to stay — the chat node, which keeps the graph honest for
     * any caller, and the service's pre-resume update, which keeps the API honest on a turn that never
     * reaches the node. This test passes with either one alone, so deleting "the redundant one" will not
     * fail here; {@code ChatTurnNodeTest} covers the node's own clear.
     */
    @Test
    void turn_stampsEditsLeftAndClearsThePreviousReport() {
        AiSession session = startedSession();
        int spentEdits = 3;
        int expectedEditsLeft = AiSessionService.MAX_EDITS_PER_SESSION - spentEdits;
        llm.queueChat(editTurn("Let us build the packages first.", "Beer Bike", "Club Crawl"),
                turn("Sure, tell me more.", Brief.empty()));

        AiSessionService.TurnOutcome reported = service.message(session.getToken(), "swap them");
        session.setEditCount(spentEdits);
        AiSessionService.TurnOutcome next = service.message(session.getToken(), "anything else to know?");

        assertThat(reported.editReport()).hasValueSatisfying(report ->
                assertThat(report.rejected()).singleElement().satisfies(rejected ->
                        assertThat(rejected.reason()).isEqualTo(EditRejectionReason.NO_PACKAGES_YET)));
        assertThat(next.editReport()).isEmpty();
        assertThat(graph.snapshot(session.getToken()).state().editsLeft()).isEqualTo(expectedEditsLeft);
    }

    /**
     * "An edit landed" means "a READY row exists", so the chat says READY. The hole this closes: a
     * generation whose result never committed leaves the chat FAILED while the checkpoint still holds the
     * previous plan, and an edit on that plan would otherwise stay filed under a failure.
     */
    @Test
    void editTurn_onAChatLeftFailed_putsTheSessionBackToReady() {
        AiSession session = startedSession();
        CatalogActivity expectedReplaced = catalogActivity("Beer Bike", "beer-bike");
        CatalogActivity expectedReplacement = catalogActivity("Club Crawl", "club-crawl");
        ComposedPlan plan = planWith(expectedReplaced);
        AiGeneration parent = storedParent(session, plan);
        parkWithPackages(session, List.of(expectedReplaced, expectedReplacement), plan, parent.getId());
        session.setStatus(AiSessionStatus.FAILED);
        llm.queueChat(editTurn("Swapped it.", expectedReplaced.name(), expectedReplacement.name()))
                .queueRefresh(refreshedTexts("Rebuilt around the swap"));
        answerEditedGenerationLookups();

        AiSessionService.TurnOutcome outcome = service.message(session.getToken(), "swap the bike for the crawl");

        assertThat(outcome.editedGeneration()).isPresent();
        assertThat(session.getStatus()).isEqualTo(AiSessionStatus.READY);
    }

    /**
     * The row was stored and the report says so; only the read-back came up empty. This pins the
     * log-and-continue behaviour rather than closing a 500 — the lookup answers with an empty
     * {@code Optional}, it does not throw — so the turn hands back the report and lets the client read
     * the session again for the packages. The budget is spent either way: the write happened.
     */
    @Test
    void editTurn_whoseStoredRowCannotBeReadBack_stillReturnsTheReport() {
        AiSession session = startedSession();
        int expectedEditCount = 1;
        CatalogActivity expectedReplaced = catalogActivity("Beer Bike", "beer-bike");
        CatalogActivity expectedReplacement = catalogActivity("Club Crawl", "club-crawl");
        ComposedPlan plan = planWith(expectedReplaced);
        AiGeneration parent = storedParent(session, plan);
        parkWithPackages(session, List.of(expectedReplaced, expectedReplacement), plan, parent.getId());
        llm.queueChat(editTurn("Swapped it.", expectedReplaced.name(), expectedReplacement.name()))
                .queueRefresh(refreshedTexts("Rebuilt around the swap"));
        when(generationRepository.findWithSessionById(any())).thenReturn(Optional.empty());

        AiSessionService.TurnOutcome outcome = service.message(session.getToken(), "swap the bike for the crawl");

        assertThat(outcome.editedGeneration()).isEmpty();
        assertThat(outcome.editReport()).hasValueSatisfying(report -> assertThat(report.applied()).hasSize(1));
        // The write happened, so the budget is spent whether or not this thread could read the row back.
        assertThat(session.getEditCount()).isEqualTo(expectedEditCount);
    }

    /**
     * The twenty-first edit turn is answered like any other turn: every op rejected with EDIT_LIMIT, no
     * model call to rewrite copy, no row, no counter moved - and a limits block that already said zero.
     */
    @Test
    void editTurn_overTheEditLimit_rejectsTheBatchWithoutStoringAnything() {
        AiSession session = startedSession();
        int expectedEditsLeft = 0;
        CatalogActivity inThePlan = catalogActivity("Beer Bike", "beer-bike");
        CatalogActivity replacement = catalogActivity("Club Crawl", "club-crawl");
        ComposedPlan plan = planWith(inThePlan);
        AiGeneration parent = storedParent(session, plan);
        parkWithPackages(session, List.of(inThePlan, replacement), plan, parent.getId());
        session.setEditCount(AiSessionService.MAX_EDITS_PER_SESSION);
        llm.queueChat(editTurn("That is as far as I can take it.", inThePlan.name(), replacement.name()));
        savedGenerations.clear();

        AiSessionService.TurnOutcome outcome = service.message(session.getToken(), "swap them once more");

        assertThat(graph.snapshot(session.getToken()).state().editsLeft()).isEqualTo(expectedEditsLeft);
        assertThat(outcome.editReport()).hasValueSatisfying(report -> {
            assertThat(report.applied()).isEmpty();
            assertThat(report.rejected()).singleElement().satisfies(rejected ->
                    assertThat(rejected.reason()).isEqualTo(EditRejectionReason.EDIT_LIMIT));
        });
        assertThat(outcome.editedGeneration()).isEmpty();
        assertThat(savedGenerations).isEmpty();
        assertThat(llm.refreshRequests).isEmpty();
        assertThat(session.getEditCount()).isEqualTo(AiSessionService.MAX_EDITS_PER_SESSION);
        assertThat(mapper.sessionState(service.get(session.getToken())).limits().editsLeft())
                .isEqualTo(expectedEditsLeft);
    }

    /**
     * Selecting an older READY generation is the documented undo, so the graph has to be moved onto that
     * row whole: the cart, the chat and the next edit's parent all have to mean the same plan. Stamping
     * the id alone left the next edit applied to the packages the undone edit produced while filed under
     * the row the organizer went back to.
     */
    @Test
    void select_onTheParentAfterAnEdit_putsThatPlanBack_andTheNextEditHangsOffIt() {
        AiSession session = startedSession();
        CatalogActivity expectedInTheCart = catalogActivity("Beer Bike", "beer-bike");
        CatalogActivity expectedReplacement = catalogActivity("Club Crawl", "club-crawl");
        ComposedPlan parentPlan = planWith(expectedInTheCart);
        AiGeneration parent = storedParent(session, parentPlan);
        parkWithPackages(session, List.of(expectedInTheCart, expectedReplacement), parentPlan, parent.getId());
        llm.queueChat(editTurn("Swapped it.", expectedInTheCart.name(), expectedReplacement.name()))
                .queueRefresh(refreshedTexts("Rebuilt around the swap"))
                .queueChat(editTurn("Swapped it again.", expectedInTheCart.name(), expectedReplacement.name()))
                .queueRefresh(refreshedTexts("Rebuilt around the swap again"));
        answerEditedGenerationLookups();
        service.message(session.getToken(), "swap the bike for the crawl");

        AiSessionService.Selection selection = service.select(parent.getId(), Tier.BASIC);
        AiSessionService.TurnOutcome afterUndo = service.message(session.getToken(), "swap it again");

        assertThat(selection.activityIds()).containsExactly(expectedInTheCart.id());
        // The swap could only apply a second time because the parent's plan is back in the state; against
        // the edited one the Beer Bike it names is no longer there and it would be NOT_IN_PACKAGE.
        assertThat(afterUndo.editReport()).hasValueSatisfying(report -> {
            assertThat(report.rejected()).isEmpty();
            assertThat(report.applied()).hasSize(1);
        });
        assertThat(afterUndo.editedGeneration()).hasValueSatisfying(edited ->
                assertThat(edited.getParentId()).isEqualTo(parent.getId()));
    }

    /**
     * The brief is half of what an undo undoes: it prices every line and is what the validator checks the
     * day count against. Restoring only the plan billed the older packages for the newer group size while
     * filing them under a brief snapshot that said otherwise - a money-facing contradiction - and made the
     * validator reject every edit with WRONG_DAY_COUNT once the day count had moved. Leaving
     * LAST_GENERATED_BRIEF behind was worse still: the next chat turn saw a brief that "changed" and spent
     * one of the five generations rebuilding what the organizer had just gone back to.
     */
    @Test
    void select_onAnOlderGeneration_restoresTheBriefItWasBuiltFor() {
        AiSession session = startedSession();
        int expectedGroupSize = editBrief().groupSize();
        BigDecimal expectedLineTotal = new BigDecimal("160.00");
        CatalogActivity expectedReplaced = catalogActivity("Beer Bike", "beer-bike");
        CatalogActivity expectedReplacement = catalogActivity("Club Crawl", "club-crawl");
        ComposedPlan planOfA = planWith(expectedReplaced);
        AiGeneration generationA = storedParent(session, planOfA);
        // What a regeneration for "8 of us, 2 days" leaves behind: a bigger group, a longer trip, and
        // its own packages and id in the state.
        Brief briefOfB = new Brief(2, 8, List.of("nightlife"), null, null, null,
                DayEdge.AFTERNOON, DayEdge.EVENING, null);
        parkWithPackages(session, briefOfB, List.of(expectedReplaced, expectedReplacement),
                planWith(expectedReplacement), UUID.randomUUID());
        llm.queueChat(editTurn("Swapped it back.", expectedReplaced.name(), expectedReplacement.name()))
                .queueRefresh(refreshedTexts("Rebuilt around the swap"));
        answerEditedGenerationLookups();

        service.select(generationA.getId(), Tier.BASIC);
        AiSessionService.TurnOutcome outcome = service.message(session.getToken(), "swap the bike for the crawl");

        assertThat(graph.snapshot(session.getToken()).state().brief().groupSize()).isEqualTo(expectedGroupSize);
        AiGeneration edited = outcome.editedGeneration().orElseThrow();
        assertThat(edited.getParentId()).isEqualTo(generationA.getId());
        // The row is filed under A's brief, so every line in it has to be priced for A's group.
        Brief snapshot = JsonCodec.read(edited.getBriefSnapshot(), Brief.class);
        assertThat(snapshot.groupSize()).isEqualTo(expectedGroupSize);
        assertThat(lineTotalsIn(JsonCodec.read(edited.getResult(), ComposedPlan.class)))
                .allSatisfy(lineTotal -> assertThat(lineTotal).isEqualByComparingTo(expectedLineTotal));
        // and the turn edited rather than regenerating: no plan asked for, no generation spent
        assertThat(outcome.startedGeneration()).isEmpty();
        assertThat(llm.planRequests).isEmpty();
        assertThat(submittedJobs).isEmpty();
        assertThat(session.getGenerationCount()).isZero();
    }

    private static List<BigDecimal> lineTotalsIn(ComposedPlan plan) {
        return plan.packages().stream()
                .flatMap(p -> p.days().stream())
                .flatMap(day -> day.items().stream())
                .map(ComposedPlan.ItemResult::lineTotal)
                .toList();
    }

    /**
     * A rejected op adds a second assistant message after the model's own reply, and only the last one
     * used to reach the client - so on exactly the turns that needed explaining, the model's answer was
     * dropped and the group saw the template line alone.
     */
    @Test
    void editTurn_withARejectedOp_handsBackEveryAssistantMessageOfTheTurn() {
        AiSession session = startedSession();
        String expectedReply = "I could not find that one.";
        String expectedUnknownName = "Hot Air Balloon";
        CatalogActivity inThePlan = catalogActivity("Beer Bike", "beer-bike");
        ComposedPlan plan = planWith(inThePlan);
        AiGeneration parent = storedParent(session, plan);
        parkWithPackages(session, List.of(inThePlan), plan, parent.getId());
        llm.queueChat(editTurn(expectedReply, expectedUnknownName, inThePlan.name()));

        AiSessionService.TurnOutcome outcome = service.message(session.getToken(), "swap the balloon ride");

        assertThat(outcome.assistantMessages()).hasSize(2);
        assertThat(outcome.assistantMessages().get(0).content()).isEqualTo(expectedReply);
        assertThat(outcome.assistantMessages().get(1).content()).contains(expectedUnknownName);
    }

    @Test
    void editBeforeAnyPackages_handsBackBothTheReplyAndTheNote() {
        AiSession session = startedSession();
        String expectedReply = "Sure - what would you like changed?";
        String expectedNote = EditMessages.noPackagesYet("en");
        llm.queueChat(editTurn(expectedReply, "Beer Bike", "Club Crawl"));

        AiSessionService.TurnOutcome outcome = service.message(session.getToken(), "swap them");

        assertThat(outcome.assistantMessages()).extracting(ChatMessage::content)
                .containsExactly(expectedReply, expectedNote);
    }

    /** An ordinary turn hands back its one reply, and never a message an earlier turn already produced. */
    @Test
    void anOrdinaryTurn_handsBackOnlyItsOwnReply() {
        AiSession session = startedSession();
        String expectedReply = "Sure, tell me more.";
        llm.queueChat(turn("How many days?", Brief.empty()), turn(expectedReply, Brief.empty()));
        service.message(session.getToken(), "hello");

        AiSessionService.TurnOutcome outcome = service.message(session.getToken(), "anything else to know?");

        assertThat(outcome.assistantMessages()).extracting(ChatMessage::content).containsExactly(expectedReply);
    }

    /** A report belongs to the turn that produced it, and an explicit "build it now" is not that turn. */
    @Test
    void requestGeneration_clearsThePreviousTurnsEditReport() {
        AiSession session = startedSession();
        graph.update(session.getToken(), Map.of(
                PlannerState.BRIEF, JsonCodec.write(readyBrief()),
                PlannerState.EDIT_REPORT, JsonCodec.write(EditReport.allRejected(
                        List.of(new EditRequest(EditOp.REPLACE, "Beer Bike", "Club Crawl", null, null, null)),
                        EditRejectionReason.NO_FREE_SLOT))));

        service.requestGeneration(session.getToken());

        assertThat(graph.snapshot(session.getToken()).state().editReport()).isEmpty();
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
