package com.myhive.backend.service;

import com.myhive.backend.dto.ContactDTO;
import com.myhive.backend.exception.EmailSendException;
import com.myhive.backend.model.ContactSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactDigestSchedulerTest {

    private static final String FRONTEND_URL = "https://trivlu.com";

    @Mock private ContactService contactService;
    @Mock private EmailService emailService;

    @InjectMocks
    private ContactDigestScheduler scheduler;

    @BeforeEach
    void enableFlags() {
        ReflectionTestUtils.setField(scheduler, "digestEnabled", true);
        ReflectionTestUtils.setField(scheduler, "emailEnabled", true);
        ReflectionTestUtils.setField(scheduler, "frontendUrl", FRONTEND_URL);
    }

    private static ContactDTO contact() {
        return new ContactDTO(UUID.randomUUID(), "anna@example.com", null, null,
                ContactSource.VOTE, ContactSource.VOTE,
                LocalDateTime.of(2026, 9, 12, 8, 30), LocalDateTime.of(2026, 9, 12, 8, 30), 1, false);
    }

    @Test
    void sendDailyDigest_sendsAndStampsWhenContactsAreNew() {
        ContactDTO expectedContact = contact();
        when(contactService.findUndigested()).thenReturn(List.of(expectedContact));

        scheduler.sendDailyDigest();

        verify(emailService).sendNewContactsDigest(List.of(expectedContact), FRONTEND_URL);
        verify(contactService).markDigested(eq(List.of(expectedContact.getId())), any(LocalDateTime.class));
    }

    @Test
    void sendDailyDigest_noContacts_sendsNothing() {
        when(contactService.findUndigested()).thenReturn(List.of());

        scheduler.sendDailyDigest();

        verifyNoInteractions(emailService);
        verify(contactService, never()).markDigested(anyCollection(), any());
    }

    @Test
    void sendDailyDigest_sendFails_leavesRowsUnstamped() {
        when(contactService.findUndigested()).thenReturn(List.of(contact()));
        doThrow(new EmailSendException("smtp down", null))
                .when(emailService).sendNewContactsDigest(any(), eq(FRONTEND_URL));

        scheduler.sendDailyDigest();

        verify(contactService, never()).markDigested(anyCollection(), any());
    }

    @Test
    void sendDailyDigest_noopWhenDigestDisabled() {
        ReflectionTestUtils.setField(scheduler, "digestEnabled", false);

        scheduler.sendDailyDigest();

        verifyNoInteractions(contactService, emailService);
    }

    @Test
    void sendDailyDigest_noopWhenEmailDisabled() {
        ReflectionTestUtils.setField(scheduler, "emailEnabled", false);

        scheduler.sendDailyDigest();

        verifyNoInteractions(contactService, emailService);
    }
}
