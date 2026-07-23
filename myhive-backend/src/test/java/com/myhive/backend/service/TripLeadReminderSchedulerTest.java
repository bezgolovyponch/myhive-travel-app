package com.myhive.backend.service;

import com.myhive.backend.entity.TripLead;
import com.myhive.backend.model.TripLeadStatus;
import com.myhive.backend.repository.TripLeadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripLeadReminderSchedulerTest {

    @Mock private TripLeadRepository tripLeadRepository;
    @Mock private TripLeadReminderService tripLeadReminderService;

    @InjectMocks
    private TripLeadReminderScheduler scheduler;

    private TripLead activeLead() {
        TripLead lead = new TripLead();
        lead.setId(UUID.randomUUID());
        lead.setStatus(TripLeadStatus.ACTIVE);
        return lead;
    }

    private void enableFlags() {
        ReflectionTestUtils.setField(scheduler, "remindersEnabled", true);
        ReflectionTestUtils.setField(scheduler, "emailEnabled", true);
    }

    @Test
    void processDueReminders_delegatesPerActiveLead() {
        enableFlags();
        TripLead lead1 = activeLead();
        TripLead lead2 = activeLead();
        when(tripLeadRepository.findByStatus(TripLeadStatus.ACTIVE)).thenReturn(List.of(lead1, lead2));

        scheduler.processDueReminders();

        verify(tripLeadReminderService).processReminder(lead1.getId());
        verify(tripLeadReminderService).processReminder(lead2.getId());
    }

    @Test
    void processDueReminders_continuesOnError() {
        enableFlags();
        TripLead failing = activeLead();
        TripLead healthy = activeLead();
        when(tripLeadRepository.findByStatus(TripLeadStatus.ACTIVE)).thenReturn(List.of(failing, healthy));
        doThrow(new RuntimeException("boom")).when(tripLeadReminderService).processReminder(failing.getId());

        scheduler.processDueReminders();

        verify(tripLeadReminderService).processReminder(healthy.getId());
    }

    @Test
    void processDueReminders_noopWhenRemindersDisabled() {
        ReflectionTestUtils.setField(scheduler, "remindersEnabled", false);
        ReflectionTestUtils.setField(scheduler, "emailEnabled", true);

        scheduler.processDueReminders();

        verify(tripLeadReminderService, never()).processReminder(any());
    }

    @Test
    void processDueReminders_noopWhenEmailDisabled() {
        // A disabled mailer must not silently burn the series stages.
        ReflectionTestUtils.setField(scheduler, "remindersEnabled", true);
        ReflectionTestUtils.setField(scheduler, "emailEnabled", false);

        scheduler.processDueReminders();

        verify(tripLeadReminderService, never()).processReminder(any());
    }

    @Test
    void cleanupOldLeads_deletesByRetentionCutoff() {
        when(tripLeadRepository.deleteByUpdatedAtBefore(any())).thenReturn(3);

        scheduler.cleanupOldLeads();

        verify(tripLeadRepository).deleteByUpdatedAtBefore(any());
    }
}
