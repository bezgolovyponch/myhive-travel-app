package com.myhive.backend.ai.service;

import com.myhive.backend.ai.graph.PlannerGraph;
import com.myhive.backend.ai.llm.AiProperties;
import com.myhive.backend.entity.AiSession;
import com.myhive.backend.repository.AiGenerationRepository;
import com.myhive.backend.repository.AiSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Retention for planner chats. A session is three things - the {@code ai_sessions} row with its
 * generations, a checkpoint thread in the saver, and an entry in {@link SessionLocks} - and all
 * three have to go, or the process keeps paying for a chat nobody will ever open again.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiCleanupScheduler {

    private final AiProperties properties;
    private final AiSessionRepository sessionRepository;
    private final AiGenerationRepository generationRepository;
    private final PlannerGraph graph;
    private final SessionLocks sessionLocks;

    /** 02:45 UTC, after the trip-lead cleanup at 02:30. Deletes idle sessions and releases their graph threads. */
    @Scheduled(cron = "0 45 2 * * *", zone = "UTC")
    @Transactional
    public void cleanupExpiredSessions() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(properties.getSessionTtlDays());
        int deleted = 0;
        for (AiSession session : sessionRepository.findByLastActivityAtBefore(cutoff)) {
            UUID token = session.getToken();
            generationRepository.deleteBySessionId(session.getId());
            sessionRepository.delete(session);
            try {
                graph.release(token);
            } catch (RuntimeException e) {
                // A thread the saver has already forgotten (released, or never checkpointed) is not a
                // reason to leave the rest of the batch behind. Class name only - no state is logged.
                log.warn("could not release planner thread {}: {}", token, e.toString());
            }
            // Unconditional: the lock entry outlives the graph thread, and the session is gone either way.
            sessionLocks.release(token);
            deleted++;
        }
        log.info("Cleaned up {} AI planner sessions", deleted);
    }
}
