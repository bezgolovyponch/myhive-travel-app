package com.myhive.backend.service;

import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.VoteSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoteSessionScheduler {

    private final VoteSessionRepository voteSessionRepository;
    private final VoteSessionService voteSessionService;
    private final VoteProgressNotifier voteProgressNotifier;

    /** Kill switch for the organizer progress emails only; creation/result emails are unaffected. */
    @Value("${app.vote.organizer-emails-enabled:true}")
    private boolean organizerEmailsEnabled;

    /** With the mailer off, ticking would silently burn the one-shot markers — skip instead. */
    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

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

    /** Organizer progress emails (halfway + 12 h reminder); each notifier call owns its transaction. */
    @Scheduled(fixedDelay = 300_000)
    public void sendOrganizerProgressEmails() {
        if (!organizerEmailsEnabled || !emailEnabled) {
            return;
        }
        for (UUID sessionId : voteProgressNotifier.reminderCandidateIds()) {
            runQuietly("reminder", sessionId, voteProgressNotifier::sendReminderIfDue);
        }
        for (UUID sessionId : voteProgressNotifier.halfwayCandidateIds()) {
            runQuietly("halfway", sessionId, voteProgressNotifier::sendHalfwayIfDue);
        }
    }

    private static void runQuietly(String kind, UUID sessionId, Consumer<UUID> action) {
        try {
            action.accept(sessionId);
        } catch (Exception e) {
            log.error("Failed to process vote {} email for session {}: {}", kind, sessionId, e.getMessage(), e);
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
