package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.EmailSuppression;
import com.myhive.backend.entity.TripLead;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.model.TripLeadSource;
import com.myhive.backend.model.TripLeadStatus;
import com.myhive.backend.model.VoteMode;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.BookingRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.EmailSuppressionRepository;
import com.myhive.backend.repository.TripLeadActivityRepository;
import com.myhive.backend.repository.TripLeadRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Not @Transactional: createFromVoteSession opens a REQUIRES_NEW transaction, which would
// deadlock/miss data pinned in an uncommitted test transaction. Cleanup is manual.
@SpringBootTest
@Import(TestSecurityConfig.class)
class TripLeadFromVoteSessionTest {

    @Autowired private TripLeadService tripLeadService;
    @Autowired private TripLeadRepository tripLeadRepository;
    @Autowired private TripLeadActivityRepository tripLeadActivityRepository;
    @Autowired private EmailSuppressionRepository emailSuppressionRepository;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionActivityRepository voteSessionActivityRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private BookingRepository bookingRepository;

    private Destination destination;
    private Activity karting;

    @BeforeEach
    void setUp() {
        // No broad deleteAll: this class is not @Transactional and shares the cached context DB
        // with other test classes — each test isolates itself via unique emails/session ids instead.
        destination = destinationRepository.saveAndFlush(TestDataFactory.destination("Prague"));
        karting = activityRepository.saveAndFlush(
                TestDataFactory.activity(destination, "Karting", new BigDecimal("50.00")));
    }

    private VoteSession completedSession(String email) {
        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail(email);
        session.setNumberOfTravelers(8);
        session.setStartDate(LocalDate.now().plusDays(20));
        session.setEndDate(LocalDate.now().plusDays(22));
        session.setStatus(VoteSessionStatus.COMPLETED);
        session.setVoteMode(VoteMode.CART);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC));
        session = voteSessionRepository.saveAndFlush(session);

        VoteSessionActivity ballotRow = new VoteSessionActivity();
        ballotRow.setSession(session);
        ballotRow.setActivity(karting);
        ballotRow.setActivityName("Karting");
        ballotRow.setPrice(new BigDecimal("50.00"));
        ballotRow.setSortOrder(0);
        voteSessionActivityRepository.saveAndFlush(ballotRow);
        return session;
    }

    @Test
    void createFromVoteSession_createsVoteLeadWithBallotSnapshot() {
        VoteSession session = completedSession("Organizer@Example.com");

        tripLeadService.createFromVoteSession(session);

        List<TripLead> leads = tripLeadRepository
                .findAllByEmailAndStatus("organizer@example.com", TripLeadStatus.ACTIVE);
        assertThat(leads).hasSize(1);
        TripLead lead = leads.get(0);
        assertThat(lead.getSource()).isEqualTo(TripLeadSource.VOTE);
        assertThat(lead.getVoteSessionId()).isEqualTo(session.getId());
        assertThat(lead.getNumberOfTravelers()).isEqualTo(8);
        assertThat(tripLeadActivityRepository.findByLeadIdOrderBySortOrder(lead.getId())).hasSize(1);
    }

    @Test
    void createFromVoteSession_skipsWhenBookingExists() {
        VoteSession session = completedSession("booked@example.com");
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setId(null);
        booking.setVoteSessionId(session.getId());
        bookingRepository.saveAndFlush(booking);

        tripLeadService.createFromVoteSession(session);

        assertThat(tripLeadRepository.findAllByEmailAndStatus("booked@example.com", TripLeadStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    void createFromVoteSession_skipsSuppressedEmails() {
        VoteSession session = completedSession("optout@example.com");
        EmailSuppression suppression = new EmailSuppression();
        suppression.setEmail("optout@example.com");
        emailSuppressionRepository.saveAndFlush(suppression);

        tripLeadService.createFromVoteSession(session);

        assertThat(tripLeadRepository.findAllByEmailAndStatus("optout@example.com", TripLeadStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    void createFromVoteSession_repurposesExistingActiveLead() {
        VoteSession session = completedSession("existing@example.com");
        TripLead quizLead = tripLeadService.newLead("existing@example.com");
        quizLead.setReminderStage(1);
        quizLead.setLastActivityAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        tripLeadRepository.saveAndFlush(quizLead);

        tripLeadService.createFromVoteSession(session);

        List<TripLead> leads = tripLeadRepository
                .findAllByEmailAndStatus("existing@example.com", TripLeadStatus.ACTIVE);
        assertThat(leads).hasSize(1);
        assertThat(leads.get(0).getId()).isEqualTo(quizLead.getId());
        assertThat(leads.get(0).getSource()).isEqualTo(TripLeadSource.VOTE);
        assertThat(leads.get(0).getReminderStage()).isZero();
    }
}
