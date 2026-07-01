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
            @Value("${app.payment.deposit-pct:30}") int depositPct,
            @Value("${spring.profiles.active:}") String activeProfiles) {
        // L2: fail fast on a misconfigured deposit percentage. Outside [1,99] a deposit would meet or
        // exceed the trip total (and leave no balance), which is never intended.
        if (depositPct < 1 || depositPct > 99) {
            throw new IllegalArgumentException(
                    "app.payment.deposit-pct must be between 1 and 99, was " + depositPct);
        }
        // SEC-7: a blank secret key or webhook secret silently breaks all Stripe integration in prod.
        // Fail fast at startup rather than discovering the misconfiguration at reconciliation time.
        if (activeProfiles.contains("prod")) {
            if (secretKey == null || secretKey.isBlank()) {
                throw new IllegalArgumentException(
                        "app.stripe.secret-key must not be blank in the prod profile");
            }
            if (webhookSecret == null || webhookSecret.isBlank()) {
                throw new IllegalArgumentException(
                        "app.stripe.webhook-secret must not be blank in the prod profile");
            }
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
