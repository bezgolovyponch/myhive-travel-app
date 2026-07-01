package com.myhive.backend.payment;

import com.myhive.backend.payment.StripeRefs.CheckoutSessionRef;
import com.myhive.backend.payment.StripeRefs.PaymentLinkRef;
import com.myhive.backend.payment.StripeRefs.StripeWebhookEvent;
import java.util.Map;

/**
 * Thin seam over the Stripe SDK so PaymentService is unit-testable without network/static
 * mocking. The only production implementation is StripeGatewayImpl (Task 11).
 */
public interface StripeGateway {

    CheckoutSessionRef createCheckoutSession(long amountCents, String currency, String description,
            Map<String, String> metadata, String successUrl, String cancelUrl, String idempotencyKey);

    /** Verifies the signature and returns a normalized event. Throws BadRequestException on bad signature. */
    StripeWebhookEvent constructEvent(String payload, String signatureHeader);

    /** Creates a reusable Stripe Payment Link for a one-off amount (admin balance/add-on collection). */
    PaymentLinkRef createPaymentLink(long amountCents, String currency, String description,
            Map<String, String> metadata);

    /** Deactivates a Payment Link so its URL can no longer be paid. */
    void deactivatePaymentLink(String paymentLinkId);
}
