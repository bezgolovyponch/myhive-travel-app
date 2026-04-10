package com.myhive.backend.service;

import com.myhive.backend.dto.ContactRequest;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.exception.EmailSendException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.email.contact-to}")
    private String contactToEmail;

    public void sendItineraryConfirmation(String toEmail, String customerName, TripExportRequest tripData) {
        log.info("Preparing itinerary confirmation email: from={}, to={}, customer={}", fromEmail, toEmail, customerName);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Your Trivlu Travel Itinerary Confirmation");

            Context context = new Context();
            context.setVariable("customerName", customerName);
            context.setVariable("tripData", tripData);
            context.setVariable("bookingDate", java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));

            log.debug("Processing email template: email/itinerary-confirmation");
            String htmlContent = templateEngine.process("itinerary-confirmation", context);
            helper.setText(htmlContent, true);

            log.info("Sending email via SMTP to: {}", toEmail);
            mailSender.send(message);
            log.info("Itinerary confirmation email sent successfully to: {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send itinerary confirmation email to: {}. Cause: {}", toEmail, e.getMessage(), e);
            throw new EmailSendException("Failed to send confirmation email", e);
        }
    }

    public void sendContactNotification(ContactRequest request) {
        log.info("Sending contact form notification: from={}, subject={}", request.getEmail(), request.getSubject());
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(contactToEmail);
            helper.setReplyTo(request.getEmail());
            helper.setSubject("Contact Form: " + request.getSubject());

            Context context = new Context();
            context.setVariable("name", request.getName());
            context.setVariable("email", request.getEmail());
            context.setVariable("subject", request.getSubject());
            context.setVariable("message", request.getMessage());

            String htmlContent = templateEngine.process("contact-notification", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Contact form notification sent successfully to: {}", contactToEmail);

        } catch (Exception e) {
            log.error("Failed to send contact notification. Cause: {}", e.getMessage(), e);
            throw new EmailSendException("Failed to send contact notification", e);
        }
    }
}
