package com.myhive.backend.service;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ContactsDigestTemplateRenderTest {

    @Test
    void rendersEveryRowAndTheAdminLink() {
        String expectedAdminUrl = "https://trivlu.com/admin/contacts";
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("heading", "2 new contacts");
        context.setVariable("digestDate", "2026-09-13");
        context.setVariable("adminUrl", expectedAdminUrl);
        context.setVariable("contacts", List.of(
                new EmailService.ContactDigestRow("anna@example.com", "Anna Example", "VOTE → BOOKING",
                        "2026-09-12 08:30 UTC", "DE", false),
                new EmailService.ContactDigestRow("bob@example.com", "", "CONTACT_FORM",
                        "2026-09-12 19:05 UTC", "EN", true)));

        String html = EmailTemplateTestSupport.engine().process("contacts-digest", context);

        assertThat(html)
                .contains("2 new contacts")
                .contains("2026-09-13")
                .contains("anna@example.com")
                .contains("Anna Example")
                .contains("VOTE → BOOKING")
                .contains("2026-09-12 08:30 UTC")
                .contains("bob@example.com")
                .contains("CONTACT_FORM")
                .contains("Unsubscribed")
                .contains(expectedAdminUrl)
                .doesNotContain("??");
        assertThat(html.indexOf("anna@example.com")).isLessThan(html.indexOf("bob@example.com"));
    }
}
