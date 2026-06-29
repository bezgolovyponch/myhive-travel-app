package com.myhive.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripeProperties {

    private final String secretKey;
    private final String webhookSecret;
    private final String currency;
    private final int depositPct;

    public StripeProperties(
            @Value("${app.stripe.secret-key:}") String secretKey,
            @Value("${app.stripe.webhook-secret:}") String webhookSecret,
            @Value("${app.stripe.currency:eur}") String currency,
            @Value("${app.payment.deposit-pct:30}") int depositPct) {
        // L2: fail fast on a misconfigured deposit percentage. Outside [1,99] yields a deposit that
        // exceeds (or equals) the total and a negative/zero balance whose split no longer sums correctly.
        if (depositPct < 1 || depositPct > 99) {
            throw new IllegalArgumentException(
                    "app.payment.deposit-pct must be between 1 and 99, was " + depositPct);
        }
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.currency = currency;
        this.depositPct = depositPct;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public String getCurrency() {
        return currency;
    }

    public int getDepositPct() {
        return depositPct;
    }
}
