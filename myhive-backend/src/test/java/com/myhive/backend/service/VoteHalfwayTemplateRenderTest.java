package com.myhive.backend.service;

import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class VoteHalfwayTemplateRenderTest {

    private static Context context(Locale locale) {
        Destination destination = new Destination();
        destination.setName("Prague");
        VoteSession session = new VoteSession();
        session.setDestination(destination);
        session.setNumberOfTravelers(12);

        Context context = locale == null ? new Context() : new Context(locale);
        context.setVariable("session", session);
        context.setVariable("voters", 6L);
        context.setVariable("travelers", 12);
        context.setVariable("standings", List.of(
                new EmailService.VoteStandingView("Bar Crawl", 4),
                new EmailService.VoteStandingView("Karting", 1)));
        context.setVariable("dashboardUrl", "https://trivlu.com/vote/tok/waiting?manager=mgr-9");
        context.setVariable("expiresAt", "August 2, 2026 at 12:00 UTC");
        context.setVariable("supportEmail", "support@trivlu.com");
        return context;
    }

    @Test
    void rendersCountStandingsAndDashboardLink() {
        String html = EmailTemplateTestSupport.engine().process("vote-halfway", context(null));

        assertThat(html)
                .contains("6 of 12 have voted")
                .contains("Prague")
                .contains("Bar Crawl")
                .contains("4 ♥")
                .contains("Karting")
                .contains("1 ♥")
                .contains("https://trivlu.com/vote/tok/waiting?manager=mgr-9")
                .contains("See live results")
                .contains("August 2, 2026 at 12:00 UTC")
                .contains("mailto:support@trivlu.com")
                .doesNotContain("??");
        assertThat(html.indexOf("Bar Crawl")).isLessThan(html.indexOf("Karting"));
    }

    @Test
    void rendersGermanCopyForGermanLocale() {
        String html = EmailTemplateTestSupport.engine().process("vote-halfway", context(Locale.GERMAN));

        assertThat(html)
                .contains("6 von 12 haben abgestimmt")
                .contains("Live-Ergebnisse ansehen")
                .doesNotContain("??");
    }
}
