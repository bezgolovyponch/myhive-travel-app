package com.myhive.backend.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AsyncMailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private AsyncMailSender asyncMailSender;

    @Test
    void send_delegatesMessageToMailSender() {
        MimeMessage message = mock(MimeMessage.class);

        asyncMailSender.send(message, "vote-created confirmation");

        verify(mailSender).send(message);
    }

    @Test
    void send_swallowsExceptionWhenMailSenderFails() {
        MimeMessage message = mock(MimeMessage.class);
        doThrow(new MailSendException("smtp unreachable")).when(mailSender).send(message);

        // A failed send is fire-and-forget — it must never propagate to the caller,
        // since the HTTP request has already returned by the time this runs.
        assertThatCode(() -> asyncMailSender.send(message, "vote-created confirmation"))
                .doesNotThrowAnyException();
    }
}
