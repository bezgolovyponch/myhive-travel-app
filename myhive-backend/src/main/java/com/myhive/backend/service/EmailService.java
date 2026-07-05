package com.myhive.backend.service;

import com.myhive.backend.dto.ContactRequest;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.exception.EmailSendException;
import com.myhive.backend.model.VoteMode;
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
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm 'UTC'");
    private static final String SUPPORT_EMAIL = "support@trivlu.com";

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
    private final AsyncMailSender asyncMailSender;

    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.email.contact-to}")
    private String contactToEmail;

    @Value("${app.email.bookings-to}")
    private String bookingsToEmail;

    public void sendItineraryConfirmation(String toEmail, String customerName, TripExportRequest tripData, String tripId) {
        sendItineraryConfirmation(toEmail, customerName, tripData, tripId, null, null);
    }

    /** Overload that also renders a payment summary (deposit paid / balance due) — used by the deposit flow. */
    public void sendItineraryConfirmation(String toEmail, String customerName, TripExportRequest tripData,
            String tripId, BigDecimal amountPaid, BigDecimal totalAmount) {
        log.info("Preparing itinerary confirmation email: from={}, to={}", fromEmail, maskEmail(toEmail));
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
            context.setVariable("tripId", tripId);
            // Payment status — only present when a deposit has actually been collected (not for leads).
            boolean depositPaid = amountPaid != null && amountPaid.signum() > 0;
            context.setVariable("depositPaid", depositPaid);
            context.setVariable("amountPaid", amountPaid);
            context.setVariable("totalAmount", totalAmount);
            if (depositPaid && totalAmount != null) {
                context.setVariable("balanceDue", totalAmount.subtract(amountPaid));
            }

            log.debug("Processing email template: email/itinerary-confirmation");
            String htmlContent = templateEngine.process("itinerary-confirmation", context);
            helper.setText(htmlContent, true);

            log.info("Queueing itinerary confirmation email to: {}", maskEmail(toEmail));
            asyncMailSender.send(message, "itinerary confirmation to " + maskEmail(toEmail));

        } catch (Exception e) {
            log.error("Failed to build itinerary confirmation email to: {}. Cause: {}", maskEmail(toEmail), e.getMessage(), e);
            throw new EmailSendException("Failed to send confirmation email", e);
        }
    }

    /**
     * Internal notification to the bookings inbox so the team is alerted the moment a booking comes in.
     * Carries the customer's contact details and a summary of the booked itinerary; Reply-To is set to
     * the customer so staff can respond directly. Distinct from {@link #sendItineraryConfirmation}, which
     * is the customer-facing confirmation.
     */
    public void sendBookingNotification(Booking booking, TripExportRequest tripData) {
        log.info("Preparing booking notification for booking {} to: {}", booking.getId(), bookingsToEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(bookingsToEmail);
            if (booking.getUserEmail() != null) {
                helper.setReplyTo(booking.getUserEmail());
            }
            helper.setSubject("New booking — " + booking.getTripId());

            Context context = new Context();
            context.setVariable("customerName", booking.getCustomerName());
            context.setVariable("userEmail", booking.getUserEmail());
            context.setVariable("phone", booking.getPhone());
            context.setVariable("tripId", booking.getTripId());
            context.setVariable("totalAmount", booking.getTotalAmount());
            context.setVariable("tripName", tripData.getTripName());
            context.setVariable("destinationViews", buildDestinationViews(tripData));
            // Payment status — present only once a deposit has been collected (paid deposit vs. a lead).
            BigDecimal amountPaid = booking.getAmountPaid();
            boolean depositPaid = amountPaid != null && amountPaid.signum() > 0;
            context.setVariable("depositPaid", depositPaid);
            context.setVariable("amountPaid", amountPaid);
            if (depositPaid && booking.getTotalAmount() != null) {
                context.setVariable("balanceDue", booking.getTotalAmount().subtract(amountPaid));
            }

            log.debug("Processing email template: email/booking-notification");
            String htmlContent = templateEngine.process("booking-notification", context);
            helper.setText(htmlContent, true);

            asyncMailSender.send(message, "booking notification for booking " + booking.getId());

        } catch (Exception e) {
            log.error("Failed to build booking notification for booking {}. Cause: {}", booking.getId(), e.getMessage(), e);
            throw new EmailSendException("Failed to send booking notification", e);
        }
    }

    public void sendContactNotification(ContactRequest request) {
        // PII/log-forging guard: mask the sender address and keep the user-controlled subject out of logs
        // (same treatment as customerName in the itinerary emails).
        log.info("Sending contact form notification: from={}", maskEmail(request.getEmail()));
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

            // Sent synchronously (unlike the other emails): a contact submission has no durable
            // record behind it, so a delivery failure must surface to the user rather than being
            // swallowed by the fire-and-forget async sender.
            log.info("Sending contact form notification via SMTP to: {}", contactToEmail);
            mailSender.send(message);
            log.info("Contact form notification sent successfully to: {}", contactToEmail);

        } catch (Exception e) {
            log.error("Failed to send contact notification. Cause: {}", e.getMessage(), e);
            throw new EmailSendException("Failed to send contact notification", e);
        }
    }

    public void sendVoteResult(VoteSession session, List<VoteSessionResultActivity> resultActivities, String frontendUrl) {
        log.info("Preparing vote result email: from={}, to={}, destination={}", fromEmail, maskEmail(session.getInitiatorEmail()), session.getDestination().getName());
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(session.getInitiatorEmail());
            helper.setSubject("Your group trip to " + session.getDestination().getName() + " is ready!");

            // QUIZ results deep-link straight to the destination's Trip Builder tab with
            // the vote session id — TripBuilder fetches the result and seeds the
            // itinerary, budget panel, and suggestions server-side.
            // CART results only ever annotate the initiator's own cart (the annotation
            // effect never seeds items), so that deep link would land any other device
            // on an empty Trip Builder. Send CART results to the read-only result page
            // instead, which works cross-device.
            String destinationSlug = session.getDestination().getSlug();
            String resultUrl;
            if (session.getVoteMode() == VoteMode.CART) {
                resultUrl = frontendUrl + "/vote/" + session.getShareToken() + "/result";
            } else if (destinationSlug != null) {
                resultUrl = frontendUrl + "/destination/" + destinationSlug + "?tab=trip-builder&voteSession=" + session.getShareToken();
            } else {
                resultUrl = frontendUrl + "/vote/" + session.getShareToken() + "/result";
            }

            Context context = new Context();
            context.setVariable("session", session);
            context.setVariable("resultActivities", resultActivities);
            context.setVariable("resultUrl", resultUrl);

            log.debug("Processing email template: vote-result");
            String htmlContent = templateEngine.process("vote-result", context);
            helper.setText(htmlContent, true);

            log.info("Queueing vote result email to: {}", maskEmail(session.getInitiatorEmail()));
            asyncMailSender.send(message, "vote result to " + maskEmail(session.getInitiatorEmail()));

        } catch (Exception e) {
            log.error("Failed to build vote result email to: {}. Cause: {}", maskEmail(session.getInitiatorEmail()), e.getMessage(), e);
            throw new EmailSendException("Failed to send vote result email", e);
        }
    }

    public void sendVoteCreatedConfirmation(VoteSession session, String frontendUrl) {
        log.info("Preparing vote-created confirmation email: from={}, to={}, destination={}",
                fromEmail, maskEmail(session.getInitiatorEmail()), session.getDestination().getName());
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(session.getInitiatorEmail());
            helper.setSubject("Your group vote for " + session.getDestination().getName() + " is live");

            String shareToken = session.getShareToken().toString();
            String inviteUrl = frontendUrl + "/vote/" + shareToken + "/activities?ref=invite";
            String dashboardUrl = frontendUrl + "/vote/" + shareToken + "/waiting?manager=" + session.getManagerToken();

            Context context = new Context();
            context.setVariable("session", session);
            context.setVariable("inviteUrl", inviteUrl);
            context.setVariable("dashboardUrl", dashboardUrl);
            context.setVariable("supportEmail", SUPPORT_EMAIL);
            context.setVariable("startDate", session.getStartDate().format(DATE_FORMAT));
            context.setVariable("endDate", session.getEndDate().format(DATE_FORMAT));
            context.setVariable("expiresAt", session.getExpiresAt().format(DATE_TIME_FORMAT));

            log.debug("Processing email template: vote-created");
            String htmlContent = templateEngine.process("vote-created", context);
            helper.setText(htmlContent, true);

            log.info("Queueing vote-created confirmation email to: {}", maskEmail(session.getInitiatorEmail()));
            asyncMailSender.send(message, "vote-created confirmation to " + maskEmail(session.getInitiatorEmail()));

        } catch (Exception e) {
            log.error("Failed to build vote-created confirmation email to: {}. Cause: {}",
                    maskEmail(session.getInitiatorEmail()), e.getMessage(), e);
            throw new EmailSendException("Failed to send vote-created confirmation email", e);
        }
    }

    public void sendPaymentReceived(String toEmail, String customerName, String tripId,
            BigDecimal amountPaid, BigDecimal totalAmount, boolean fullyPaid) {
        log.info("Preparing payment-received email: to={}, tripId={}, fullyPaid={}", maskEmail(toEmail), tripId, fullyPaid);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(fullyPaid ? "Your trip is fully paid 🎉" : "We received your payment");

            Context context = new Context();
            context.setVariable("customerName", customerName);
            context.setVariable("tripId", tripId);
            context.setVariable("amountPaid", amountPaid);
            context.setVariable("totalAmount", totalAmount);
            context.setVariable("fullyPaid", fullyPaid);

            String htmlContent = templateEngine.process("payment-received", context);
            helper.setText(htmlContent, true);

            asyncMailSender.send(message, "payment received to " + maskEmail(toEmail));
        } catch (Exception e) {
            log.error("Failed to build payment-received email to: {}. Cause: {}", maskEmail(toEmail), e.getMessage(), e);
            throw new EmailSendException("Failed to send payment-received email", e);
        }
    }

    public void sendConsultationLead(Booking booking) {
        log.info("Preparing consultation-lead notification for booking {}", booking.getId());
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(contactToEmail);
            if (booking.getUserEmail() != null) {
                helper.setReplyTo(booking.getUserEmail());
            }
            helper.setSubject("New consultation request — " + booking.getTripId());

            Context context = new Context();
            context.setVariable("customerName", booking.getCustomerName());
            context.setVariable("userEmail", booking.getUserEmail());
            context.setVariable("phone", booking.getPhone());
            context.setVariable("tripId", booking.getTripId());
            context.setVariable("totalAmount", booking.getTotalAmount());

            String htmlContent = templateEngine.process("consultation-lead", context);
            helper.setText(htmlContent, true);

            asyncMailSender.send(message, "consultation lead for booking " + booking.getId());
        } catch (Exception e) {
            log.error("Failed to build consultation-lead email for booking {}. Cause: {}", booking.getId(), e.getMessage(), e);
            throw new EmailSendException("Failed to send consultation-lead email", e);
        }
    }

    private String maskEmail(String email) {
        return com.myhive.backend.util.EmailMasker.mask(email);
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
