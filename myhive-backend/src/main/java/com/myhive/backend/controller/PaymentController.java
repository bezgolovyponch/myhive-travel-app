package com.myhive.backend.controller;

import com.myhive.backend.dto.ConsultationLeadResponse;
import com.myhive.backend.dto.DepositSessionResponse;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.service.PaymentService;
import com.myhive.backend.service.TurnstileService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final TurnstileService turnstileService;

    @PostMapping("/deposit-session")
    public ResponseEntity<DepositSessionResponse> createDepositSession(
            @RequestHeader("X-Vote-Share-Token") UUID voteShareToken,
            @RequestHeader("X-Manager-Token") UUID managerToken,
            @Valid @RequestBody TripExportRequest request) {
        DepositSessionResponse response = paymentService.createDepositBookingAndSession(voteShareToken, managerToken, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Direct 30% deposit for a self-curated Trip Builder trip (no vote). Public but Turnstile-gated. */
    @PostMapping("/trip-deposit-session")
    public ResponseEntity<DepositSessionResponse> createTripDepositSession(
            @RequestHeader("X-Turnstile-Token") String turnstileToken,
            @Valid @RequestBody TripExportRequest request) {
        if (!turnstileService.verifyToken(turnstileToken)) {
            throw new BadRequestException("Captcha verification failed");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createTripDepositSession(request));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        paymentService.handleStripeEvent(payload, signature);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/consultation-lead")
    public ResponseEntity<ConsultationLeadResponse> createConsultationLead(
            @RequestHeader("X-Vote-Share-Token") UUID voteShareToken,
            @RequestHeader("X-Manager-Token") UUID managerToken,
            @Valid @RequestBody TripExportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createConsultationLead(voteShareToken, managerToken, request));
    }
}
