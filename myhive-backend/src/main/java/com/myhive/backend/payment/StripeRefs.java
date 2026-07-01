package com.myhive.backend.payment;

public final class StripeRefs {

    private StripeRefs() {
    }

    public record CheckoutSessionRef(String id, String url) {
    }

    public record PaymentLinkRef(String id, String url) {
    }

    /**
     * Normalized view of the Stripe webhook events PaymentService cares about, so the
     * service never touches the Stripe SDK. Fields not relevant to a given event type are null.
     */
    public record StripeWebhookEvent(
            String id,
            String type,
            String shareId,
            String paymentLinkId,
            String sessionId,
            String paymentIntentId,
            String payerEmail,
            String paymentStatus,
            Long amountTotalCents,
            Long amountRefundedCents,
            boolean fullyRefunded) {
    }
}
