package com.myhive.backend.service;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TripReminderTemplateRenderTest {

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

    private Context baseContext() {
        Context context = new Context();
        context.setVariable("source", "QUIZ");
        context.setVariable("stage", 1);
        context.setVariable("lastTouch", false);
        context.setVariable("showConsultation", false);
        context.setVariable("destinationName", "Prague");
        context.setVariable("travelers", 6);
        context.setVariable("hasItems", false);
        context.setVariable("lines", List.of());
        context.setVariable("totalPrice", BigDecimal.ZERO);
        context.setVariable("recommendations", List.of());
        context.setVariable("restoreUrl", "https://trivlu.com/destination/prague?tab=trip-builder&restore=tok-1");
        context.setVariable("contactUrl", "https://trivlu.com/contact");
        context.setVariable("unsubscribeUrl", "https://trivlu.com/unsubscribe?token=unsub-1");
        context.setVariable("supportEmail", "support@trivlu.com");
        return context;
    }

    @Test
    void rendersCartLinesTotalAndLinks() {
        EmailService.ReminderLineView line = new EmailService.ReminderLineView();
        line.name = "Karting";
        line.price = new BigDecimal("50.00");
        line.lineTotal = new BigDecimal("300.00");
        line.groupMinApplies = false;
        EmailService.ReminderLineView floored = new EmailService.ReminderLineView();
        floored.name = "Shooting Range";
        floored.price = new BigDecimal("40.00");
        floored.lineTotal = new BigDecimal("400.00");
        floored.groupMinApplies = true;

        Context context = baseContext();
        context.setVariable("hasItems", true);
        context.setVariable("lines", List.of(line, floored));
        context.setVariable("totalPrice", new BigDecimal("700.00"));

        String html = engine().process("trip-reminder", context);

        assertThat(html)
                .contains("Karting")
                .contains("Shooting Range")
                .contains("group minimum")
                .contains("https://trivlu.com/destination/prague?tab=trip-builder&amp;restore=tok-1")
                .contains("https://trivlu.com/unsubscribe?token=unsub-1")
                .contains("Prague");
    }

    @Test
    void rendersRecommendationsWhenCartIsEmpty() {
        Context context = baseContext();
        context.setVariable("recommendations", List.of(
                Map.of("name", "Beer Bike", "price", new BigDecimal("35.00"))));

        String html = engine().process("trip-reminder", context);

        assertThat(html)
                .contains("Beer Bike")
                .contains("we picked for your group")
                .doesNotContain("group minimum");
    }

    @Test
    void rendersConsultationAndUrgencyBlocksByStage() {
        Context consultationContext = baseContext();
        consultationContext.setVariable("stage", 2);
        consultationContext.setVariable("showConsultation", true);
        String consultationHtml = engine().process("trip-reminder", consultationContext);

        Context urgencyContext = baseContext();
        urgencyContext.setVariable("stage", 3);
        urgencyContext.setVariable("lastTouch", true);
        String urgencyHtml = engine().process("trip-reminder", urgencyContext);

        assertThat(consultationHtml).contains("Need a hand?");
        assertThat(urgencyHtml).contains("fill up early");
        assertThat(consultationHtml).doesNotContain("fill up early");
    }
}
