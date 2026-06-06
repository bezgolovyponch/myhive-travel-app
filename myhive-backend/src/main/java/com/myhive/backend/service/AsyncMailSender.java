package com.myhive.backend.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Performs the actual (blocking) SMTP send off the request thread.
 *
 * <p>This lives in its own bean — separate from {@link EmailService} — for two reasons:
 * <ul>
 *   <li>{@code @Async} is proxy-based, so a self-invocation from within {@code EmailService}
 *       would run synchronously and defeat the purpose. Crossing a bean boundary routes the
 *       call through the async proxy.</li>
 *   <li>Callers build a fully-materialized {@link MimeMessage} (template already rendered to an
 *       HTML string, recipients resolved) before handing it over, so no lazy-loaded JPA entities
 *       cross the async boundary — avoids {@code LazyInitializationException} outside the
 *       request transaction.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AsyncMailSender {

    private final JavaMailSender mailSender;

    @Async("emailTaskExecutor")
    public void send(MimeMessage message, String description) {
        try {
            mailSender.send(message);
            log.info("Email sent successfully: {}", description);
        } catch (Exception e) {
            // Fire-and-forget: the HTTP request has already returned, so a failure here cannot be
            // surfaced to the caller. Log it for monitoring and move on — never rethrow.
            log.error("Failed to send email ({}). Cause: {}", description, e.getMessage(), e);
        }
    }
}
