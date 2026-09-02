package com.myhive.backend.service;

import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class VoteReminderTemplateRenderTest {

    private static Context context(Locale locale, String pasteText) {
        Destination destination = new Destination();
        destination.setName("Prague");
        VoteSession session = new VoteSession();
        session.setDestination(destination);
        session.setNumberOfTravelers(12);

        Context context = locale == null ? new Context() : new Context(locale);
        context.setVariable("session", session);
        context.setVariable("missing", 5L);
        context.setVariable("travelers", 12);
        context.setVariable("pasteText", pasteText);
        context.setVariable("inviteUrl", "https://trivlu.com/vote/tok/activities?ref=invite");
        context.setVariable("dashboardUrl", "https://trivlu.com/vote/tok/waiting?manager=mgr-9");
        context.setVariable("supportEmail", "support@trivlu.com");
        return context;
    }

    @Test
    void rendersMissingCountPasteTextAndLinks() {
        String html = EmailTemplateTestSupport.engine().process("vote-reminder",
                context(null, "Hey, 5 of you still haven't voted: https://trivlu.com/vote/tok/activities?ref=invite"));

        assertThat(html)
                .contains("5 of 12 have not voted yet")
                .contains("closes in about 12 hours")
                .contains("Hey, 5 of you still haven&#39;t voted: https://trivlu.com/vote/tok/activities?ref=invite")
                .contains("https://trivlu.com/vote/tok/waiting?manager=mgr-9")
                .contains("Open your vote dashboard")
                .doesNotContain("??");
    }

    @Test
    void rendersGermanCopyForGermanLocale() {
        String html = EmailTemplateTestSupport.engine().process("vote-reminder", context(Locale.GERMAN, "Hey"));

        assertThat(html)
                .contains("5 von 12 haben noch nicht abgestimmt")
                .contains("Voting-Dashboard öffnen")
                .doesNotContain("??");
    }
}
