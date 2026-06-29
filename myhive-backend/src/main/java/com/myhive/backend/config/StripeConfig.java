package com.myhive.backend.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class StripeConfig {

    private final StripeProperties stripeProperties;

    @PostConstruct
    public void init() {
        String key = stripeProperties.getSecretKey();
        if (key != null && !key.isBlank()) {
            Stripe.apiKey = key;
            log.info("Stripe SDK initialized (live key = {})", key.startsWith("sk_live"));
        } else {
            // No key in dev/test: gateway calls will not be made (deposit/link endpoints
            // are only exercised in tests via a mocked StripeGateway).
            log.warn("Stripe secret key not configured; live Stripe calls are disabled");
        }
    }
}
