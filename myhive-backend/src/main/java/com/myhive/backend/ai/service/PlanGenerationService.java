package com.myhive.backend.ai.service;

import com.myhive.backend.ai.exception.AiLimitException;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerGraph;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.graph.ResumeReason;
import com.myhive.backend.ai.graph.nodes.PersistResultNode;
import com.myhive.backend.ai.graph.nodes.SelectNode;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.entity.AiGeneration;
import com.myhive.backend.entity.AiGenerationStatus;
import com.myhive.backend.entity.AiSession;
import com.myhive.backend.entity.AiSessionStatus;
import com.myhive.backend.repository.AiGenerationRepository;
import com.myhive.backend.repository.AiSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Owns everything about one plan generation: the {@code ai_generations} row, the background job that
 * resumes the parked graph thread, and the two callbacks the graph uses to report back. It is the
 * single bean implementing {@link PersistResultNode.GenerationResultSink} and
 * {@link SelectNode.SelectionSink} — a second candidate would make the nodes' {@code getIfUnique()}
 * lookup fall back to a logging no-op and silently drop every plan.
 */
@Service
@Slf4j
public class PlanGenerationService implements PersistResultNode.GenerationResultSink, SelectNode.SelectionSink {

    /** A RUNNING generation that has not reported back by now lost its thread; nothing runs this long. */
    static final int STALE_AFTER_MINUTES = 3;

    /** Asked after every failure: does this chat still have packages to fall back to? */
    private static final List<AiGenerationStatus> READY_ONLY = List.of(AiGenerationStatus.READY);

