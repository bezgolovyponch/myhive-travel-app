package com.myhive.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * C1 defense-in-depth: the bean-validation bounds on amounts/discounts/travelers must reject
 * tampering values, and {@code @Valid} must cascade into the nested destinations/activities lists.
 */
class TripExportRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private TripExportRequest validBase() {
        TripExportRequest req = new TripExportRequest();
        req.setTripName("Trip");
        req.setUserEmail("a@b.com");
        req.setCustomerName("A");
        req.setNumberOfTravelers(2);
        TripExportRequest.DestinationExport de = new TripExportRequest.DestinationExport();
        de.setDestinationName("Bali");
        TripExportRequest.ActivityExport ae = new TripExportRequest.ActivityExport();
        ae.setPrice(50.0);
        ae.setPackageDiscountPct(new BigDecimal("10.00"));
        de.setActivities(List.of(ae));
        req.setDestinations(List.of(de));
        return req;
    }

    @Test
    void validRequest_passesValidation() {
        assertThat(validator.validate(validBase())).isEmpty();
    }

    @Test
    void packageDiscountOver100_failsValidation_viaCascade() {
        TripExportRequest req = validBase();
        req.getDestinations().getFirst().getActivities().getFirst()
                .setPackageDiscountPct(new BigDecimal("200.00"));
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void negativeActivityPrice_failsValidation_viaCascade() {
        TripExportRequest req = validBase();
        req.getDestinations().getFirst().getActivities().getFirst().setPrice(-5.0);
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void numberOfTravelersOver100_failsValidation() {
        TripExportRequest req = validBase();
        req.setNumberOfTravelers(101);
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void nullActivitiesList_failsValidation() {
        // F2: a null activities list must be rejected (400) rather than NPE-ing the booking builder (500).
        TripExportRequest req = validBase();
        req.getDestinations().getFirst().setActivities(null);
        assertThat(validator.validate(req)).isNotEmpty();
    }
}
