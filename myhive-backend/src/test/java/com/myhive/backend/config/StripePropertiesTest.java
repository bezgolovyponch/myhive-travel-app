package com.myhive.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = StripeProperties.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.stripe.secret-key=sk_test_abc",
        "app.stripe.webhook-secret=whsec_def",
        "app.stripe.currency=eur",
        "app.payment.deposit-pct=30"
})
class StripePropertiesTest {

    @Autowired
    private StripeProperties properties;

    @Test
    void bindsStripeAndPaymentConfig() {
        assertThat(properties.getSecretKey()).isEqualTo("sk_test_abc");
        assertThat(properties.getWebhookSecret()).isEqualTo("whsec_def");
        assertThat(properties.getCurrency()).isEqualTo("eur");
        assertThat(properties.getDepositPct()).isEqualTo(30);
    }
}
