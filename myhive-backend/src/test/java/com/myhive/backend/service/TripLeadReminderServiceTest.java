package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.MockEmailServiceConfig;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.EmailSuppression;
import com.myhive.backend.entity.TripLead;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.model.TripLeadSource;
import com.myhive.backend.model.TripLeadStatus;
import com.myhive.backend.repository.BookingRepository;
import com.myhive.backend.repository.EmailSuppressionRepository;
import com.myhive.backend.repository.TripLeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

// Not @Transactional: processReminder manages its own transaction; state is cleaned per test.
@SpringBootTest
@Import({TestSecurityConfig.class, MockEmailServiceConfig.class})
class TripLeadReminderServiceTest {

    @Autowired private TripLeadReminderService reminderService;
    @Autowired private TripLeadRepository tripLeadRepository;
    @Autowired private EmailSuppressionRepository emailSuppressionRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private EmailService emailService;

    @BeforeEach
    void setUp() {
        // No deleteAll: the class is not @Transactional and shares the cached-context DB with
        // other test classes — isolation comes from a unique email per test instead.
        reset(emailService);
    }

    private TripLead lead(String email, TripLeadSource source, int stage, LocalDateTime lastActivityAt) {
        TripLead lead = new TripLead();
        lead.setEmail(email);
        lead.setSource(source);
        lead.setRestoreToken(UUID.randomUUID());
        lead.setUnsubscribeToken(UUID.randomUUID());
        lead.setStatus(TripLeadStatus.ACTIVE);
        lead.setReminderStage(stage);
        lead.setLastActivityAt(lastActivityAt);
        return tripLeadRepository.saveAndFlush(lead);
    }

    private LocalDateTime hoursAgo(int hours) {
        return LocalDateTime.now(ZoneOffset.UTC).minusHours(hours);
    }

    @Test
    void quizLead_firstReminderSentAfterOneHour() {
        TripLead due = lead("quiz-due@example.com", TripLeadSource.QUIZ, 0, hoursAgo(2));

        reminderService.processReminder(due.getId());

        verify(emailService).sendTripReminder(any(), anyInt(), anyList(), anyList(), anyString());
        TripLead updated = tripLeadRepository.findById(due.getId()).orElseThrow();
        assertThat(updated.getReminderStage()).isEqualTo(1);
        assertThat(updated.getStatus()).isEqualTo(TripLeadStatus.ACTIVE);
        assertThat(updated.getLastReminderAt()).isNotNull();
    }

    @Test
    void quizLead_notDueYet_nothingHappens() {
        TripLead notDue = lead("quiz-early@example.com", TripLeadSource.QUIZ, 0,
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(30));

        reminderService.processReminder(notDue.getId());

        verify(emailService, never()).sendTripReminder(any(), anyInt(), anyList(), anyList(), anyString());
        assertThat(tripLeadRepository.findById(notDue.getId()).orElseThrow().getReminderStage()).isZero();
    }

    @Test
    void quizLead_finalStageCompletesSeries() {
        TripLead lastStage = lead("quiz-final@example.com", TripLeadSource.QUIZ, 2, hoursAgo(73));

        reminderService.processReminder(lastStage.getId());

        TripLead updated = tripLeadRepository.findById(lastStage.getId()).orElseThrow();
        assertThat(updated.getReminderStage()).isEqualTo(3);
        assertThat(updated.getStatus()).isEqualTo(TripLeadStatus.COMPLETED);
    }

    @Test
    void voteLead_firstReminderWaits24Hours() {
        TripLead tooEarly = lead("vote-early@example.com", TripLeadSource.VOTE, 0, hoursAgo(2));

        reminderService.processReminder(tooEarly.getId());

        verify(emailService, never()).sendTripReminder(any(), anyInt(), anyList(), anyList(), anyString());
    }

    @Test
    void voteLead_secondReminderCompletesSeries() {
        TripLead lastStage = lead("vote-final@example.com", TripLeadSource.VOTE, 1, hoursAgo(73));

        reminderService.processReminder(lastStage.getId());

        TripLead updated = tripLeadRepository.findById(lastStage.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TripLeadStatus.COMPLETED);
    }

    @Test
    void suppressedEmail_marksUnsubscribedWithoutSending() {
        TripLead due = lead("suppressed@example.com", TripLeadSource.QUIZ, 0, hoursAgo(2));
        EmailSuppression suppression = new EmailSuppression();
        suppression.setEmail("suppressed@example.com");
        emailSuppressionRepository.saveAndFlush(suppression);

        reminderService.processReminder(due.getId());

        verify(emailService, never()).sendTripReminder(any(), anyInt(), anyList(), anyList(), anyString());
        assertThat(tripLeadRepository.findById(due.getId()).orElseThrow().getStatus())
                .isEqualTo(TripLeadStatus.UNSUBSCRIBED);
    }

    @Test
    void bookingByEmail_marksConvertedWithoutSending() {
        TripLead due = lead("converted@example.com", TripLeadSource.QUIZ, 0, hoursAgo(2));
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setId(null);
        booking.setUserEmail("Converted@Example.com");
        bookingRepository.saveAndFlush(booking);

        reminderService.processReminder(due.getId());

        verify(emailService, never()).sendTripReminder(any(), anyInt(), anyList(), anyList(), anyString());
        assertThat(tripLeadRepository.findById(due.getId()).orElseThrow().getStatus())
                .isEqualTo(TripLeadStatus.CONVERTED);
    }
}
