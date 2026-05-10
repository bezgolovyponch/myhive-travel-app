package com.myhive.backend.service;

import com.myhive.backend.dto.ContactRequest;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public static class DestinationView {
        public String destinationName;
        public String country;
        public Integer duration;
        public String startDate;
        public String endDate;
        public List<PackageGroup> packageGroups = new ArrayList<>();
        public List<TripExportRequest.ActivityExport> standaloneActivities = new ArrayList<>();
    }

    public static class PackageGroup {
        public String packageName;
        public BigDecimal discountPct;
        public BigDecimal subtotal;
        public BigDecimal discounted;
        public List<TripExportRequest.ActivityExport> activities = new ArrayList<>();
    }

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
            context.setVariable("destinationViews", buildDestinationViews(tripData));
            context.setVariable("bookingDate", LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));

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

    public void sendVoteResult(VoteSession session, List<VoteSessionResultActivity> resultActivities, String siteUrl) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    List<DestinationView> buildDestinationViews(TripExportRequest tripData) {
        List<DestinationView> views = new ArrayList<>();
        if (tripData.getDestinations() == null) {
            return views;
        }
        for (TripExportRequest.DestinationExport dest : tripData.getDestinations()) {
            DestinationView view = new DestinationView();
            view.destinationName = dest.getDestinationName();
            view.country = dest.getCountry();
            view.duration = dest.getDuration();
            view.startDate = dest.getStartDate();
            view.endDate = dest.getEndDate();

            if (dest.getActivities() != null) {
                Map<UUID, PackageGroup> groupMap = new LinkedHashMap<>();
                for (TripExportRequest.ActivityExport activity : dest.getActivities()) {
                    UUID packageId = activity.getPackageId();
                    if (packageId == null) {
                        view.standaloneActivities.add(activity);
                    } else {
                        PackageGroup group = groupMap.computeIfAbsent(packageId, id -> {
                            PackageGroup g = new PackageGroup();
                            g.packageName = activity.getPackageName();
                            g.discountPct = activity.getPackageDiscountPct();
                            return g;
                        });
                        group.activities.add(activity);
                    }
                }
                for (PackageGroup group : groupMap.values()) {
                    BigDecimal subtotal = BigDecimal.ZERO;
                    for (TripExportRequest.ActivityExport activity : group.activities) {
                        BigDecimal activityPrice = activity.getPrice() != null
                                ? BigDecimal.valueOf(activity.getPrice())
                                : BigDecimal.ZERO;
                        subtotal = subtotal.add(activityPrice);
                    }
                    group.subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
                    BigDecimal discountPct = group.discountPct != null ? group.discountPct : BigDecimal.ZERO;
                    BigDecimal multiplier = HUNDRED.subtract(discountPct)
                            .divide(HUNDRED, 10, RoundingMode.HALF_UP);
                    group.discounted = subtotal.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                    view.packageGroups.add(group);
                }
            }
            views.add(view);
        }
        return views;
    }
}
