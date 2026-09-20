package com.myhive.backend.ai.service;

import com.myhive.backend.ai.edit.AppliedEdit;
import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.edit.EditOutcome;
import com.myhive.backend.ai.edit.EditReport;
import com.myhive.backend.ai.exception.AiLimitException;
import com.myhive.backend.ai.graph.PlannerGraph;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.graph.ResumeReason;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.entity.AiGeneration;
import com.myhive.backend.entity.AiGenerationKind;
import com.myhive.backend.entity.AiGenerationStatus;
import com.myhive.backend.entity.AiSession;
import com.myhive.backend.entity.AiSessionStatus;
import com.myhive.backend.repository.AiGenerationRepository;
import com.myhive.backend.repository.AiSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlanGenerationServiceTest {

    private final AiGenerationRepository generationRepository = mock(AiGenerationRepository.class);
    private final AiSessionRepository sessionRepository = mock(AiSessionRepository.class);
    private final PlannerGraph graph = mock(PlannerGraph.class);
    private final Executor executor = mock(Executor.class);
    /** Stands in for the transactional proxy Spring injects; wired to the service itself in setUp. */
    @SuppressWarnings("unchecked")
    private final ObjectProvider<PlanGenerationService> self = mock(ObjectProvider.class);
    private final PlanGenerationService service =
            new PlanGenerationService(generationRepository, sessionRepository, graph, executor, self);

    @BeforeEach
    void wireSelfProvider() {
        when(self.getObject()).thenReturn(service);
        // What a job normally finds: the thread parked at awaitGeneration, where the request left it.
        parkedAt(PlannerGraph.AWAIT_GENERATION);
    }

    private void parkedAt(String node) {
        when(graph.snapshot(any()))
                .thenReturn(new PlannerGraph.PlannerStateSnapshot(new PlannerState(Map.of()), node));
    }

    private static AiSession session() {
        AiSession s = new AiSession();
        s.setId(UUID.randomUUID());
        s.setToken(UUID.randomUUID());
        s.setStatus(AiSessionStatus.COLLECTING);
        return s;
    }

    private AiGeneration savedGeneration(AiGenerationStatus status) {
        return savedGeneration(status, session());
    }

    private AiGeneration savedGeneration(AiGenerationStatus status, AiSession session) {
        AiGeneration g = new AiGeneration();
        g.setId(UUID.randomUUID());
        g.setSession(session);
        g.setStatus(status);
        when(generationRepository.findWithSessionById(g.getId())).thenReturn(Optional.of(g));
        when(generationRepository.findById(g.getId())).thenReturn(Optional.of(g));
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        return g;
    }

    private void savesWithGeneratedId() {
        when(generationRepository.save(any())).thenAnswer(inv -> {
            AiGeneration saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }

    @Test
    void enqueue_savesQueuedRow_andSubmitsJob() {
        savesWithGeneratedId();
        AiSession session = session();
        int expectedGenerationCount = 1;

        AiGeneration generation = service.enqueue(session, Brief.empty());

        assertThat(generation.getStatus()).isEqualTo(AiGenerationStatus.QUEUED);
        assertThat(generation.getBriefSnapshot()).isNotBlank();
        assertThat(session.getGenerationCount()).isEqualTo(expectedGenerationCount);
        assertThat(session.getStatus()).isEqualTo(AiSessionStatus.GENERATING);
        verify(executor).execute(any());
    }

    /**
     * The QUEUED row is committed before the job is submitted, so anything after it that can throw
     * strands the row: no job owns it, the sweeper skips it (it was created after this process
     * started) and every later message or selection answers 409 GENERATION_IN_PROGRESS until the
     * next restart. The generation id is written by {@link PlanGenerationService#runJob} instead,
     * with the resume reason, immediately before the thread is resumed.
     */
    @Test
    void enqueue_neverTouchesTheGraph_soAGraphFailureCannotBrickTheSession() {
        savesWithGeneratedId();
        AiSession session = session();
        doThrow(new UnsupportedOperationException("graph unreachable")).when(graph).update(any(), any());

        AiGeneration generation = service.enqueue(session, Brief.empty());

        assertThat(generation.getStatus()).isEqualTo(AiGenerationStatus.QUEUED);
        verify(executor).execute(any());
        verifyNoInteractions(graph);
    }

    @Test
    void enqueue_whenExecutorRejects_marksFailedAndThrowsBusy() {
        savesWithGeneratedId();
        AiSession session = session();
        int expectedGenerationCount = 0;
        doThrow(new RejectedExecutionException()).when(executor).execute(any());

        assertThatThrownBy(() -> service.enqueue(session, Brief.empty()))
                .isInstanceOf(AiLimitException.class).hasFieldOrPropertyWithValue("code", "AI_BUSY");

        ArgumentCaptor<AiGeneration> saved = ArgumentCaptor.captor();
        verify(generationRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(AiGenerationStatus.FAILED);
        assertThat(saved.getValue().getErrorCode()).isEqualTo("AI_BUSY");
        assertThat(session.getStatus()).isEqualTo(AiSessionStatus.FAILED);
        // A full pool is a "retry in a few seconds", not one of the group's five generations.
        assertThat(session.getGenerationCount()).isEqualTo(expectedGenerationCount);
    }

    /**
     * A rejection is "retry in a few seconds", not "your packages are gone": with a READY generation
     * on file the chat stays READY and the UI restores from {@code latestReadyGeneration}.
     */
    @Test
    void enqueue_whenExecutorRejects_keepsASessionThatAlreadyHasPackagesReady() {
        savesWithGeneratedId();
        AiSession session = session();
        doThrow(new RejectedExecutionException()).when(executor).execute(any());
        when(generationRepository.existsBySessionIdAndStatusIn(eq(session.getId()), any())).thenReturn(true);

        assertThatThrownBy(() -> service.enqueue(session, Brief.empty()))
                .isInstanceOf(AiLimitException.class).hasFieldOrPropertyWithValue("code", "AI_BUSY");

        assertThat(session.getStatus()).isEqualTo(AiSessionStatus.READY);
    }

    @Test
    void failingAGeneration_whileAnEarlierOneIsReady_keepsTheSessionReady() {
        AiSession session = session();
        AiGeneration generation = savedGeneration(AiGenerationStatus.RUNNING, session);
        when(generationRepository.existsBySessionIdAndStatusIn(eq(session.getId()), any())).thenReturn(true);

        service.fail(generation.getId(), "INTERNAL");

        assertThat(generation.getStatus()).isEqualTo(AiGenerationStatus.FAILED);
        assertThat(generation.getErrorCode()).isEqualTo("INTERNAL");
        assertThat(session.getStatus()).isEqualTo(AiSessionStatus.READY);
    }

    @Test
    void runJob_marksRunning_resumesGraph_andFailsOnException() {
        AiGeneration generation = savedGeneration(AiGenerationStatus.QUEUED);
        doThrow(new IllegalStateException("graph exploded")).when(graph).runUntilInterrupt(any());

        service.runJob(generation.getId());

        assertThat(generation.getStatus()).isEqualTo(AiGenerationStatus.FAILED);
        assertThat(generation.getErrorCode()).isEqualTo("INTERNAL");
        assertThat(generation.getSession().getStatus()).isEqualTo(AiSessionStatus.FAILED);
    }

    @Test
    void runJob_stampsGenerationIdAndTheGenerateResumeReasonBeforeResuming() {
        AiGeneration generation = savedGeneration(AiGenerationStatus.QUEUED);

        service.runJob(generation.getId());

        ArgumentCaptor<Map<String, Object>> update = ArgumentCaptor.captor();
        verify(graph).update(eq(generation.getSession().getToken()), update.capture());
        assertThat(update.getValue()).containsEntry(PlannerState.GENERATION_ID, generation.getId().toString());
        // Only GENERATE reaches the generation branch, and only this job may write it.
        assertThat(update.getValue()).containsEntry(PlannerState.RESUME_REASON, ResumeReason.GENERATE.name());
    }

    @Test
    void failingAJob_doesNotRewindTheSessionCountersTheRequestThreadWrote() {
        int expectedMessageCount = 3;
        int expectedGenerationCount = 2;
        AiSession session = session();
        AiGeneration generation = savedGeneration(AiGenerationStatus.QUEUED, session);
        // The job has been holding its copy since it started; meanwhile the request that enqueued it
        // bumped the counters. Re-reading is what keeps a failure from handing those limits back.
        AiSession current = session();
        current.setId(session.getId());
        current.setToken(session.getToken());
        current.setMessageCount(expectedMessageCount);
        current.setGenerationCount(expectedGenerationCount);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(current));
        doThrow(new IllegalStateException("graph exploded")).when(graph).runUntilInterrupt(any());

        service.runJob(generation.getId());

        ArgumentCaptor<AiSession> saved = ArgumentCaptor.captor();
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().getMessageCount()).isEqualTo(expectedMessageCount);
        assertThat(saved.getValue().getGenerationCount()).isEqualTo(expectedGenerationCount);
        assertThat(saved.getValue().getStatus()).isEqualTo(AiSessionStatus.FAILED);
    }

    @Test
    void runJob_whenGraphParkedWithoutPersisting_failsTheRow() {
        AiGeneration generation = savedGeneration(AiGenerationStatus.QUEUED);

        service.runJob(generation.getId());

        assertThat(generation.getStatus()).isEqualTo(AiGenerationStatus.FAILED);
        assertThat(generation.getErrorCode()).isEqualTo("INTERNAL");
    }

    @Test
    void runJob_whenPersistResultAlreadyStoredThePlan_leavesTheRowAlone() {
        AiGeneration generation = savedGeneration(AiGenerationStatus.QUEUED);
        // persistResult runs inside runUntilInterrupt and commits READY in its own transaction; the
        // detached row this thread holds still says RUNNING, so the check has to re-read it.
        AiGeneration reread = new AiGeneration();
        reread.setId(generation.getId());
        reread.setSession(generation.getSession());
        reread.setStatus(AiGenerationStatus.READY);
        when(graph.runUntilInterrupt(any())).thenAnswer(inv -> {
            when(generationRepository.findById(generation.getId())).thenReturn(Optional.of(reread));
            return null;
        });

        service.runJob(generation.getId());

        assertThat(generation.getErrorCode()).isNull();
        assertThat(generation.getSession().getStatus()).isNotEqualTo(AiSessionStatus.FAILED);
    }

    /**
     * persistResult commits READY from inside the resume, so a fault raised afterwards - parking the
     * thread, the checkpoint write - arrives when the packages are already on the group's screen. An
     * unconditional fail() there flipped a finished generation to FAILED.
     */
    @Test
    void runJob_whenTheResumeThrowsAfterTheResultWasStored_leavesTheRowReady() {
        AiGeneration generation = savedGeneration(AiGenerationStatus.QUEUED);
        AiGeneration reread = new AiGeneration();
        reread.setId(generation.getId());
        reread.setSession(generation.getSession());
        reread.setStatus(AiGenerationStatus.READY);
        doThrow(new IllegalStateException("checkpoint write failed")).when(graph).runUntilInterrupt(any());
        when(generationRepository.findById(generation.getId())).thenReturn(Optional.of(reread));

        service.runJob(generation.getId());

        assertThat(reread.getStatus()).isEqualTo(AiGenerationStatus.READY);
        assertThat(reread.getErrorCode()).isNull();
        assertThat(reread.getSession().getStatus()).isNotEqualTo(AiSessionStatus.FAILED);
    }

    /**
     * A job must enter the branch through awaitGeneration, the only park point GENERATE turns into a
     * plan. A thread that had to be re-parked comes back at awaitUser instead, so the job walks it
     * forward first - without that hop the resume below would park again and the job would report
     * "finished without a result".
     */
    /**
     * A job must enter the branch through awaitGeneration, the only park point GENERATE turns into a
     * plan. A thread found anywhere else - re-parked at awaitUser after a dead run, or simply left at
     * awaitSelection - is walked there first; without that hop the resume would park again and the job
     * would report "finished without a result".
     */
    @Test
    void runJob_whenTheThreadIsNotAtAwaitGeneration_walksItThereFirst() {
        AiGeneration generation = savedGeneration(AiGenerationStatus.QUEUED);
        UUID token = generation.getSession().getToken();
        parkedAt(PlannerGraph.AWAIT_USER);

        service.runJob(generation.getId());

        verify(graph).update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.GENERATE.name()));
        verify(graph).update(token, Map.of(PlannerState.GENERATION_ID, generation.getId().toString(),
                PlannerState.RESUME_REASON, ResumeReason.GENERATE.name()));
        verify(graph, times(2)).runUntilInterrupt(token);
    }

    /** The common case: the request already parked the thread where the job needs it. */
    @Test
    void runJob_whenTheThreadIsAlreadyAtAwaitGeneration_resumesItOnce() {
        AiGeneration generation = savedGeneration(AiGenerationStatus.QUEUED);

        service.runJob(generation.getId());

        verify(graph, times(1)).runUntilInterrupt(generation.getSession().getToken());
    }

    /**
     * The brief handed to the re-park is the one the newest generation that really delivered packages
     * was built from, and it is only looked up when a dead run is actually found.
     */
    @Test
    void ensureParked_offersTheBriefOfTheNewestGenerationThatDelivered() {
        AiSession session = session();
        String expectedBrief = "{\"days\":2,\"groupSize\":6}";
        AiGeneration delivered = new AiGeneration();
        delivered.setBriefSnapshot(expectedBrief);
        when(generationRepository.findFirstBySessionIdAndStatusOrderByCreatedAtDescIdDesc(session.getId(),
                AiGenerationStatus.READY)).thenReturn(Optional.of(delivered));

        service.ensureParked(session);

        ArgumentCaptor<Supplier<String>> supplier = ArgumentCaptor.captor();
        verify(graph).ensureParked(eq(session.getToken()), supplier.capture());
        verify(generationRepository, never()).findFirstBySessionIdAndStatusOrderByCreatedAtDescIdDesc(any(), any());
        assertThat(supplier.getValue().get()).isEqualTo(expectedBrief);
    }

    /**
     * Defence in depth behind the re-park: a plan that arrives from a run nobody owns any more must
     * not put packages back on a screen the sweep has already told the group are gone.
     */
    @Test
    void ready_onAGenerationTheSweepAlreadyFailed_isRefused() {
        AiGeneration generation = savedGeneration(AiGenerationStatus.FAILED);
        String expectedErrorCode = "STALE";
        generation.setErrorCode(expectedErrorCode);

        boolean stored = service.ready(generation.getId(), new ComposedPlan(List.of(), false), false,
                LlmUsage.none(), 0);

        // false is what tells persistResult to unwind the "these packages exist" stamp
        assertThat(stored).isFalse();
        assertThat(generation.getStatus()).isEqualTo(AiGenerationStatus.FAILED);
        assertThat(generation.getErrorCode()).isEqualTo(expectedErrorCode);
        assertThat(generation.getResult()).isNull();
        verify(generationRepository, never()).save(any());
    }

    @Test
    void ready_storesResultAndUsage_andMarksSessionReady() {
        AiGeneration generation = savedGeneration(AiGenerationStatus.RUNNING);
        String expectedModel = "qwen";
        short expectedAttempt = 1;
        ComposedPlan expectedPlan = new ComposedPlan(List.of(), false);

        boolean stored = service.ready(generation.getId(), expectedPlan, false,
                new LlmUsage(expectedModel, 100, 200, 1500L), expectedAttempt);

        assertThat(stored).isTrue();
        assertThat(generation.getStatus()).isEqualTo(AiGenerationStatus.READY);
        assertThat(generation.getResult()).contains("\"packages\"");
        assertThat(generation.getModel()).isEqualTo(expectedModel);
        assertThat(generation.getAttempt()).isEqualTo(expectedAttempt);
        assertThat(generation.getSession().getStatus()).isEqualTo(AiSessionStatus.READY);
    }

    @Test
    void failStaleRunning_marksRowsStale() {
        AiGeneration stale = savedGeneration(AiGenerationStatus.RUNNING);
        stale.setStartedAt(LocalDateTime.now().minusMinutes(10));
        when(generationRepository.findByStatusAndStartedAtBefore(eq(AiGenerationStatus.RUNNING), any()))
                .thenReturn(List.of(stale));

        service.failStaleRunning();

        assertThat(stale.getStatus()).isEqualTo(AiGenerationStatus.FAILED);
        assertThat(stale.getErrorCode()).isEqualTo("STALE");
        assertThat(stale.getSession().getStatus()).isEqualTo(AiSessionStatus.FAILED);
    }

    @Test
    void failStaleRunning_alsoSweepsQueuedRowsOrphanedByARestart() {
        // A restart between the insert and the pool thread leaves a QUEUED row nobody owns; it never
        // gets a startedAt, so the RUNNING sweep can never see it and the chat waits for ever.
        AiGeneration orphan = savedGeneration(AiGenerationStatus.QUEUED);
        orphan.setCreatedAt(PlanGenerationService.PROCESS_STARTED_AT.minusMinutes(10));
        when(generationRepository.findByStatusAndCreatedAtBefore(eq(AiGenerationStatus.QUEUED), any()))
                .thenReturn(List.of(orphan));

        service.failStaleRunning();

        assertThat(orphan.getStatus()).isEqualTo(AiGenerationStatus.FAILED);
        assertThat(orphan.getErrorCode()).isEqualTo("STALE");
        assertThat(orphan.getSession().getStatus()).isEqualTo(AiSessionStatus.FAILED);
    }

    /**
     * The sweep ages RUNNING rows on the clock, and a job that is merely slow is still holding the
     * graph thread mid-branch when that clock runs out. Failing its row stops holding requests off:
     * the next message re-parks the thread and runs a chat turn underneath the live job, whose next
     * node checkpoint is then pushed on top of both - and the message and its reply disappear from
     * the transcript. A row this process is still running is therefore never stale.
     */
    @Test
    void failStaleRunning_leavesARunThisProcessIsStillWorkingOn() {
        AiGeneration generation = savedGeneration(AiGenerationStatus.QUEUED);
        AtomicReference<AiGenerationStatus> statusDuringSweep = new AtomicReference<>();
        when(generationRepository.findByStatusAndStartedAtBefore(eq(AiGenerationStatus.RUNNING), any()))
                .thenReturn(List.of(generation));
        // the sweep fires while the job is inside the graph: a slow model call, not a lost thread
        when(graph.runUntilInterrupt(any())).thenAnswer(inv -> {
            service.failStaleRunning();
            statusDuringSweep.set(generation.getStatus());
            return null;
        });

        service.runJob(generation.getId());

        assertThat(statusDuringSweep.get()).isEqualTo(AiGenerationStatus.RUNNING);
        assertThat(generation.getErrorCode()).isNotEqualTo("STALE");
    }

    /** Ownership is given back when the job returns, so a row it really did abandon is still swept. */
    @Test
    void failStaleRunning_failsARowAgainOnceItsJobReturned() {
        AiGeneration generation = savedGeneration(AiGenerationStatus.QUEUED);
        service.runJob(generation.getId());

        sweepFinds(generation);

        assertThat(generation.getErrorCode()).isEqualTo("STALE");
    }

    /** Given back on the way out of a throwing run too - a finally, not the happy path. */
    @Test
    void failStaleRunning_failsARowAgainOnceItsJobThrew() {
        AiGeneration generation = savedGeneration(AiGenerationStatus.QUEUED);
        doThrow(new IllegalStateException("graph exploded")).when(graph).runUntilInterrupt(any());
        service.runJob(generation.getId());

        sweepFinds(generation);

        assertThat(generation.getErrorCode()).isEqualTo("STALE");
    }

    /** Re-arms a generation the job already closed out and runs the sweep over it. */
    private void sweepFinds(AiGeneration generation) {
        generation.setStatus(AiGenerationStatus.RUNNING);
        generation.setErrorCode(null);
        when(generationRepository.findByStatusAndStartedAtBefore(eq(AiGenerationStatus.RUNNING), any()))
                .thenReturn(List.of(generation));

        service.failStaleRunning();
    }

    @Test
    void failStaleRunning_agesRunningRowsOnTheStaleCutoff() {
        int expectedStaleAfterMinutes = PlanGenerationService.STALE_AFTER_MINUTES;

        service.failStaleRunning();

        ArgumentCaptor<LocalDateTime> runningCutoff = ArgumentCaptor.captor();
        verify(generationRepository)
                .findByStatusAndStartedAtBefore(eq(AiGenerationStatus.RUNNING), runningCutoff.capture());
        assertThat(runningCutoff.getValue())
                .isCloseTo(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(expectedStaleAfterMinutes),
                        within(1, ChronoUnit.MINUTES));
    }

    @Test
    void failStaleRunning_agesQueuedRowsOnThisProcessStart_neverOnAWaitingTime() {
        // Two workers over a twenty-deep queue at up to three minutes a job: an honest QUEUED wait
        // routinely outlives STALE_AFTER_MINUTES. Sweeping on age would fail a live generation, burn
        // the group's slot and flip the chat to FAILED while the job went on to produce a plan. Only
        // a row from a previous process can be an orphan, so the cutoff is this JVM's start instant.
        LocalDateTime expectedCutoff = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()), ZoneOffset.UTC);

        service.failStaleRunning();

        verify(generationRepository)
                .findByStatusAndCreatedAtBefore(eq(AiGenerationStatus.QUEUED), eq(expectedCutoff));
    }

    @Test
    void failStaleRunning_leavesARowThatTurnedReadyWhileTheSweepRan() {
        AiGeneration stale = savedGeneration(AiGenerationStatus.RUNNING);
        stale.setStartedAt(LocalDateTime.now().minusMinutes(10));
        when(generationRepository.findByStatusAndStartedAtBefore(eq(AiGenerationStatus.RUNNING), any()))
                .thenReturn(List.of(stale));
        // persistResult committed READY in its own transaction after the sweep's query selected the
        // row; closing it out now would take three finished packages off the group's screen.
        AiGeneration reread = new AiGeneration();
        reread.setId(stale.getId());
        reread.setSession(stale.getSession());
        reread.setStatus(AiGenerationStatus.READY);
        when(generationRepository.findById(stale.getId())).thenReturn(Optional.of(reread));

        service.failStaleRunning();

        assertThat(reread.getStatus()).isEqualTo(AiGenerationStatus.READY);
        assertThat(reread.getErrorCode()).isNull();
        assertThat(reread.getSession().getStatus()).isNotEqualTo(AiSessionStatus.FAILED);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void selected_recordsPackageKey() {
        AiGeneration generation = savedGeneration(AiGenerationStatus.READY);
        Tier expectedKey = Tier.PREMIUM;

        service.selected(generation.getId(), expectedKey);

        assertThat(generation.getSelectedPackageKey()).isEqualTo(expectedKey.name());
        assertThat(generation.getSelectedAt()).isNotNull();
    }

    @Test
    void edited_storesAReadyEditedRowWithTheParentsBriefAndDegradedFlag() {
        savesWithGeneratedId();
        AiSession session = session();
        AiGeneration parent = savedGeneration(AiGenerationStatus.READY, session);
        String expectedBriefSnapshot = "{\"days\":3}";
        parent.setBriefSnapshot(expectedBriefSnapshot);
        parent.setDegraded(true);
        ComposedPlan expectedPlan = new ComposedPlan(List.of(), false);
        AppliedEdit oneApplied = new AppliedEdit(EditOp.ADD, "Karting", null, Tier.PREMIUM, 1, Slot.AFTERNOON,
                UUID.randomUUID());
        EditReport expectedReport = EditReport.of(new EditOutcome(expectedPlan, List.of(oneApplied), List.of()), true);
        String expectedModel = "qwen";

        UUID editedId = service.edited(parent.getId(), expectedPlan, expectedReport,
                new LlmUsage(expectedModel, 10, 20, 500L));

        ArgumentCaptor<AiGeneration> saved = ArgumentCaptor.captor();
        verify(generationRepository).save(saved.capture());
        AiGeneration savedGeneration = saved.getValue();
        assertThat(savedGeneration.getId()).isEqualTo(editedId);
        assertThat(savedGeneration.getKind()).isEqualTo(AiGenerationKind.EDITED);
        assertThat(savedGeneration.getParentId()).isEqualTo(parent.getId());
        assertThat(savedGeneration.getStatus()).isEqualTo(AiGenerationStatus.READY);
        assertThat(savedGeneration.getBriefSnapshot()).isEqualTo(expectedBriefSnapshot);
        assertThat(savedGeneration.isDegraded()).isTrue();
        assertThat(savedGeneration.getResult()).contains("\"packages\"");
        assertThat(savedGeneration.getEditReport()).contains("\"tierRulesRelaxed\":true");
        assertThat(savedGeneration.getModel()).isEqualTo(expectedModel);
        assertThat(savedGeneration.getPromptTokens()).isEqualTo(10);
        assertThat(savedGeneration.getCompletionTokens()).isEqualTo(20);
        assertThat(savedGeneration.getCreatedAt()).isNotNull();
        assertThat(savedGeneration.getFinishedAt()).isNotNull();
        // The request thread saves the session right after the graph run; a write here would be a lost update.
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void edited_withoutModelUsage_leavesModelAndTokensNull() {
        savesWithGeneratedId();
        AiGeneration parent = savedGeneration(AiGenerationStatus.READY);
        ComposedPlan plan = new ComposedPlan(List.of(), false);
        EditReport report = EditReport.of(new EditOutcome(plan, List.of(), List.of()), false);

        service.edited(parent.getId(), plan, report, LlmUsage.none());

        ArgumentCaptor<AiGeneration> saved = ArgumentCaptor.captor();
        verify(generationRepository).save(saved.capture());
        assertThat(saved.getValue().getModel()).isNull();
        assertThat(saved.getValue().getPromptTokens()).isNull();
        assertThat(saved.getValue().getCompletionTokens()).isNull();
    }

    @Test
    void edited_whenTheParentIsGone_throwsIllegalState() {
        ComposedPlan plan = new ComposedPlan(List.of(), false);
        EditReport report = EditReport.of(new EditOutcome(plan, List.of(), List.of()), false);
        UUID missingParentId = UUID.randomUUID();

        assertThatThrownBy(() -> service.edited(missingParentId, plan, report, LlmUsage.none()))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * The edited row inherits the parent's brief snapshot and its degraded flag, so a parent that never
     * produced packages would describe the edited ones with a brief that is not theirs - and file them
     * under a generation the chat reports as failed. The edit node turns the throw into an INTERNAL report.
     */
    @Test
    void edited_whenTheParentIsNotReady_throwsIllegalState_andStoresNothing() {
        ComposedPlan plan = new ComposedPlan(List.of(), false);
        EditReport report = EditReport.of(new EditOutcome(plan, List.of(), List.of()), false);
        AiGeneration failedParent = savedGeneration(AiGenerationStatus.FAILED);

        assertThatThrownBy(() -> service.edited(failedParent.getId(), plan, report, LlmUsage.none()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AiGenerationStatus.FAILED.name());

        verify(generationRepository, never()).save(any());
    }
}
