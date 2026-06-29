package com.myhive.backend.exception;

/**
 * Thrown when the upstream payment gateway (Stripe) fails to create a session/link. Maps to HTTP 502
 * so infrastructure failures are not misreported as client 400s, and the raw upstream message is logged
 * server-side rather than returned to the caller (M3).
 */
public class PaymentGatewayException extends RuntimeException {
    public PaymentGatewayException(String message) {
        super(message);
    }
}
