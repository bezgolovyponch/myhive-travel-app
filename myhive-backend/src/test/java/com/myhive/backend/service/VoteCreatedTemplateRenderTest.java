package com.myhive.backend.service;

import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.VoteMode;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VoteCreatedTemplateRenderTest {

    private SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/templates/email/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }

    @Test
    void voteCreatedTemplateRendersLinksAndDestination() {
        Destination destination = new Destination();
        destination.setName("Bali");

        VoteSession session = new VoteSession();
        session.setDestination(destination);
        session.setNumberOfTravelers(2);
        session.setStartDate(LocalDate.of(2026, 8, 1));
        session.setEndDate(LocalDate.of(2026, 8, 10));
        session.setExpiresAt(LocalDateTime.of(2026, 8, 2, 12, 0));

        Context context = new Context();
        context.setVariable("session", session);
        context.setVariable("inviteUrl", "https://trivlu.com/vote/tok/activities?ref=invite");
        context.setVariable("dashboardUrl", "https://trivlu.com/vote/tok/waiting?manager=mgr-9");
        context.setVariable("supportEmail", "support@trivlu.com");
        context.setVariable("startDate", "August 1, 2026");
        context.setVariable("endDate", "August 10, 2026");
        context.setVariable("expiresAt", "August 2, 2026 at 12:00 UTC");

        String html = engine().process("vote-created", context);

        assertThat(html)
                .contains("Bali")
                .contains("https://trivlu.com/vote/tok/waiting?manager=mgr-9")
                .contains("https://trivlu.com/vote/tok/activities?ref=invite")
                .contains("mailto:support@trivlu.com")
                .contains("How it works")
                .contains("they swipe to vote on the activities")
                .contains("final itinerary to open in Trip Builder")
                .doesNotContain("they pick their favourites from your shortlist");
    }

    @Test
    void voteCreatedTemplateRendersCartCopyForCartSessions() {
        Destination destination = new Destination();
        destination.setName("Prague");

        VoteSession session = new VoteSession();
        session.setDestination(destination);
        session.setVoteMode(VoteMode.CART);
        session.setNumberOfTravelers(4);
        session.setStartDate(LocalDate.of(2026, 8, 1));
        session.setEndDate(LocalDate.of(2026, 8, 3));
        session.setExpiresAt(LocalDateTime.of(2026, 8, 2, 12, 0));

        Context context = new Context();
        context.setVariable("session", session);
        context.setVariable("inviteUrl", "https://trivlu.com/vote/tok/activities?ref=invite");
        context.setVariable("dashboardUrl", "https://trivlu.com/vote/tok/waiting?manager=mgr-9");
        context.setVariable("supportEmail", "support@trivlu.com");
        context.setVariable("startDate", "August 1, 2026");
        context.setVariable("endDate", "August 3, 2026");
        context.setVariable("expiresAt", "August 2, 2026 at 12:00 UTC");

        String html = engine().process("vote-created", context);

        assertThat(html)
                .contains("Prague")
                .contains("they swipe to vote on the activities")
                .doesNotContain("they pick their favourites from your shortlist")
                .doesNotContain("final itinerary to open in Trip Builder");
    }
}
