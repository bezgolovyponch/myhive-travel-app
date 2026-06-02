package com.myhive.backend.service;

import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@trivlu.com");
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
        assertThat(view.standaloneActivities.getFirst().getActivityName()).isEqualTo("Standalone");
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
        doNothing().when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> emailService.sendVoteResult(session, List.of(), "https://trivlu.com"))
                .doesNotThrowAnyException();
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
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailService.sendVoteCreatedConfirmation(session, "https://trivlu.com");

        Context context = contextCaptor.getValue();
        String dashboardUrl = (String) context.getVariable("dashboardUrl");
        String inviteUrl = (String) context.getVariable("inviteUrl");
        assertThat(dashboardUrl)
                .contains("/vote/" + shareToken + "/waiting")
                .contains("manager=" + managerToken);
        assertThat(inviteUrl)
                .isEqualTo("https://trivlu.com/vote/" + shareToken + "/activities");
        assertThat(context.getVariable("supportEmail")).isEqualTo("support@trivlu.com");
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }
}
