package com.myhive.backend.service;

import com.myhive.backend.dto.ContactRequest;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.dto.VotePoolActivityDTO;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.TripLead;
import com.myhive.backend.entity.TripLeadActivity;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.exception.EmailSendException;
import com.myhive.backend.model.TripLeadSource;
import com.myhive.backend.model.VoteMode;
import com.myhive.backend.util.MoneyMath;
import com.myhive.backend.util.Translations;
import jakarta.mail.internet.MimeMessage;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    private static final String SUPPORT_EMAIL = "support@trivlu.com";

    // Customer-facing emails render in the locale the customer was browsing in
    // (VoteSession/TripLead/Booking.locale, null = English): the Thymeleaf
    // context carries the Locale so the templates' #{...} keys resolve from
    // messages_<locale>.properties, subjects come from the same bundle, dates
    // are spelled in that language, and links into the frontend carry the
    // locale prefix (/de/...). Staff notifications stay English.
    private static Locale localeOf(String code) {
        String lc = Translations.normalize(code);
        return lc == null ? Locale.ENGLISH : Locale.forLanguageTag(lc);
    }

    /** "" for English, "/de" otherwise — the frontend's URL prefix for that locale. */
    private static String localePrefix(String code) {
        String lc = Translations.normalize(code);
        return lc == null ? "" : "/" + lc;
    }

    private static String formatDate(LocalDate date, Locale locale) {
        String pattern = Locale.GERMAN.getLanguage().equals(locale.getLanguage()) ? "d. MMMM yyyy" : "MMMM d, yyyy";
        return date.format(DateTimeFormatter.ofPattern(pattern, locale));
    }

    private static String formatDateTime(LocalDateTime dateTime, Locale locale) {
        String pattern = Locale.GERMAN.getLanguage().equals(locale.getLanguage())
                ? "d. MMMM yyyy 'um' HH:mm 'UTC'" : "MMMM d, yyyy 'at' HH:mm 'UTC'";
        return dateTime.format(DateTimeFormatter.ofPattern(pattern, locale));
    }

    private String msg(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }

    public static class DestinationView {
        public String destinationName;
        public String country;
        public Integer duration;
        public String startDate;
        public String endDate;
        public List<PackageGroup> packageGroups = new ArrayList<>();
        public List<ActivityLineView> standaloneActivities = new ArrayList<>();
    }

    public static class PackageGroup {
        public String packageName;
        public BigDecimal discountPct;
        public BigDecimal subtotal;
        public BigDecimal discounted;
        public List<ActivityLineView> activities = new ArrayList<>();
    }

    /** One itinerary line: the exported activity plus whether its group minimum binds. */
    public static class ActivityLineView {
        public TripExportRequest.ActivityExport activity;
        public boolean groupMinApplies;

        ActivityLineView(TripExportRequest.ActivityExport activity, boolean groupMinApplies) {
            this.activity = activity;
            this.groupMinApplies = groupMinApplies;
        }
    }

    /** Everything that varies between the outbound emails; {@link #send(EmailSpec)} does the rest. */
    @Builder
    private record EmailSpec(
            String to,
            String replyTo,
            String subject,
            String template,
            Map<String, Object> variables,
            Map<String, String> headers,
            String description,
            boolean synchronous,
            /** Rendering locale for the template's #{...} keys; null = English. */
            Locale locale) {
    }

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AsyncMailSender asyncMailSender;
    private final MessageSource messageSource;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.email.contact-to}")
    private String contactToEmail;

    @Value("${app.email.bookings-to}")
    private String bookingsToEmail;

    /** Public base URL of this API (RFC 8058 one-click unsubscribe target); blank = headers omitted. */
    @Value("${app.api.public-url:}")
    private String apiPublicUrl;

    public void sendItineraryConfirmation(String toEmail, String customerName, TripExportRequest tripData, String tripId) {
        sendItineraryConfirmation(toEmail, customerName, tripData, tripId, null, null);
    }

    /** Overload that also renders a payment summary (deposit paid / balance due) — used by the deposit flow. */
    public void sendItineraryConfirmation(String toEmail, String customerName, TripExportRequest tripData,
            String tripId, BigDecimal amountPaid, BigDecimal totalAmount) {
        // The customer's locale rides on the request (set by the booking form, or copied
        // from the Booking when PaymentService rebuilds the request after a deposit).
        Locale locale = localeOf(tripData.getLocale());
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("customerName", customerName);
        variables.put("tripData", tripData);
        variables.put("destinationViews", buildDestinationViews(tripData));
        variables.put("bookingDate", formatDate(LocalDate.now(), locale));
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
                .subject(msg(locale, "email.itinerary.subject"))
                .template("itinerary-confirmation")
                .variables(variables)
                .locale(locale)
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
        Locale locale = localeOf(session.getLocale());
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("session", session);
        variables.put("resultActivities", resultActivities);
        variables.put("resultUrl", resultUrlFor(session, frontendUrl + localePrefix(session.getLocale())));

        send(EmailSpec.builder()
                .to(session.getInitiatorEmail())
                .subject(msg(locale, "email.voteResult.subject", session.getDestination().getName()))
                .template("vote-result")
                .variables(variables)
                .locale(locale)
                .description("vote result to " + maskEmail(session.getInitiatorEmail()))
                .build());
    }

    public void sendVoteCreatedConfirmation(VoteSession session, String frontendUrl) {
        Locale locale = localeOf(session.getLocale());
        String base = frontendUrl + localePrefix(session.getLocale());
        String shareToken = session.getShareToken().toString();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("session", session);
        variables.put("inviteUrl", base + "/vote/" + shareToken + "/activities?ref=invite");
        variables.put("dashboardUrl", base + "/vote/" + shareToken + "/waiting?manager=" + session.getManagerToken());
        variables.put("supportEmail", SUPPORT_EMAIL);
        variables.put("startDate", formatDate(session.getStartDate(), locale));
        variables.put("endDate", formatDate(session.getEndDate(), locale));
        variables.put("expiresAt", formatDateTime(session.getExpiresAt(), locale));

        send(EmailSpec.builder()
                .to(session.getInitiatorEmail())
                .subject(msg(locale, "email.voteCreated.subject", session.getDestination().getName()))
                .template("vote-created")
                .variables(variables)
                .locale(locale)
                .description("vote-created confirmation to " + maskEmail(session.getInitiatorEmail()))
                .build());
    }

    public void sendPaymentReceived(String toEmail, String customerName, String tripId,
            BigDecimal amountPaid, BigDecimal totalAmount, boolean fullyPaid) {
        sendPaymentReceived(toEmail, customerName, tripId, amountPaid, totalAmount, fullyPaid, null);
    }

    public void sendPaymentReceived(String toEmail, String customerName, String tripId,
            BigDecimal amountPaid, BigDecimal totalAmount, boolean fullyPaid, String localeCode) {
        Locale locale = localeOf(localeCode);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("customerName", customerName);
        variables.put("tripId", tripId);
        variables.put("amountPaid", amountPaid);
        variables.put("totalAmount", totalAmount);
        variables.put("fullyPaid", fullyPaid);

        send(EmailSpec.builder()
                .to(toEmail)
                .subject(msg(locale, fullyPaid ? "email.paymentReceived.subject.full" : "email.paymentReceived.subject.partial"))
                .template("payment-received")
                .variables(variables)
                .locale(locale)
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

    /** One reminder itinerary line: snapshot name/price plus the floored group total. */
    public static class ReminderLineView {
        public String name;
        public BigDecimal price;
        public BigDecimal lineTotal;
        public boolean groupMinApplies;
    }

    public void sendTripReminder(TripLead lead, int stage, List<TripLeadActivity> items,
            List<VotePoolActivityDTO> recommendations, String frontendUrl) {
        Locale locale = localeOf(lead.getLocale());
        String base = frontendUrl + localePrefix(lead.getLocale());
        String destinationName = lead.getDestination() == null
                ? msg(locale, "email.tripReminder.yourTrip") : lead.getDestination().getName();
        int travelers = lead.getNumberOfTravelers() == null || lead.getNumberOfTravelers() < 1
                ? 1 : lead.getNumberOfTravelers();
        List<ReminderLineView> lines = items.stream()
                .map(item -> toReminderLine(item, travelers))
                .toList();
        BigDecimal totalPrice = lines.stream()
                .map(line -> line.lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean lastTouch = lead.getSource() == TripLeadSource.VOTE ? stage >= 2 : stage >= 3;
        boolean showConsultation = lead.getSource() == TripLeadSource.VOTE ? stage == 1 : stage == 2;

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("source", lead.getSource().name());
        variables.put("stage", stage);
        variables.put("lastTouch", lastTouch);
        variables.put("showConsultation", showConsultation);
        variables.put("destinationName", destinationName);
        variables.put("travelers", travelers);
        variables.put("hasItems", !lines.isEmpty());
        variables.put("lines", lines);
        variables.put("totalPrice", totalPrice);
        variables.put("recommendations", recommendations);
        variables.put("restoreUrl", restoreUrlFor(lead, base));
        variables.put("contactUrl", base + "/contact");
        variables.put("unsubscribeUrl", base + "/unsubscribe?token=" + lead.getUnsubscribeToken());
        variables.put("supportEmail", SUPPORT_EMAIL);

        send(EmailSpec.builder()
                .to(lead.getEmail())
                .subject(reminderSubject(locale, lead.getSource(), stage, destinationName))
                .template("trip-reminder")
                .variables(variables)
                .headers(unsubscribeHeaders(lead))
                .locale(locale)
                .description("trip reminder (stage " + stage + ") to " + maskEmail(lead.getEmail()))
                .build());
    }

    private static ReminderLineView toReminderLine(TripLeadActivity item, int travelers) {
        ReminderLineView line = new ReminderLineView();
        line.name = item.getActivityName();
        line.price = item.getPrice();
        BigDecimal groupTotal = item.getPrice().multiply(BigDecimal.valueOf(travelers));
        boolean floored = item.getMinPrice() != null && groupTotal.compareTo(item.getMinPrice()) < 0;
        line.groupMinApplies = floored;
        line.lineTotal = floored ? item.getMinPrice() : groupTotal;
        return line;
    }

    /** Mirrors resultUrlFor: the Trip Builder tab deep link, restore token appended. */
    private static String restoreUrlFor(TripLead lead, String frontendUrl) {
        String destinationSlug = lead.getDestination() == null ? null : lead.getDestination().getSlug();
        if (destinationSlug == null) {
            return frontendUrl;
        }
        return frontendUrl + "/destination/" + destinationSlug
                + "?tab=trip-builder&restore=" + lead.getRestoreToken();
    }

    private String reminderSubject(Locale locale, TripLeadSource source, int stage, String destinationName) {
        String key;
        if (source == TripLeadSource.VOTE) {
            key = stage == 1 ? "email.tripReminder.subject.vote1" : "email.tripReminder.subject.vote2";
        } else {
            key = switch (stage) {
                case 1 -> "email.tripReminder.subject.quiz1";
                case 2 -> "email.tripReminder.subject.quiz2";
                default -> "email.tripReminder.subject.quiz3";
            };
        }
        return msg(locale, key, destinationName);
    }

    private Map<String, String> unsubscribeHeaders(TripLead lead) {
        if (apiPublicUrl == null || apiPublicUrl.isBlank()) {
            return Map.of();
        }
        String oneClickUrl = apiPublicUrl + "/leads/unsubscribe/one-click?token=" + lead.getUnsubscribeToken();
        return Map.of(
                "List-Unsubscribe", "<" + oneClickUrl + ">",
                "List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
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
            if (spec.headers() != null) {
                for (Map.Entry<String, String> header : spec.headers().entrySet()) {
                    message.setHeader(header.getKey(), header.getValue());
                }
            }

            Context context = new Context(spec.locale() == null ? Locale.ENGLISH : spec.locale());
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
        int travelers = tripData.getNumberOfTravelers() != null && tripData.getNumberOfTravelers() > 0
                ? tripData.getNumberOfTravelers() : 1;
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
                    ActivityLineView line = new ActivityLineView(activity, groupMinApplies(activity, travelers));
                    UUID packageId = activity.getPackageId();
                    if (packageId == null) {
                        view.standaloneActivities.add(line);
                    } else {
                        PackageGroup group = groupMap.computeIfAbsent(packageId, id -> {
                            PackageGroup g = new PackageGroup();
                            g.packageName = activity.getPackageName();
                            g.discountPct = activity.getPackageDiscountPct();
                            return g;
                        });
                        group.activities.add(line);
                    }
                }
                for (PackageGroup group : groupMap.values()) {
                    BigDecimal subtotal = BigDecimal.ZERO;
                    for (ActivityLineView line : group.activities) {
                        BigDecimal activityPrice = line.activity.getPrice() != null
                                ? BigDecimal.valueOf(line.activity.getPrice())
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

    /** True when price × travelers < minPrice — the booked total for this line is the group minimum. */
    private static boolean groupMinApplies(TripExportRequest.ActivityExport activity, int travelers) {
        if (activity.getMinPrice() == null || activity.getPrice() == null) {
            return false;
        }
        BigDecimal line = BigDecimal.valueOf(activity.getPrice())
                .multiply(BigDecimal.valueOf(travelers));
        return line.compareTo(activity.getMinPrice()) < 0;
    }
}
