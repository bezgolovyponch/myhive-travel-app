package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.MockEmailServiceConfig;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// Not @Transactional: the point of these tests is that the one-shot marker is COMMITTED before
// the email hand-off, which a shared test transaction would hide (and the notifier's own
// transaction per call is what makes the second call a no-op). State is cleaned per test instead.
@SpringBootTest
@Import({TestSecurityConfig.class, MockEmailServiceConfig.class})
class VoteProgressNotifierTest {

    @Autowired private VoteProgressNotifier notifier;
    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private EmailService emailService;

    private final List<UUID> createdSessionIds = new ArrayList<>();

    private Destination prague;
    private Activity barCrawl;
    private Activity karting;

    @BeforeEach
    void setUp() {
        reset(emailService);
        createdSessionIds.clear();
        prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        karting = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Karting", new BigDecimal("60.00")));
    }

    // Rows outlive the test without a rollback, and another test class wipes activities and
    // destinations wholesale — leftover sessions would break its deleteAll with an FK violation.
    @AfterEach
    void tearDown() {
        voteSessionRepository.deleteAllById(createdSessionIds);
    }

    private static LocalDateTime hoursFromNow(int hours) {
        return LocalDateTime.now(ZoneOffset.UTC).plusHours(hours);
    }

    private VoteSession activeSession(int travelers, LocalDateTime expiresAt, String email) {
        VoteSessionCartCreateRequest request = new VoteSessionCartCreateRequest();
        request.setDestinationId(prague.getId());
        request.setInitiatorEmail(email);
        request.setNumberOfTravelers(travelers);
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setEndDate(LocalDate.of(2026, 8, 3));
        request.setActivityIds(Arrays.asList(barCrawl.getId(), karting.getId()));
        VoteSessionResponse response = voteSessionService.createCartSession(request);
        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        createdSessionIds.add(session.getId());
        session.setExpiresAt(expiresAt);
        return voteSessionRepository.saveAndFlush(session);
    }

    /** One new participant likes the given activities and skips the rest of the ballot. */
    private void vote(VoteSession session, Activity... liked) {
        List<Activity> likedList = Arrays.asList(liked);
        VoteBatchRequest batch = new VoteBatchRequest();
        batch.setVoterToken(UUID.randomUUID());
        batch.setVotes(List.of(
                voteItem(barCrawl, likedList.contains(barCrawl)),
                voteItem(karting, likedList.contains(karting))));
        voteSessionService.castVotes(session.getShareToken(), batch);
    }

    private static VoteBatchRequest.VoteItem voteItem(Activity activity, boolean liked) {
        VoteBatchRequest.VoteItem item = new VoteBatchRequest.VoteItem();
        item.setActivityId(activity.getId());
        item.setLiked(liked);
        return item;
    }

    /** Fresh read outside the notifier's transaction — proves the marker was committed, not just set. */
    private VoteSession reload(VoteSession session) {
        return voteSessionRepository.findById(session.getId()).orElseThrow();
    }

    // ---- halfway ------------------------------------------------------------

