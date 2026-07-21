package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.ContactRequest;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.exception.EmailSendException;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.model.VoteMode;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private AsyncMailSender asyncMailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@trivlu.com");
        ReflectionTestUtils.setField(emailService, "contactToEmail", "info@trivlu.com");
        ReflectionTestUtils.setField(emailService, "bookingsToEmail", "booking@trivlu.com");
        ReflectionTestUtils.setField(emailService, "emailEnabled", true);
    }

    @Test
    void sendContactNotification_whenEmailDisabled_throwsSoTheUserSeesTheFailure() {
        ReflectionTestUtils.setField(emailService, "emailEnabled", false);
        ContactRequest request = new ContactRequest();
        request.setName("Bob");
        request.setEmail("bob@example.com");
        request.setSubject("Hi");
        request.setMessage("Hello there");

        // A contact submission has no durable record behind it — a silent skip would discard
        // the message while the controller reports success. Disabled email must fail loudly here.
        assertThatThrownBy(() -> emailService.sendContactNotification(request))
                .isInstanceOf(EmailSendException.class)
                .hasMessageContaining("disabled");

        verifyNoInteractions(mailSender, asyncMailSender, templateEngine);
    }

    @Test
    void sendItineraryConfirmation_whenEmailDisabled_skipsSendWithoutError() {
        ReflectionTestUtils.setField(emailService, "emailEnabled", false);
        TripExportRequest request = new TripExportRequest();
        request.setDestinations(List.of());

        assertThatCode(() -> emailService.sendItineraryConfirmation("customer@example.com", "Alice", request, "TRV-1"))
                .doesNotThrowAnyException();

        verifyNoInteractions(mailSender, asyncMailSender, templateEngine);
    }

    @Test
    void sendPaymentReceived_whenEmailDisabled_skipsSendWithoutError() {
        ReflectionTestUtils.setField(emailService, "emailEnabled", false);

        assertThatCode(() -> emailService.sendPaymentReceived("payer@example.com", "Alice", "TRV-1",
                new BigDecimal("30.00"), new BigDecimal("100.00"), false))
                .doesNotThrowAnyException();

        verifyNoInteractions(mailSender, asyncMailSender, templateEngine);
    }

    @Test
    void sendBookingNotification_sendsToBookingsInboxWithTripIdSubjectAndCustomerReplyTo() throws Exception {
        String expectedTripId = "TRV-ABCD1234";
        String expectedReplyTo = "customer@example.com";
        Booking booking = new Booking();
        booking.setTripId(expectedTripId);
        booking.setUserEmail(expectedReplyTo);
        booking.setCustomerName("Jane Traveller");
        booking.setTotalAmount(new BigDecimal("120.00"));

        TripExportRequest request = new TripExportRequest();
        request.setDestinations(List.of());

        // Real MimeMessage so the to-address / subject / reply-to set via MimeMessageHelper are retrievable.
        MimeMessage realMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(realMessage);
        when(templateEngine.process(eq("booking-notification"), any())).thenReturn("<html>booking</html>");

        emailService.sendBookingNotification(booking, request);

        assertThat(realMessage.getAllRecipients()).hasSize(1);
        assertThat(realMessage.getAllRecipients()[0].toString()).isEqualTo("booking@trivlu.com");
        assertThat(realMessage.getSubject()).contains(expectedTripId);
        // Reply-To points at the customer so staff can respond directly.
        assertThat(realMessage.getReplyTo()[0].toString()).isEqualTo(expectedReplyTo);
        verify(asyncMailSender).send(eq(realMessage), anyString());
    }

    @Test
    void buildDestinationViewsGroupsByPackageAndComputesDiscount() {
        UUID packageId = UUID.randomUUID();

        TripExportRequest.ActivityExport a1 = new TripExportRequest.ActivityExport();
        a1.setActivityName("A1");
        a1.setPrice(100.0);
        a1.setPackageId(packageId);
        a1.setPackageName("My Package");
        a1.setPackageDiscountPct(new BigDecimal("10.00"));

        TripExportRequest.ActivityExport a2 = new TripExportRequest.ActivityExport();
        a2.setActivityName("A2");
        a2.setPrice(200.0);
        a2.setPackageId(packageId);
        a2.setPackageName("My Package");
        a2.setPackageDiscountPct(new BigDecimal("10.00"));

        TripExportRequest.ActivityExport standalone = new TripExportRequest.ActivityExport();
        standalone.setActivityName("Standalone");
        standalone.setPrice(50.0);

        TripExportRequest.DestinationExport dest = new TripExportRequest.DestinationExport();
        dest.setDestinationName("Bali");
        dest.setActivities(List.of(a1, a2, standalone));

        TripExportRequest req = new TripExportRequest();
        req.setDestinations(List.of(dest));

        List<EmailService.DestinationView> views = emailService.buildDestinationViews(req);

        assertThat(views).hasSize(1);
        EmailService.DestinationView view = views.getFirst();
        assertThat(view.packageGroups).hasSize(1);
        EmailService.PackageGroup group = view.packageGroups.getFirst();
        assertThat(group.packageName).isEqualTo("My Package");
        assertThat(group.subtotal).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(group.discounted).isEqualByComparingTo(new BigDecimal("270.00"));
        assertThat(group.activities).hasSize(2);
        assertThat(view.standaloneActivities).hasSize(1);
        assertThat(view.standaloneActivities.getFirst().activity.getActivityName()).isEqualTo("Standalone");
    }

    @Test
    void buildDestinationViews_flagsGroupMinimumOnlyWhenFloorBinds() {
        TripExportRequest.ActivityExport floored = new TripExportRequest.ActivityExport();
        floored.setActivityName("Boat Rental");
        floored.setPrice(50.0);
        floored.setMinPrice(new BigDecimal("300.00"));
        TripExportRequest.ActivityExport regular = new TripExportRequest.ActivityExport();
        regular.setActivityName("Bar Crawl");
        regular.setPrice(40.0);

        TripExportRequest.DestinationExport dest = new TripExportRequest.DestinationExport();
        dest.setDestinationName("Tenerife");
        dest.setActivities(List.of(floored, regular));
        TripExportRequest req = new TripExportRequest();
        req.setNumberOfTravelers(2);
        req.setDestinations(List.of(dest));

        List<EmailService.DestinationView> views = emailService.buildDestinationViews(req);

        List<EmailService.ActivityLineView> lines = views.getFirst().standaloneActivities;
        assertThat(lines.getFirst().groupMinApplies).isTrue();  // 2 × €50 = €100 < €300
        assertThat(lines.get(1).groupMinApplies).isFalse();     // no minimum set
    }

    @Test
    void sendContactNotification_sendsSynchronouslyViaMailSender() {
        ContactRequest request = new ContactRequest();
        request.setName("Bob");
        request.setEmail("bob@example.com");
        request.setSubject("Hi");
        request.setMessage("Hello there");

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("contact-notification"), any())).thenReturn("<html>ok</html>");

        emailService.sendContactNotification(request);

        // The contact form must send synchronously: a failed delivery has to surface to the user,
        // because the contact message is the only email with no durable record behind it. It must
        // NOT be handed to the fire-and-forget async sender.
        verify(mailSender).send(mimeMessage);
        verifyNoInteractions(asyncMailSender);
    }

    @Test
    void sendVoteResult_masksRecipientInAsyncDescription() throws Exception {
        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        Destination destination = new Destination();
        destination.setName("Bali");
        session.setDestination(destination);
        session.setInitiatorEmail("alice@example.com");

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("vote-result"), any())).thenReturn("<html>test</html>");

        emailService.sendVoteResult(session, List.of(), "https://trivlu.com");

        // The description handed to the async sender is logged — it must carry the masked address,
        // never the raw initiator email (PII-masking is a project-wide convention for vote emails).
        ArgumentCaptor<String> descriptionCaptor = ArgumentCaptor.forClass(String.class);
        verify(asyncMailSender).send(eq(mimeMessage), descriptionCaptor.capture());
        assertThat(descriptionCaptor.getValue())
                .contains("a****@example.com")
                .doesNotContain("alice@example.com");
    }

    @Test
    void sendVoteResult_doesNotThrowWhenMailSucceeds() throws Exception {
        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());

        Destination destination = new Destination();
        destination.setName("Bali");
        session.setDestination(destination);
        session.setInitiatorEmail("alice@example.com");

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("vote-result"), any())).thenReturn("<html>test</html>");

        assertThatCode(() -> emailService.sendVoteResult(session, List.of(), "https://trivlu.com"))
                .doesNotThrowAnyException();

        // The blocking SMTP send is delegated to the async sender, not invoked inline.
        verify(asyncMailSender).send(eq(mimeMessage), anyString());
    }

    @Test
    void sendVoteResult_cartSession_linksToResultPageNotTripBuilder() throws Exception {
        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setVoteMode(VoteMode.CART);

        Destination destination = new Destination();
        destination.setName("Prague");
        destination.setSlug("prague");
        session.setDestination(destination);
        session.setInitiatorEmail("alice@example.com");

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("vote-result"), contextCaptor.capture())).thenReturn("<html>test</html>");

        emailService.sendVoteResult(session, List.of(), "https://trivlu.com");

        // CART annotates the initiator's own cart — it never seeds one — so another
        // device following the Trip Builder deep link would land on an empty cart.
        // The email must point CART results at the read-only result page instead.
        String resultUrl = (String) contextCaptor.getValue().getVariable("resultUrl");
        assertThat(resultUrl).isEqualTo("https://trivlu.com/vote/" + session.getShareToken() + "/result");
    }

    @Test
    void sendVoteResult_quizSession_stillLinksToTripBuilderTab() throws Exception {
        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        // voteMode defaults to QUIZ (not set explicitly).

        Destination destination = new Destination();
        destination.setName("Prague");
        destination.setSlug("prague");
        session.setDestination(destination);
        session.setInitiatorEmail("alice@example.com");

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("vote-result"), contextCaptor.capture())).thenReturn("<html>test</html>");

        emailService.sendVoteResult(session, List.of(), "https://trivlu.com");

        String resultUrl = (String) contextCaptor.getValue().getVariable("resultUrl");
        assertThat(resultUrl)
                .contains("/destination/prague")
                .contains("tab=trip-builder")
                .contains("voteSession=" + session.getShareToken());
    }

    @Test
    void sendVoteCreatedConfirmation_buildsLinksWithManagerTokenAndSends() throws Exception {
        UUID shareToken = UUID.randomUUID();
        UUID managerToken = UUID.randomUUID();

        Destination destination = new Destination();
        destination.setName("Bali");
        destination.setSlug("bali");

        VoteSession session = new VoteSession();
        session.setShareToken(shareToken);
        session.setManagerToken(managerToken);
        session.setInitiatorEmail("alice@example.com");
        session.setNumberOfTravelers(2);
        session.setStartDate(LocalDate.of(2026, 8, 1));
        session.setEndDate(LocalDate.of(2026, 8, 10));
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
        session.setDestination(destination);

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("vote-created"), contextCaptor.capture()))
                .thenReturn("<html>ok</html>");

        emailService.sendVoteCreatedConfirmation(session, "https://trivlu.com");

        Context context = contextCaptor.getValue();
        String dashboardUrl = (String) context.getVariable("dashboardUrl");
        String inviteUrl = (String) context.getVariable("inviteUrl");
        assertThat(dashboardUrl)
                .contains("/vote/" + shareToken + "/waiting")
                .contains("manager=" + managerToken);
        assertThat(inviteUrl)
                .isEqualTo("https://trivlu.com/vote/" + shareToken + "/activities?ref=invite");
        assertThat(context.getVariable("supportEmail")).isEqualTo("support@trivlu.com");
        verify(asyncMailSender).send(eq(mimeMessage), anyString());
    }

    @Test
    void sendItineraryConfirmation_includesTripIdInTemplateContext() throws Exception {
        TripExportRequest request = new TripExportRequest();
        request.setTripName("Bali Adventure");
        request.setDestinations(List.of());

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("itinerary-confirmation"), contextCaptor.capture()))
                .thenReturn("<html>ok</html>");

        String expectedTripId = "TRV-ABCD1234";
        emailService.sendItineraryConfirmation("customer@example.com", "Alice", request, expectedTripId);

        Context context = contextCaptor.getValue();
        assertThat(context.getVariable("tripId")).isEqualTo(expectedTripId);
    }

    @Test
    void sendItineraryConfirmation_withDeposit_setsPaymentContext() throws Exception {
        TripExportRequest request = new TripExportRequest();
        request.setTripName("Prague");
        request.setDestinations(List.of());

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("itinerary-confirmation"), contextCaptor.capture()))
                .thenReturn("<html>ok</html>");

        emailService.sendItineraryConfirmation("customer@example.com", "Alice", request, "TRV-1",
                new BigDecimal("12.00"), new BigDecimal("40.00"));

        Context context = contextCaptor.getValue();
        assertThat(context.getVariable("depositPaid")).isEqualTo(true);
        assertThat(context.getVariable("amountPaid")).isEqualTo(new BigDecimal("12.00"));
        assertThat(context.getVariable("balanceDue")).isEqualTo(new BigDecimal("28.00"));
    }

    @Test
    void sendItineraryConfirmation_lead_hasNoDepositInContext() throws Exception {
        TripExportRequest request = new TripExportRequest();
        request.setTripName("Prague");
        request.setDestinations(List.of());

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("itinerary-confirmation"), contextCaptor.capture()))
                .thenReturn("<html>ok</html>");

        // 4-arg overload (lead flow) → no deposit line.
        emailService.sendItineraryConfirmation("customer@example.com", "Alice", request, "TRV-1");

        assertThat(contextCaptor.getValue().getVariable("depositPaid")).isEqualTo(false);
    }

    @Test
    void sendBookingNotification_withPaidDeposit_setsPaymentContext() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("booking-notification"), contextCaptor.capture()))
                .thenReturn("<html>ok</html>");

        Booking booking = TestDataFactory.booking(BookingStatus.DEPOSIT_PAID);
        booking.setTotalAmount(new BigDecimal("40.00"));
        booking.setAmountPaid(new BigDecimal("12.00"));
        TripExportRequest request = new TripExportRequest();
        request.setTripName("Prague");
        request.setDestinations(List.of());

        emailService.sendBookingNotification(booking, request);

        Context context = contextCaptor.getValue();
        assertThat(context.getVariable("depositPaid")).isEqualTo(true);
        assertThat(context.getVariable("amountPaid")).isEqualTo(new BigDecimal("12.00"));
        assertThat(context.getVariable("balanceDue")).isEqualTo(new BigDecimal("28.00"));
    }

    @Test
    void sendPaymentReceived_rendersTemplateWithAmounts() throws Exception {
        jakarta.mail.internet.MimeMessage mimeMessage = mock(jakarta.mail.internet.MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ArgumentCaptor<org.thymeleaf.context.Context> ctx = ArgumentCaptor.forClass(org.thymeleaf.context.Context.class);
        when(templateEngine.process(eq("payment-received"), ctx.capture())).thenReturn("<html>ok</html>");

        emailService.sendPaymentReceived("payer@example.com", "Alice", "TRV-ABCD1234",
                new BigDecimal("30.00"), new BigDecimal("100.00"), false);

        assertThat(ctx.getValue().getVariable("tripId")).isEqualTo("TRV-ABCD1234");
        assertThat(ctx.getValue().getVariable("fullyPaid")).isEqualTo(false);
        verify(asyncMailSender).send(eq(mimeMessage), anyString());
    }

    @Test
    void sendConsultationLead_sendsToContactInbox() throws Exception {
        jakarta.mail.internet.MimeMessage mimeMessage = mock(jakarta.mail.internet.MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("consultation-lead"), any(org.thymeleaf.context.Context.class)))
                .thenReturn("<html>lead</html>");

        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        emailService.sendConsultationLead(booking);

        verify(asyncMailSender).send(eq(mimeMessage), anyString());
    }

    @Test
    void sendItineraryConfirmation_doesNotExposeUtmFieldsInContext() throws Exception {
        TripExportRequest request = new TripExportRequest();
        request.setTripName("Bali Adventure");
        request.setDestinations(List.of());
        request.setUtmSource("google");
        request.setUtmMedium("cpc");
        request.setUtmCampaign("summer");

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("itinerary-confirmation"), contextCaptor.capture()))
                .thenReturn("<html>ok</html>");

        emailService.sendItineraryConfirmation("customer@example.com", "Alice", request, "TRV-ABCD1234");

        Context context = contextCaptor.getValue();
        assertThat(context.getVariableNames()).doesNotContain("utmSource", "utmMedium", "utmCampaign");
    }

    @Test
    void sendVoteCreatedConfirmation_setsRecipientAndSubjectNamingDestination() throws Exception {
        Destination destination = new Destination();
        destination.setName("Bali");

        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setInitiatorEmail("alice@example.com");
        session.setNumberOfTravelers(2);
        session.setStartDate(LocalDate.of(2026, 8, 1));
        session.setEndDate(LocalDate.of(2026, 8, 10));
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
        session.setDestination(destination);

        // Use a real MimeMessage so the to-address and subject set via MimeMessageHelper
        // are actually retrievable (a mock would not record them).
        MimeMessage realMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(realMessage);
        when(templateEngine.process(eq("vote-created"), any())).thenReturn("<html>ok</html>");

        emailService.sendVoteCreatedConfirmation(session, "https://trivlu.com");

        assertThat(realMessage.getAllRecipients()).hasSize(1);
        assertThat(realMessage.getAllRecipients()[0].toString()).isEqualTo("alice@example.com");
        assertThat(realMessage.getSubject()).contains("Bali");
    }
}
