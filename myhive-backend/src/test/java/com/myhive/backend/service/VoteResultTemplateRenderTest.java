package com.myhive.backend.service;

import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class VoteResultTemplateRenderTest {

    private static Context context(Locale locale) {
        Destination destination = new Destination();
        destination.setName("Prague");
        VoteSession session = new VoteSession();
        session.setDestination(destination);

        Context context = locale == null ? new Context() : new Context(locale);
        context.setVariable("session", session);
        context.setVariable("resultActivities", List.of());
        context.setVariable("resultUrl", "https://trivlu.com/vote/tok/result");
        return context;
    }

    @Test
    void ctaSaysBookIt() {
        String html = EmailTemplateTestSupport.engine().process("vote-result", context(null));

        assertThat(html)
                .contains(">Book it<")
                .contains("https://trivlu.com/vote/tok/result")
                .doesNotContain(">Open in Trip Builder<")
                .doesNotContain("??");
    }

    @Test
    void germanCtaSaysJetztBuchen() {
        String html = EmailTemplateTestSupport.engine().process("vote-result", context(Locale.GERMAN));

        assertThat(html).contains(">Jetzt buchen<").doesNotContain("??");
    }
}
