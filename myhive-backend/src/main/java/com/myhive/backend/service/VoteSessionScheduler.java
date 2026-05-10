package com.myhive.backend.service;

import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.VoteSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoteSessionScheduler {

    private final VoteSessionRepository voteSessionRepository;
    private final VoteSessionService voteSessionService;

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void processExpiredSessions() {
        List<VoteSession> expired = voteSessionRepository
                .findByStatusAndExpiresAtBefore(VoteSessionStatus.ACTIVE, LocalDateTime.now(ZoneOffset.UTC));

        for (VoteSession session : expired) {
            try {
                voteSessionService.processSession(session);
            } catch (Exception e) {
                log.error("Failed to process vote session {}: {}", session.getId(), e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldSessions() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(7);
        int deleted = voteSessionRepository
                .deleteByStatusAndExpiresAtBefore(VoteSessionStatus.COMPLETED, cutoff);
        log.info("Cleaned up {} completed vote sessions", deleted);
    }
}
