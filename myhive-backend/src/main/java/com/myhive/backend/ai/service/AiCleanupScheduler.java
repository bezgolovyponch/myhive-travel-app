package com.myhive.backend.ai.service;

import com.myhive.backend.ai.graph.CheckpointRetention;
import com.myhive.backend.ai.graph.PlannerGraph;
import com.myhive.backend.ai.llm.AiProperties;
import com.myhive.backend.entity.AiSession;
import com.myhive.backend.repository.AiGenerationRepository;
import com.myhive.backend.repository.AiSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Retention for planner chats. A session is three things - the {@code ai_sessions} row with its
 * generations, a checkpoint thread in the saver, and an entry in {@link SessionLocks} - and all
 * three have to go, or the process keeps paying for a chat nobody will ever open again. The thread
 * takes two calls: {@code release} flags it released, {@link CheckpointRetention} deletes its rows.
 *
 * <p>The three are torn down in that order, and the order is the point: releasing a graph thread is
 * irreversible, so it must not happen until the rows it belongs to are actually gone. Hence one
 * transaction per session rather than one for the batch, with the two releases outside it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiCleanupScheduler {

    private final AiProperties properties;
    private final AiSessionRepository sessionRepository;
    private final AiGenerationRepository generationRepository;
    private final PlannerGraph graph;
    private final CheckpointRetention checkpointRetention;
    private final SessionLocks sessionLocks;
    /** This bean's own transactional proxy, resolved lazily; injecting the type directly would cycle. */
    private final ObjectProvider<AiCleanupScheduler> self;

    /** 02:45 UTC, after the trip-lead cleanup at 02:30. Deletes idle sessions and releases their graph threads. */
    @Scheduled(cron = "0 45 2 * * *", zone = "UTC")
    public void cleanupExpiredSessions() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(properties.getSessionTtlDays());
        int deleted = 0;
        for (AiSession session : sessionRepository.findByLastActivityAtBefore(cutoff)) {
            UUID token = session.getToken();
            try {
                self.getObject().deleteSession(session.getId());
            } catch (RuntimeException e) {
                // Its rows are still there, so the chat is still resumable: leave the thread and the
                // lock alone and take the next session. Class name only - no state is logged.
                log.warn("could not delete AI planner session {}: {}", session.getId(), e.getClass().getName());
                continue;
            }
            releaseThread(token);
            // Unconditional once the rows are gone: the lock entry outlives the graph thread.
            sessionLocks.release(token);
            deleted++;
        }
        log.info("Cleaned up {} AI planner sessions", deleted);
    }

    /**
     * One session's rows, in their own transaction. Public and invoked through {@link #self} so the
     * annotation is honoured; a plain {@code this.deleteSession(...)} would skip the proxy and run
     * the two deletes unrelated.
     */
    @Transactional
    public void deleteSession(UUID sessionId) {
        generationRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
    }

    private void releaseThread(UUID token) {
        try {
            graph.release(token);
            // release() only flags the thread released; the rows themselves have to be deleted or the
            // checkpoint tables outlive every chat they belong to.
            checkpointRetention.deleteThread(token);
        } catch (RuntimeException e) {
            // A thread the saver has already forgotten (released, or never checkpointed) is not a
            // reason to leave the rest of the batch behind. The cost of failing here is checkpoint
            // rows nothing points at any more, which no later run will find because the session row
            // is already gone - worth a WARN, not an abort. Class name only - no state is logged.
            log.warn("could not release planner thread {}: {}", token, e.getClass().getName());
        }
    }
}
