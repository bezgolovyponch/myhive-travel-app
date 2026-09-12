package com.myhive.backend.service;

import com.myhive.backend.dto.ContactDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Once a day, mails sales every contact captured since the previous digest. Rows are stamped as
 * reported only after the email was delivered, so a failed send simply retries tomorrow.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContactDigestScheduler {

    private final ContactService contactService;
    private final EmailService emailService;

    /** Kill switch — capture keeps working when off, only the digest stops. */
    @Value("${app.contacts.digest-enabled:true}")
    private boolean digestEnabled;

    /** With the mailer off, the synchronous send would throw every day — skip instead. */
    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.frontend.url:https://trivlu.com}")
    private String frontendUrl;

    /** 07:00 UTC daily — start of the working day in Berlin, before the inbox fills up. */
    @Scheduled(cron = "0 0 7 * * *")
    public void sendDailyDigest() {
        if (!digestEnabled || !emailEnabled) {
            return;
        }
        List<ContactDTO> fresh = contactService.findUndigested();
        if (fresh.isEmpty()) {
            log.info("Contacts digest: nothing new since the last run");
            return;
        }
        try {
            emailService.sendNewContactsDigest(fresh, frontendUrl);
        } catch (Exception e) {
            // Rows stay un-stamped on purpose: they go out with the next successful digest.
            log.error("Contacts digest failed; {} contacts stay queued: {}", fresh.size(), e.getMessage(), e);
            return;
        }
        List<UUID> ids = fresh.stream().map(ContactDTO::getId).toList();
        contactService.markDigested(ids, LocalDateTime.now(ZoneOffset.UTC));
        log.info("Contacts digest sent with {} contacts", ids.size());
    }
}