    /**
     * When this JVM started. The QUEUED sweep ages rows against it rather than against a duration,
     * so a long but honest queue wait is never mistaken for an orphan.
     */
    static final LocalDateTime PROCESS_STARTED_AT = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()), ZoneOffset.UTC);

    /**
     * The generations this JVM's pool threads are running right now, so the sweep can tell a slow job
     * from a lost one. A job that is merely slow is still holding the graph thread inside the
     * generation branch when the stale clock runs out; failing its row stops holding requests off, and
     * the next user message then re-parks the thread and runs a chat turn underneath the live job -
     * whose next node checkpoint is pushed on top of both, taking the message and its reply out of the
     * transcript. Bounded by the model deadlines: at most one compose and one repair, each capped at
     * {@code app.ai.planner-timeout} (60 s by default) by a hard {@code CompletableFuture.get}, so an
     * owned run cannot outlive roughly two minutes of model time and can never sit here for ever.
     * Entries are given back in a {@code finally}, and the only way one survives that is the JVM dying
     * with it - which empties the set and is exactly when RUNNING rows really are orphans.
     */
    private final Set<UUID> ownedRuns = ConcurrentHashMap.newKeySet();

    private final AiGenerationRepository generationRepository;
    private final AiSessionRepository sessionRepository;
    private final PlannerGraph graph;
    private final Executor executor;
    /** This bean's own transactional proxy, resolved lazily; injecting the type directly would cycle. */
    private final ObjectProvider<PlanGenerationService> self;

    public PlanGenerationService(AiGenerationRepository generationRepository, AiSessionRepository sessionRepository,
            PlannerGraph graph, @Qualifier("aiTaskExecutor") Executor executor,
            ObjectProvider<PlanGenerationService> self) {
        this.generationRepository = generationRepository;
        this.sessionRepository = sessionRepository;
        this.graph = graph;
        this.executor = executor;
        this.self = self;
    }

    /**
     * Creates the QUEUED row and hands the job to the planner pool. Deliberately not
     * {@code @Transactional}: the job thread looks the row up by id, so the insert has to be
     * committed before {@code execute} is called, and an enclosing request transaction would still
     * be open at that point. The caller persists the session counters it mutates here.
     */
    public AiGeneration enqueue(AiSession session, Brief brief) {
        AiGeneration generation = new AiGeneration();
        generation.setSession(session);
        generation.setStatus(AiGenerationStatus.QUEUED);
        generation.setBriefSnapshot(JsonCodec.write(brief));
        generation.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        generation = generationRepository.save(generation);

        UUID generationId = generation.getId();
        session.setGenerationCount(session.getGenerationCount() + 1);
        session.setStatus(AiSessionStatus.GENERATING);
        sessionRepository.save(session);
        // Deliberately no graph write here. The id belongs in the state before the thread is resumed,
        // and runJob writes it together with RESUME_REASON=GENERATE immediately before resuming -
        // nothing reads it in between (turn() builds its view before startGeneration, and select() is
        // locked out while a row is in flight). Stamping it here instead put a graph call after the
        // committed QUEUED row: a failure there left a row no job owned, which the sweeper ignores
        // because it was created after this process started, and every later message or selection
        // answered 409 GENERATION_IN_PROGRESS until the next restart.
        try {
            // Last statement on purpose: from here the job thread owns both the row and the graph thread.
            executor.execute(() -> runJob(generationId));
        } catch (RejectedExecutionException e) {
            // The pool is full and no job exists, so this thread can still undo it all. The group's
            // generation allowance is given back - "retry in a few seconds" must not cost one.
            session.setGenerationCount(session.getGenerationCount() - 1);
            generation.setStatus(AiGenerationStatus.FAILED);
            generation.setErrorCode("AI_BUSY");
            generation.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
            generationRepository.save(generation);
            session.setStatus(statusAfterAFailedGeneration(session.getId()));
            sessionRepository.save(session);
            throw new AiLimitException("AI_BUSY", "The planner is busy, please retry in a few seconds");
        }
        return generation;
    }

    /**
     * Runs on {@code aiTaskExecutor}: resumes the parked thread and lets the graph's persistResult
     * node call {@link #ready}. Compose and repair failures never reach here — the graph absorbs
     * them into a degraded fallback plan — so anything caught is an unexpected internal fault.
     */
    public void runJob(UUID generationId) {
        AiGeneration generation = generationRepository.findWithSessionById(generationId).orElse(null);
        if (generation == null) {
            log.warn("planner job skipped: generation {} no longer exists", generationId);
            return;
        }
        AiSession session = generation.getSession();
        UUID token = session.getToken();
        // Claimed before the row says RUNNING, so the sweep never sees it unowned.
        ownedRuns.add(generationId);
        try {
            markRunning(generation);
            enterThroughAwaitGeneration(session);
            // GENERATE is the only reason that reaches the generation branch, and this is the only
            // resume that runs with GENERATE *from* awaitGeneration - which is what keeps a chat turn
            // or a selection from building a plan. Every resume stamps its own reason (requestGeneration
            // writes GENERATE too, but only to walk a parked thread forward to awaitGeneration).
            graph.update(token, Map.of(
                    PlannerState.GENERATION_ID, generationId.toString(),
                    PlannerState.RESUME_REASON, ResumeReason.GENERATE.name()));
            graph.runUntilInterrupt(token);
            if (statusOf(generationId) == AiGenerationStatus.RUNNING) {
                // The thread parked without reaching persistResult; nothing will ever store a plan.
                log.error("planner generation {} finished without a result", generationId);
                self.getObject().fail(generationId, "INTERNAL");
            }
        } catch (RuntimeException e) {
            log.error("planner generation {} failed: {}", generationId, e.getClass().getName(), e);
            // Not fail(): persistResult commits READY in its own transaction while the resume is still
            // running, so anything that blows up afterwards (parking the thread, the checkpoint write)
            // would otherwise take three finished packages off the group's screen.
            self.getObject().failIfStillInFlight(generationId, "INTERNAL");
        } finally {
            // Whatever happened, including an Error on the way out: the row is closed out or genuinely
            // abandoned by now, and either way the sweep may have it back.
            ownedRuns.remove(generationId);
        }
        // Deliberately no re-park here. This thread is outside SessionLocks and its row has stopped
        // holding requests off, so a write from here could land on top of a request that is already
        // running a turn. The four resume paths each re-park before they touch the graph, which is
        // both sufficient and properly serialised.
    }

    /**
     * Re-parks a graph thread that a run which never finished left inside the graph, so that the next
     * resume starts a turn instead of continuing it. Safe here because no QUEUED or RUNNING row exists
     * for the session: the request paths refuse with GENERATION_IN_PROGRESS while one does, and a job
     * calling this owns the only run there is. See {@link PlannerGraph#ensureParked}.
     *
     * @return the node the thread was found at, when it had to be re-parked
     */
    public Optional<String> ensureParked(AiSession session) {
        return graph.ensureParked(session.getToken(), () -> lastDeliveredBrief(session.getId()));
    }

    /**
     * A job must start from awaitGeneration - the only park point GENERATE turns into a plan. A thread
     * that had to be re-parked comes back at awaitUser instead, one GENERATE hop short; the hop
     * executes nothing but the park node itself, so it costs no model call. Without it the resume
     * below would simply park again and the job would report "finished without a result".
     *
     * <p>The trigger is where the thread actually is, not whether this call re-parked it: the same
     * hop is what a thread left at awaitUser or awaitSelection by anything else needs.
     */
    private void enterThroughAwaitGeneration(AiSession session) {
        ensureParked(session);
        UUID token = session.getToken();
        if (PlannerGraph.AWAIT_GENERATION.equals(graph.snapshot(token).next())) {
            return;
        }
        graph.update(token, Map.of(PlannerState.RESUME_REASON, ResumeReason.GENERATE.name()));
        graph.runUntilInterrupt(token);
    }

    /**
     * The brief the newest generation that actually delivered packages was built from, or {@code null}
     * when this chat has none. It is the same JSON {@code snapshotCatalog} stamps as
     * {@code LAST_GENERATED_BRIEF} - both write the same {@link Brief} through {@link JsonCodec} - and
     * if the two ever drifted apart the only cost would be one regeneration the chat did not need.
     */
    private String lastDeliveredBrief(UUID sessionId) {
        return generationRepository
                .findFirstBySessionIdAndStatusOrderByCreatedAtDesc(sessionId, AiGenerationStatus.READY)
                .map(AiGeneration::getBriefSnapshot)
                .orElse(null);
    }

    /**
     * The mirror image of {@link #failIfStillInFlight}: that one refuses to fail a row that finished,
     * this one refuses to finish a row that was already failed. Defence in depth behind
     * {@link #ownedRuns} and {@link PlannerGraph#ensureParked} - the group has been told this
     * generation is gone and the chat may already be FAILED, so a plan arriving from a run nobody
     * owns any more is dropped rather than put back on the screen. Answering {@code false} is what
     * lets the graph unwind its own "these packages exist" stamp.
     */
    @Override
    @Transactional
    public boolean ready(UUID generationId, ComposedPlan plan, boolean degraded, LlmUsage usage, int attempt) {
        AiGeneration generation = generationRepository.findById(generationId).orElseThrow();
        if (generation.getStatus() == AiGenerationStatus.FAILED) {
            log.warn("planner result dropped generation={}: the row is already FAILED", generationId);
            return false;
        }
        generation.setStatus(AiGenerationStatus.READY);
        generation.setResult(JsonCodec.write(plan));
        generation.setDegraded(degraded);
        generation.setModel(usage.model());
        generation.setPromptTokens(usage.promptTokens());
        generation.setCompletionTokens(usage.completionTokens());
        generation.setLatencyMs((int) Math.min(Integer.MAX_VALUE, usage.latencyMs()));
        generation.setAttempt((short) attempt);
        generation.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        generation.getSession().setStatus(AiSessionStatus.READY);
        save(generation);
        return true;
    }

    @Override
    @Transactional
    public void selected(UUID generationId, Tier key) {
        AiGeneration generation = generationRepository.findById(generationId).orElseThrow();
        generation.setSelectedPackageKey(key.name());
        generation.setSelectedAt(LocalDateTime.now(ZoneOffset.UTC));
        generationRepository.save(generation);
    }

    /**
     * Sweeps rows no job thread owns any more, so a chat is never stuck on "building your packages".
     * Two ways to end up there, and they age on different clocks.
     *
     * <p>RUNNING means a thread took the row and died mid-run, so {@code startedAt} older than
     * {@link #STALE_AFTER_MINUTES} is the giveaway - unless this process is still running it, which
     * {@link #ownedRuns} answers. QUEUED cannot use a duration at all: two workers
     * over a twenty-deep queue at up to three minutes a job means an honest wait of well over three
     * minutes is routine, and sweeping on age would fail live generations, burn the group's slot and
     * flip the chat to FAILED while the job went on to produce a plan. A QUEUED row is an orphan only
     * if it belongs to a <em>previous</em> process: nothing this JVM queued can predate its own start,
     * and anything still QUEUED from before it never had a thread to lose.
     */
    @Scheduled(fixedDelay = 60_000)
    public void failStaleRunning() {
        LocalDateTime runningCutoff = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(STALE_AFTER_MINUTES);
        failStale(generationRepository.findByStatusAndStartedAtBefore(AiGenerationStatus.RUNNING, runningCutoff)
                .stream()
                .filter(generation -> !ownedRuns.contains(generation.getId()))
                .toList());
        // No such filter on QUEUED: nothing this process queued can predate its own start, so a row
        // one of its threads is about to pick up is never selected in the first place.
        failStale(generationRepository.findByStatusAndCreatedAtBefore(AiGenerationStatus.QUEUED, PROCESS_STARTED_AT));
    }

    private void failStale(List<AiGeneration> generations) {
        for (AiGeneration generation : generations) {
            log.warn("planner generation {} is stale, marking it failed", generation.getId());
            self.getObject().failIfStillInFlight(generation.getId(), "STALE");
        }
    }

    private AiGenerationStatus statusOf(UUID generationId) {
        // Re-read: persistResult commits in its own transaction, so the row this thread holds is stale.
        return generationRepository.findById(generationId).map(AiGeneration::getStatus).orElse(null);
    }

    private void markRunning(AiGeneration generation) {
        generation.setStatus(AiGenerationStatus.RUNNING);
        generation.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        generationRepository.save(generation);
    }

    /**
     * Closes a generation out. Both rows are re-read by id and only the fields this call owns are
     * touched: the instance the job has been holding since it started is minutes stale, and merging it
     * would rewind the {@code messageCount} / {@code generationCount} / {@code lastActivityAt} the
     * request that enqueued the job wrote afterwards - every failed generation would refund the limits.
     *
     * <p>Public and invoked through {@link #self} so the annotation is honoured on the job thread; a
     * plain {@code this.fail(...)} would skip the proxy and leave the two writes unrelated.
     */
    @Transactional
    public void fail(UUID generationId, String errorCode) {
        generationRepository.findById(generationId).ifPresent(generation -> closeOut(generation, errorCode));
    }

    /**
     * The sweeper's write, and the reason {@link #failStaleRunning()} is deliberately <em>not</em>
     * {@code @Transactional}: this method needs a persistence context of its own. Minutes pass
     * between the sweep's query and this call, and a generation that reached READY in between has
     * already put three packages on the group's screen — closing it out as FAILED would take them
     * away again. Sharing the sweeper's transaction would defeat the check outright, since the
     * re-read would come back identity-mapped from the very list that selected the row.
     */
    @Transactional
    public void failIfStillInFlight(UUID generationId, String errorCode) {
        generationRepository.findById(generationId).ifPresent(generation -> {
            AiGenerationStatus status = generation.getStatus();
            if (status != AiGenerationStatus.QUEUED && status != AiGenerationStatus.RUNNING) {
                log.debug("planner generation {} finished as {} while the sweep ran, leaving it alone",
                        generationId, status);
                return;
            }
            closeOut(generation, errorCode);
        });
    }

    private void closeOut(AiGeneration generation, String errorCode) {
        generation.setStatus(AiGenerationStatus.FAILED);
        generation.setErrorCode(errorCode);
        generation.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        generationRepository.save(generation);
        sessionRepository.findById(generation.getSession().getId()).ifPresent(session -> {
            session.setStatus(statusAfterAFailedGeneration(session.getId()));
            sessionRepository.save(session);
        });
    }

    /**
     * A failed generation must not take away packages an earlier one already delivered. The chat stays
     * READY whenever any READY generation survives - the failing row has been saved as FAILED by the
     * time this is asked, so it can never count itself - and the UI restores from
     * {@code latestReadyGeneration} while {@code latestGeneration} shows what went wrong.
     */
    private AiSessionStatus statusAfterAFailedGeneration(UUID sessionId) {
        return generationRepository.existsBySessionIdAndStatusIn(sessionId, READY_ONLY)
                ? AiSessionStatus.READY
                : AiSessionStatus.FAILED;
    }

    private void save(AiGeneration generation) {
        generationRepository.save(generation);
        sessionRepository.save(generation.getSession());
    }
}
