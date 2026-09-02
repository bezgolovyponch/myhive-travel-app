package com.myhive.backend.service;

import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityVoteCount;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.util.Translations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Organizer progress emails during an open vote, one shot each, idempotent through the
 * {@code *_sent_at} markers on the session. Driven by {@link VoteSessionScheduler}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoteProgressNotifier {

    /** The "people have not voted yet" reminder goes out once this much of the window is left. */
    static final Duration REMINDER_LEAD = Duration.ofHours(12);

    private final VoteSessionRepository voteSessionRepository;
    private final VoteSessionActivityRepository voteSessionActivityRepository;
    private final VoteActivityLikeRepository voteActivityLikeRepository;
    private final EmailService emailService;

    @Value("${app.frontend.url:https://trivlu.com}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<UUID> halfwayCandidateIds() {
        return idsOf(voteSessionRepository
                .findByStatusAndInitiatorEmailIsNotNullAndHalfwayEmailSentAtIsNull(VoteSessionStatus.ACTIVE));
    }

    @Transactional(readOnly = true)
    public List<UUID> reminderCandidateIds() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).plus(REMINDER_LEAD);
        return idsOf(voteSessionRepository
                .findByStatusAndInitiatorEmailIsNotNullAndReminderEmailSentAtIsNullAndExpiresAtBefore(
                        VoteSessionStatus.ACTIVE, cutoff));
    }

    /** Email 1: fires once when at least half the group (ceil) has voted and not everyone has. */
    @Transactional
    public void sendHalfwayIfDue(UUID sessionId) {
        VoteSession session = openSessionWithEmail(sessionId);
        if (session == null || session.getHalfwayEmailSentAt() != null) {
            return;
        }
        long voters = voterCount(session);
        int travelers = session.getNumberOfTravelers();
        if (voters < halfOf(travelers) || voters >= travelers) {
            return;
        }
        List<EmailService.VoteStandingView> standings = standingsOf(session);
        session.setHalfwayEmailSentAt(LocalDateTime.now(ZoneOffset.UTC));
        voteSessionRepository.saveAndFlush(session);
        sendQuietly("halfway", session,
                () -> emailService.sendVoteHalfway(session, voters, standings, frontendUrl));
    }

    /** Email 2: fires once when 12 h or less remain and somebody still has not voted. */
    @Transactional
    public void sendReminderIfDue(UUID sessionId) {
        VoteSession session = openSessionWithEmail(sessionId);
        if (session == null || session.getReminderEmailSentAt() != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (session.getExpiresAt().isAfter(now.plus(REMINDER_LEAD))) {
            return; // more than 12 h left — not due yet
        }
        long missing = session.getNumberOfTravelers() - voterCount(session);
        if (missing <= 0) {
            return;
        }
        session.setReminderEmailSentAt(now);
        voteSessionRepository.saveAndFlush(session);
        sendQuietly("reminder", session, () -> emailService.sendVoteReminder(session, missing, frontendUrl));
    }

    /** ceil(travelers / 2): the halfway line for a group of that size. */
    static int halfOf(int travelers) {
        return (travelers + 1) / 2;
    }

    private VoteSession openSessionWithEmail(UUID sessionId) {
        VoteSession session = voteSessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getStatus() != VoteSessionStatus.ACTIVE
                || session.getInitiatorEmail() == null || session.getInitiatorEmail().isBlank()) {
            return null;
        }
        return session;
    }

    private long voterCount(VoteSession session) {
        return voteActivityLikeRepository.countDistinctVoterTokensBySessionId(session.getId());
    }

    private List<EmailService.VoteStandingView> standingsOf(VoteSession session) {
        List<VoteSessionActivity> ballot =
                voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());
        Map<UUID, ActivityVoteCount> counts = voteActivityLikeRepository
                .findVoteCountsBySessionId(session.getId()).stream()
                .collect(Collectors.toMap(ActivityVoteCount::getActivityId, c -> c));
        String lc = Translations.normalize(session.getLocale());
        return ballot.stream()
                .sorted(VoteRanking.byLikes(counts))
                .map(row -> new EmailService.VoteStandingView(
                        Translations.pick(row.getActivity().getTranslations(), lc, "name", row.getActivityName()),
                        VoteRanking.likeCountOf(counts, row)))
                .toList();
    }

    private void sendQuietly(String kind, VoteSession session, Runnable send) {
        try {
            send.run();
        } catch (Exception e) {
            // The marker above must commit even if the hand-off fails, or the next tick would resend forever.
            log.error("Failed to send vote {} email for session {}: {}", kind, session.getId(), e.getMessage(), e);
        }
    }

    private static List<UUID> idsOf(List<VoteSession> sessions) {
        return sessions.stream().map(VoteSession::getId).toList();
    }
}
