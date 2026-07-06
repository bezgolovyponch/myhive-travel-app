package com.myhive.backend.service;

import com.myhive.backend.dto.ContactRequest;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.exception.EmailSendException;
import com.myhive.backend.model.VoteMode;
import com.myhive.backend.util.MoneyMath;
import jakarta.mail.internet.MimeMessage;
import lombok.Builder;
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

/**
 * Builds and dispatches all outbound email. Template variables are assembled in the public
 * methods (outside the send try-block), so best-effort callers must catch {@code Exception},
 * not just {@link EmailSendException}. When {@code app.email.enabled=false}, async emails are
 * skipped quietly; synchronous ones (contact form) fail loudly instead — see {@link #send}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

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

    /** Everything that varies between the outbound emails; {@link #send(EmailSpec)} does the rest. */
    @Builder
    private record EmailSpec(
            String to,
            String replyTo,
            String subject,
            String template,
            Map<String, Object> variables,
            String description,
            boolean synchronous) {
    }

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AsyncMailSender asyncMailSender;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

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
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("customerName", customerName);
        variables.put("tripData", tripData);
        variables.put("destinationViews", buildDestinationViews(tripData));
        variables.put("bookingDate", LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));
        variables.put("tripId", tripId);
        // Payment status — only present when a deposit has actually been collected (not for leads).
        boolean depositPaid = amountPaid != null && amountPaid.signum() > 0;
        variables.put("depositPaid", depositPaid);
        variables.put("amountPaid", amountPaid);
        variables.put("totalAmount", totalAmount);
        if (depositPaid && totalAmount != null) {
            variables.put("balanceDue", totalAmount.subtract(amountPaid));
        }

        send(EmailSpec.builder()
                .to(toEmail)
                .subject("Your Trivlu Travel Itinerary Confirmation")
                .template("itinerary-confirmation")
                .variables(variables)
                .description("itinerary confirmation to " + maskEmail(toEmail))
                .build());
    }

    /**
     * Internal notification to the bookings inbox so the team is alerted the moment a booking comes in.
     * Carries the customer's contact details and a summary of the booked itinerary; Reply-To is set to
     * the customer so staff can respond directly. Distinct from {@link #sendItineraryConfirmation}, which
     * is the customer-facing confirmation.
     */
    public void sendBookingNotification(Booking booking, TripExportRequest tripData) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("customerName", booking.getCustomerName());
        variables.put("userEmail", booking.getUserEmail());
        variables.put("phone", booking.getPhone());
        variables.put("tripId", booking.getTripId());
        variables.put("totalAmount", booking.getTotalAmount());
        variables.put("tripName", tripData.getTripName());
        variables.put("destinationViews", buildDestinationViews(tripData));
        // Payment status — present only once a deposit has been collected (paid deposit vs. a lead).
        BigDecimal amountPaid = booking.getAmountPaid();
        boolean depositPaid = amountPaid != null && amountPaid.signum() > 0;
        variables.put("depositPaid", depositPaid);
        variables.put("amountPaid", amountPaid);
        if (depositPaid && booking.getTotalAmount() != null) {
            variables.put("balanceDue", booking.getTotalAmount().subtract(amountPaid));
        }

        send(EmailSpec.builder()
                .to(bookingsToEmail)
                .replyTo(booking.getUserEmail())
                .subject("New booking — " + booking.getTripId())
                .template("booking-notification")
                .variables(variables)
                .description("booking notification for booking " + booking.getId())
                .build());
    }

    public void sendContactNotification(ContactRequest request) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("name", request.getName());
        variables.put("email", request.getEmail());
        variables.put("subject", request.getSubject());
        variables.put("message", request.getMessage());

        send(EmailSpec.builder()
                .to(contactToEmail)
                .replyTo(request.getEmail())
                // PII/log-forging guard: the user-controlled subject stays out of the description/logs.
                .subject("Contact Form: " + request.getSubject())
                .template("contact-notification")
                .variables(variables)
                .description("contact notification from " + maskEmail(request.getEmail()))
                // A contact submission has no durable record behind it, so a delivery failure must
                // surface to the user rather than being swallowed by the fire-and-forget async sender.
                .synchronous(true)
                .build());
    }

    public void sendVoteResult(VoteSession session, List<VoteSessionResultActivity> resultActivities, String frontendUrl) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("session", session);
        variables.put("resultActivities", resultActivities);
        variables.put("resultUrl", resultUrlFor(session, frontendUrl));

        send(EmailSpec.builder()
                .to(session.getInitiatorEmail())
                .subject("Your group trip to " + session.getDestination().getName() + " is ready!")
                .template("vote-result")
                .variables(variables)
                .description("vote result to " + maskEmail(session.getInitiatorEmail()))
                .build());
    }

    public void sendVoteCreatedConfirmation(VoteSession session, String frontendUrl) {
        String shareToken = session.getShareToken().toString();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("session", session);
        variables.put("inviteUrl", frontendUrl + "/vote/" + shareToken + "/activities?ref=invite");
        variables.put("dashboardUrl", frontendUrl + "/vote/" + shareToken + "/waiting?manager=" + session.getManagerToken());
        variables.put("supportEmail", SUPPORT_EMAIL);
        variables.put("startDate", session.getStartDate().format(DATE_FORMAT));
        variables.put("endDate", session.getEndDate().format(DATE_FORMAT));
        variables.put("expiresAt", session.getExpiresAt().format(DATE_TIME_FORMAT));

        send(EmailSpec.builder()
                .to(session.getInitiatorEmail())
                .subject("Your group vote for " + session.getDestination().getName() + " is live")
                .template("vote-created")
                .variables(variables)
                .description("vote-created confirmation to " + maskEmail(session.getInitiatorEmail()))
                .build());
    }

    public void sendPaymentReceived(String toEmail, String customerName, String tripId,
            BigDecimal amountPaid, BigDecimal totalAmount, boolean fullyPaid) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("customerName", customerName);
        variables.put("tripId", tripId);
        variables.put("amountPaid", amountPaid);
        variables.put("totalAmount", totalAmount);
        variables.put("fullyPaid", fullyPaid);

        send(EmailSpec.builder()
                .to(toEmail)
                .subject(fullyPaid ? "Your trip is fully paid 🎉" : "We received your payment")
                .template("payment-received")
                .variables(variables)
                .description("payment received to " + maskEmail(toEmail))
                .build());
    }

    public void sendConsultationLead(Booking booking) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("customerName", booking.getCustomerName());
        variables.put("userEmail", booking.getUserEmail());
        variables.put("phone", booking.getPhone());
        variables.put("tripId", booking.getTripId());
        variables.put("totalAmount", booking.getTotalAmount());

        send(EmailSpec.builder()
                .to(contactToEmail)
                .replyTo(booking.getUserEmail())
                .subject("New consultation request — " + booking.getTripId())
                .template("consultation-lead")
                .variables(variables)
                .description("consultation lead for booking " + booking.getId())
                .build());
    }

    private void send(EmailSpec spec) {
        if (!emailEnabled) {
            if (spec.synchronous()) {
                // A synchronous send is a fail-loud contract: nothing durable backs the message
                // (contact form), so a silent skip would discard it behind a success response.
                throw new EmailSendException("Email sending is disabled (app.email.enabled=false)", null);
            }
            log.info("Email sending is disabled (app.email.enabled=false), skipping {}", spec.description());
            return;
        }
        log.info("Preparing {} (from={}, to={})", spec.description(), fromEmail, maskEmail(spec.to()));
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(spec.to());
            if (spec.replyTo() != null) {
                helper.setReplyTo(spec.replyTo());
            }
            helper.setSubject(spec.subject());

            Context context = new Context();
            spec.variables().forEach(context::setVariable);
            helper.setText(templateEngine.process(spec.template(), context), true);

            if (spec.synchronous()) {
                mailSender.send(message);
                log.info("Email sent successfully: {}", spec.description());
            } else {
                asyncMailSender.send(message, spec.description());
            }
        } catch (Exception e) {
            log.error("Failed to send {}. Cause: {}", spec.description(), e.getMessage(), e);
            throw new EmailSendException("Failed to send " + spec.description(), e);
        }
    }

    /**
     * QUIZ results deep-link straight to the destination's Trip Builder tab with the vote session id —
     * TripBuilder fetches the result and seeds the itinerary, budget panel, and suggestions server-side.
     * CART results only ever annotate the initiator's own cart (the annotation effect never seeds items),
     * so that deep link would land any other device on an empty Trip Builder. Send CART results to the
     * read-only result page instead, which works cross-device.
     */
    private static String resultUrlFor(VoteSession session, String frontendUrl) {
        String destinationSlug = session.getDestination().getSlug();
        if (session.getVoteMode() == VoteMode.CART || destinationSlug == null) {
            return frontendUrl + "/vote/" + session.getShareToken() + "/result";
        }
        return frontendUrl + "/destination/" + destinationSlug + "?tab=trip-builder&voteSession=" + session.getShareToken();
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
                    group.discounted = MoneyMath.applyDiscountPct(subtotal, group.discountPct);
                    view.packageGroups.add(group);
                }
            }
            views.add(view);
        }
        return views;
    }
}
