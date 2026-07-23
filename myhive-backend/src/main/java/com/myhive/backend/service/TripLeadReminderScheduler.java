package com.myhive.backend.service;

import com.myhive.backend.entity.TripLead;
import com.myhive.backend.model.TripLeadStatus;
import com.myhive.backend.repository.TripLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class TripLeadReminderScheduler {

    private final TripLeadRepository tripLeadRepository;
    private final TripLeadReminderService tripLeadReminderService;

    /** Kill switch — capture keeps working when off, only the sending stops. */
    @Value("${app.leads.reminders-enabled:true}")
    private boolean remindersEnabled;

    /** With the mailer off, ticking would silently burn series stages — skip instead. */
    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Scheduled(fixedDelay = 600_000)
    public void processDueReminders() {
        if (!remindersEnabled || !emailEnabled) {
            return;
        }
        for (TripLead lead : tripLeadRepository.findByStatus(TripLeadStatus.ACTIVE)) {
            try {
                tripLeadReminderService.processReminder(lead.getId());
            } catch (Exception e) {
                log.error("Failed to process trip lead {}: {}", lead.getId(), e.getMessage(), e);
            }
        }
    }

    /** GDPR retention: leads vanish 30 days after their last touch; suppression rows never do. */
    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void cleanupOldLeads() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(30);
        int deleted = tripLeadRepository.deleteByUpdatedAtBefore(cutoff);
        log.info("Cleaned up {} trip leads", deleted);
    }
}
