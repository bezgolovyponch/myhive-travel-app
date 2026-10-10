package com.myhive.backend.service;

import com.myhive.backend.dto.TripExportRequest;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The itinerary confirmation and the internal booking notification list each line's extras. */
class ItineraryExtrasTemplateRenderTest {

    private SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/templates/email/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        templateEngine.setTemplateEngineMessageSource(messages);
        return templateEngine;
    }

    /** One destination with the same extra-carrying line once in a package and once standalone. */
    private static List<EmailService.DestinationView> viewsWithExtras() {
        EmailService.ExtraLineView show = new EmailService.ExtraLineView();
        show.name = "Private show";
        show.amount = new BigDecimal("250.00");
        show.perPerson = false;
        EmailService.ExtraLineView photographer = new EmailService.ExtraLineView();
        photographer.name = "Photographer";
        photographer.amount = new BigDecimal("60.00");
        photographer.perPerson = true;

        TripExportRequest.ActivityExport standaloneActivity = new TripExportRequest.ActivityExport();
        standaloneActivity.setActivityName("Karting");
        standaloneActivity.setPrice(40.0);
        EmailService.ActivityLineView standalone = new EmailService.ActivityLineView(standaloneActivity, false);
        standalone.extras.add(show);

        TripExportRequest.ActivityExport packagedActivity = new TripExportRequest.ActivityExport();
        packagedActivity.setActivityName("Shooting");
        packagedActivity.setPrice(80.0);
        packagedActivity.setPackageId(UUID.randomUUID());
        EmailService.ActivityLineView packaged = new EmailService.ActivityLineView(packagedActivity, false);
        packaged.extras.add(photographer);
        EmailService.PackageGroup group = new EmailService.PackageGroup();
        group.packageName = "Classic";
        group.discountPct = new BigDecimal("10.00");
        group.subtotal = new BigDecimal("80.00");
        group.discounted = new BigDecimal("72.00");
        group.activities.add(packaged);

        EmailService.DestinationView view = new EmailService.DestinationView();
        view.destinationName = "Prague";
        view.duration = 2;
        view.packageGroups.add(group);
        view.standaloneActivities.add(standalone);
        return List.of(view);
    }

    private static Context itineraryContext(Locale locale) {
        TripExportRequest tripData = new TripExportRequest();
        tripData.setTripName("Booking");
        Context context = new Context(locale);
        context.setVariable("customerName", "Alex");
        context.setVariable("tripData", tripData);
        context.setVariable("destinationViews", viewsWithExtras());
        context.setVariable("bookingDate", "October 10, 2026");
        context.setVariable("tripId", "TRV-1");
        context.setVariable("depositPaid", false);
        return context;
    }

    @Test
    void itineraryListsEachExtraWithItsAmountAndPricingMode() {
        String html = engine().process("itinerary-confirmation", itineraryContext(Locale.ENGLISH));

        assertThat(html)
                .contains("Private show")
                .contains("250.00")
                .contains("per group")
                .contains("Photographer")
                .contains("60.00")
                .contains("per person");
    }

    @Test
    void germanItinerarySaysProGruppe() {
        String html = engine().process("itinerary-confirmation", itineraryContext(Locale.GERMAN));

        assertThat(html)
                .contains("Private show")
                .contains("pro Gruppe")
                .contains("pro Person");
    }

    @Test
    void bookingNotificationListsTheExtras() {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("customerName", "Alex");
        context.setVariable("userEmail", "alex@example.com");
        context.setVariable("tripId", "TRV-1");
        context.setVariable("totalAmount", new BigDecimal("402.00"));
        context.setVariable("destinationViews", viewsWithExtras());
        context.setVariable("depositPaid", false);

        String html = engine().process("booking-notification", context);

        assertThat(html)
                .contains("Private show")
                .contains("250.00")
                .contains("per group")
                .contains("Photographer")
                .contains("per person");
    }
}
