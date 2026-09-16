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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
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

    /** A generation that has not reported back by now lost its thread; nothing takes this long. */
    static final int STALE_AFTER_MINUTES = 3;

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
        // The graph persists nothing it cannot attribute, so the id must be in the state before the
        // job can resume the thread - and a pool thread can start before execute() returns.
        stampGenerationId(session.getToken(), generationId);
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
            session.setStatus(AiSessionStatus.FAILED);
            generationRepository.save(generation);
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
        UUID token = generation.getSession().getToken();
        markRunning(generation);
        try {
            // GENERATE is the only reason that reaches the generation branch, and this is the only
            // place that writes it - which is what keeps a chat turn or a selection from building a plan.
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
            self.getObject().fail(generationId, "INTERNAL");
        }
    }

    @Override
    @Transactional
    public void ready(UUID generationId, ComposedPlan plan, boolean degraded, LlmUsage usage, int attempt) {
        AiGeneration generation = generationRepository.findById(generationId).orElseThrow();
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
    }

    @Override
    @Transactional
    public void selected(UUID generationId, Tier key) {
        AiGeneration generation = generationRepository.findById(generationId).orElseThrow();
        generation.setSelectedPackageKey(key.name());
        generation.setSelectedAt(LocalDateTime.now(ZoneOffset.UTC));
        generationRepository.save(generation);
    }

    /** Sweeps rows whose job thread died mid-run, so a chat is never stuck on "building your packages". */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void failStaleRunning() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(STALE_AFTER_MINUTES);
        for (AiGeneration generation : generationRepository
                .findByStatusAndStartedAtBefore(AiGenerationStatus.RUNNING, cutoff)) {
            log.warn("planner generation {} is stale, marking it failed", generation.getId());
            self.getObject().fail(generation.getId(), "STALE");
        }
    }

    private void stampGenerationId(UUID token, UUID generationId) {
        graph.update(token, Map.of(PlannerState.GENERATION_ID, generationId.toString()));
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
        generationRepository.findById(generationId).ifPresent(generation -> {
            generation.setStatus(AiGenerationStatus.FAILED);
            generation.setErrorCode(errorCode);
            generation.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
            generationRepository.save(generation);
            sessionRepository.findById(generation.getSession().getId()).ifPresent(session -> {
                session.setStatus(AiSessionStatus.FAILED);
                sessionRepository.save(session);
            });
        });
    }

    private void save(AiGeneration generation) {
        generationRepository.save(generation);
        sessionRepository.save(generation.getSession());
    }
}
