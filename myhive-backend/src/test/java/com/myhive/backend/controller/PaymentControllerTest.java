package com.myhive.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.DepositSessionResponse;
import com.myhive.backend.service.PaymentService;
import com.myhive.backend.service.TurnstileService;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, PaymentControllerTest.MockConfig.class})
class PaymentControllerTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        public PaymentService paymentService() {
            return mock(PaymentService.class);
        }

        @Bean
        @Primary
        public TurnstileService turnstileService() {
            return mock(TurnstileService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private TurnstileService turnstileService;

    @BeforeEach
    void setUp() {
        reset(paymentService, turnstileService);
    }

    @Test
    void depositSession_returns201AndUrl_withoutJwt() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String expectedOrigin = "https://prague.trivlu.com";
        when(paymentService.createDepositBookingAndSession(any(), any(), any(), any()))
                .thenReturn(new DepositSessionResponse(bookingId, "https://checkout.stripe.com/cs_test"));

        mockMvc.perform(post("/payments/deposit-session")
                        .header("X-Vote-Share-Token", UUID.randomUUID().toString())
                        .header("X-Manager-Token", UUID.randomUUID().toString())
                        .header("Origin", expectedOrigin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tripName": "Booking",
                                  "userEmail": "init@test.com",
                                  "customerName": "Init",
                                  "numberOfTravelers": 4,
                                  "destinations": [
                                    {"destinationName": "Prague", "activities": [
                                      {"activityName": "Beer Tour", "price": 25.0}
                                    ]}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.com/cs_test"));
        // Multidomain: the vote deposit must forward the buyer's Origin just like the trip deposit.
        verify(paymentService).createDepositBookingAndSession(any(), any(), any(), eq(expectedOrigin));
    }

    @Test
    void depositSession_missingManagerTokenHeader_returns400() throws Exception {
        // M5/F-HEADER: a missing auth header is a client error (400), not a generic 500.
        mockMvc.perform(post("/payments/deposit-session")
                        .header("X-Vote-Share-Token", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tripName": "Booking",
                                  "userEmail": "init@test.com",
                                  "customerName": "Init",
                                  "numberOfTravelers": 4,
                                  "destinations": [
                                    {"destinationName": "Prague", "activities": [
                                      {"activityName": "Beer Tour", "price": 25.0}
                                    ]}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tripDepositSession_returns201AndUrl_whenTurnstileValid() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(turnstileService.verifyToken(any())).thenReturn(true);
        when(paymentService.createTripDepositSession(any(), any()))
                .thenReturn(new DepositSessionResponse(bookingId, "https://checkout.stripe.com/cs_direct"));

        mockMvc.perform(post("/payments/trip-deposit-session")
                        .header("X-Turnstile-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tripName": "Booking",
                                  "userEmail": "buyer@test.com",
                                  "customerName": "Buyer",
                                  "numberOfTravelers": 2,
                                  "destinations": [
                                    {"destinationName": "Prague", "activities": [
                                      {"activityName": "Beer Tour", "price": 25.0}
                                    ]}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.com/cs_direct"));
    }

    @Test
    void tripDepositSession_returns400_whenTurnstileInvalid() throws Exception {
        // A bad/absent captcha must be rejected before any booking or Stripe session is created.
        when(turnstileService.verifyToken(any())).thenReturn(false);

        mockMvc.perform(post("/payments/trip-deposit-session")
                        .header("X-Turnstile-Token", "tok-bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tripName": "Booking",
                                  "userEmail": "buyer@test.com",
                                  "customerName": "Buyer",
                                  "numberOfTravelers": 2,
                                  "destinations": [
                                    {"destinationName": "Prague", "activities": [
                                      {"activityName": "Beer Tour", "price": 25.0}
                                    ]}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(paymentService, org.mockito.Mockito.never()).createTripDepositSession(any(), any());
    }

    @Test
    void tripDepositSession_forwardsOriginHeaderToService() throws Exception {
        // Multidomain: the buyer's Origin must reach the service so Stripe return URLs point back
        // at the subdomain the payment started on.
        String expectedOrigin = "https://prague.trivlu.com";
        when(turnstileService.verifyToken(any())).thenReturn(true);
        when(paymentService.createTripDepositSession(any(), any()))
                .thenReturn(new DepositSessionResponse(UUID.randomUUID(), "https://checkout.stripe.com/cs_sub"));

        mockMvc.perform(post("/payments/trip-deposit-session")
                        .header("X-Turnstile-Token", "tok-ok")
                        .header("Origin", expectedOrigin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tripName": "Booking",
                                  "userEmail": "buyer@test.com",
                                  "customerName": "Buyer",
                                  "numberOfTravelers": 2,
                                  "destinations": [
                                    {"destinationName": "Prague", "activities": [
                                      {"activityName": "Beer Tour", "price": 25.0}
                                    ]}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());

        verify(paymentService).createTripDepositSession(any(), eq(expectedOrigin));
    }

    @Test
    void tripDepositSession_returns400_whenActivitiesListExceedsMax() throws Exception {
        // DoS guard: a single destination with 101 activities (> max 100) must be rejected
        // by Bean Validation before reaching the service layer.
        when(turnstileService.verifyToken(any())).thenReturn(true);

        String activityEntry = "{\"activityName\": \"Tour\", \"price\": 10.0}";
        String activities = String.join(", ", Collections.nCopies(101, activityEntry));
        String body = """
                {
                  "tripName": "Big Trip",
                  "userEmail": "buyer@test.com",
                  "customerName": "Buyer",
                  "numberOfTravelers": 2,
                  "destinations": [
                    {"destinationName": "Prague", "activities": [%s]}
                  ]
                }
                """.formatted(activities);

        mockMvc.perform(post("/payments/trip-deposit-session")
                        .header("X-Turnstile-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(paymentService, org.mockito.Mockito.never()).createTripDepositSession(any(), any());
    }

    @Test
    void webhook_returns200_whenServiceAccepts() throws Exception {
        mockMvc.perform(post("/payments/webhook")
                        .header("Stripe-Signature", "t=1,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void webhook_returns400_onBadSignature() throws Exception {
        org.mockito.Mockito.doThrow(new com.myhive.backend.exception.BadRequestException("bad signature"))
                .when(paymentService).handleStripeEvent(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

        mockMvc.perform(post("/payments/webhook")
                        .header("Stripe-Signature", "bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