    @Test
    void halfway_sentOnceWithRankedStandingsWhenHalfTheGroupVoted() {
        long expectedVoters = 2L;
        VoteSession session = activeSession(4, hoursFromNow(20), "organizer@example.com");
        vote(session, karting);
        vote(session, karting, barCrawl);

        notifier.sendHalfwayIfDue(session.getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EmailService.VoteStandingView>> standingsCaptor = ArgumentCaptor.forClass(List.class);
        verify(emailService).sendVoteHalfway(any(), eq(expectedVoters), standingsCaptor.capture(), anyString());
        assertThat(standingsCaptor.getValue()).extracting(row -> row.name).containsExactly("Karting", "Bar Crawl");
        assertThat(standingsCaptor.getValue()).extracting(row -> row.likes).containsExactly(2L, 1L);
        assertThat(reload(session).getHalfwayEmailSentAt()).isNotNull();
    }

    @Test
    void halfway_notSentBelowTheHalfwayLine() {
        VoteSession session = activeSession(4, hoursFromNow(20), "organizer@example.com");
        vote(session, karting);

        notifier.sendHalfwayIfDue(session.getId());

        verify(emailService, never()).sendVoteHalfway(any(), anyLong(), anyList(), anyString());
        assertThat(reload(session).getHalfwayEmailSentAt()).isNull();
    }

    @Test
    void halfway_notSentOnceEveryoneVoted() {
        VoteSession session = activeSession(2, hoursFromNow(20), "organizer@example.com");
        vote(session, karting);
        vote(session, barCrawl);

        notifier.sendHalfwayIfDue(session.getId());

        verify(emailService, never()).sendVoteHalfway(any(), anyLong(), anyList(), anyString());
    }

    @Test
    void halfway_neverFiresForASoloTraveler() {
        VoteSession session = activeSession(1, hoursFromNow(20), "organizer@example.com");
        vote(session, karting);

        notifier.sendHalfwayIfDue(session.getId());

        verify(emailService, never()).sendVoteHalfway(any(), anyLong(), anyList(), anyString());
    }

    @Test
    void halfway_secondCallDoesNotResend() {
        VoteSession session = activeSession(2, hoursFromNow(20), "organizer@example.com");
        vote(session, karting);

        notifier.sendHalfwayIfDue(session.getId());
        notifier.sendHalfwayIfDue(session.getId());

        verify(emailService, times(1)).sendVoteHalfway(any(), anyLong(), anyList(), anyString());
    }

    @Test
    void halfway_markerStaysSetWhenTheSendThrows() {
        VoteSession session = activeSession(2, hoursFromNow(20), "organizer@example.com");
        vote(session, karting);
        doThrow(new RuntimeException("smtp down")).when(emailService)
                .sendVoteHalfway(any(), anyLong(), anyList(), anyString());

        notifier.sendHalfwayIfDue(session.getId());

        assertThat(reload(session).getHalfwayEmailSentAt()).isNotNull();
    }

    @Test
    void halfwayCandidates_excludeSessionsWithoutEmailCompletedOnesAndAlreadyNotified() {
        VoteSession due = activeSession(2, hoursFromNow(20), "organizer@example.com");
        VoteSession noEmail = activeSession(2, hoursFromNow(20), null);
        VoteSession completed = activeSession(2, hoursFromNow(20), "done@example.com");
        completed.setStatus(VoteSessionStatus.COMPLETED);
        voteSessionRepository.saveAndFlush(completed);
        VoteSession notified = activeSession(2, hoursFromNow(20), "seen@example.com");
        notified.setHalfwayEmailSentAt(LocalDateTime.now(ZoneOffset.UTC));
        voteSessionRepository.saveAndFlush(notified);

        List<UUID> ids = notifier.halfwayCandidateIds();

        assertThat(ids).contains(due.getId())
                .doesNotContain(noEmail.getId(), completed.getId(), notified.getId());
    }

    // ---- reminder -----------------------------------------------------------

    @Test
    void reminder_sentOnceWithMissingCountWhenTwelveHoursAreLeft() {
        long expectedMissing = 3L;
        VoteSession session = activeSession(4, hoursFromNow(11), "organizer@example.com");
        vote(session, karting);

        notifier.sendReminderIfDue(session.getId());

        verify(emailService).sendVoteReminder(any(), eq(expectedMissing), anyString());
        assertThat(reload(session).getReminderEmailSentAt()).isNotNull();
    }

    @Test
    void reminder_notDueWhileMoreThanTwelveHoursAreLeft() {
        VoteSession session = activeSession(4, hoursFromNow(13), "organizer@example.com");

        notifier.sendReminderIfDue(session.getId());

        verify(emailService, never()).sendVoteReminder(any(), anyLong(), anyString());
        assertThat(notifier.reminderCandidateIds()).doesNotContain(session.getId());
    }

    @Test
    void reminder_notSentWhenEveryoneVoted() {
        VoteSession session = activeSession(1, hoursFromNow(11), "organizer@example.com");
        vote(session, karting);

        notifier.sendReminderIfDue(session.getId());

        verify(emailService, never()).sendVoteReminder(any(), anyLong(), anyString());
        assertThat(reload(session).getReminderEmailSentAt()).isNull();
    }

    @Test
    void reminder_secondCallDoesNotResend() {
        VoteSession session = activeSession(4, hoursFromNow(11), "organizer@example.com");

        notifier.sendReminderIfDue(session.getId());
        notifier.sendReminderIfDue(session.getId());

        verify(emailService, times(1)).sendVoteReminder(any(), anyLong(), anyString());
    }

    @Test
    void reminderCandidates_includeOnlyDueEmailedActiveSessions() {
        VoteSession due = activeSession(4, hoursFromNow(11), "organizer@example.com");
        VoteSession early = activeSession(4, hoursFromNow(13), "early@example.com");
        VoteSession noEmail = activeSession(4, hoursFromNow(11), null);

        List<UUID> ids = notifier.reminderCandidateIds();

        assertThat(ids).contains(due.getId()).doesNotContain(early.getId(), noEmail.getId());
    }

    // ---- one-shot claim (the guard against the organizer closing mid-send) ----

    // @Transactional here only because a @Modifying query needs one; these two assert the
    // claim's WHERE clause directly, which is what keeps a stale snapshot from resurrecting
    // a session the organizer closed between the notifier's read and its write.
    @Test
    @Transactional
    void claimHalfwayEmail_winsOnceThenLoses() {
        VoteSession session = activeSession(4, hoursFromNow(20), "organizer@example.com");

        int firstClaim = voteSessionRepository.claimHalfwayEmail(
                session.getId(), VoteSessionStatus.ACTIVE, LocalDateTime.now(ZoneOffset.UTC));
        int secondClaim = voteSessionRepository.claimHalfwayEmail(
                session.getId(), VoteSessionStatus.ACTIVE, LocalDateTime.now(ZoneOffset.UTC));

        assertThat(firstClaim).isEqualTo(1);
        assertThat(secondClaim).isZero();
    }

    @Test
    @Transactional
    void claimHalfwayEmail_refusesAClosedSessionAndLeavesItsStatusAlone() {
        VoteSession session = activeSession(4, hoursFromNow(20), "organizer@example.com");
        session.setStatus(VoteSessionStatus.COMPLETED); // organizer ended the vote early
        voteSessionRepository.saveAndFlush(session);

        int claimed = voteSessionRepository.claimHalfwayEmail(
                session.getId(), VoteSessionStatus.ACTIVE, LocalDateTime.now(ZoneOffset.UTC));

        assertThat(claimed).isZero();
        assertThat(voteSessionRepository.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(VoteSessionStatus.COMPLETED);
    }

    @Test
    void halfOf_roundsUp() {
        assertThat(VoteProgressNotifier.halfOf(12)).isEqualTo(6);
        assertThat(VoteProgressNotifier.halfOf(5)).isEqualTo(3);
        assertThat(VoteProgressNotifier.halfOf(1)).isEqualTo(1);
    }
}
