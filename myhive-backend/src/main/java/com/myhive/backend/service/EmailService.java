package com.myhive.backend.service;

import com.myhive.backend.dto.TripExportRequest;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
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
    
    @Value("${spring.mail.username:noreply@myhive-travel.com}")
    private String fromEmail;

    public void sendItineraryConfirmation(String toEmail, String customerName, TripExportRequest tripData) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("🎉 Your MyHive Travel Itinerary Confirmation");

            // Prepare template context
            Context context = new Context();
            context.setVariable("customerName", customerName);
            context.setVariable("tripData", tripData);
            context.setVariable("bookingDate", java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));

            // Process template
            String htmlContent = templateEngine.process("email/itinerary-confirmation", context);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Itinerary confirmation email sent to: {}", toEmail);
            
        } catch (Exception e) {
            log.error("Failed to send itinerary confirmation email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send confirmation email", e);
        }
    }

    public void sendBookingNotification(String toEmail, String customerName, TripExportRequest tripData, String googleSheetUrl) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("🆕 New Booking: " + customerName);

            // Prepare template context
            Context context = new Context();
            context.setVariable("customerName", customerName);
            context.setVariable("userEmail", tripData.getUserEmail());
            context.setVariable("tripName", tripData.getTripName());
            context.setVariable("activityCount", tripData.getDestinations().get(0).getActivities().size());
            context.setVariable("bookingDate", java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));
            context.setVariable("googleSheetUrl", googleSheetUrl);

            // Process template
            String textContent = templateEngine.process("email/booking-notification", context);
            message.setText(textContent);
            
            mailSender.send(message);
            
            log.info("Booking notification sent to admin: {}", toEmail);
            
        } catch (Exception e) {
            log.error("Failed to send booking notification to admin: {}", toEmail, e);
        }
    }
}
