package com.myhive.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void prodProfile_blankSecretKey_throwsIllegalArgument() {
        assertThatThrownBy(() -> new StripeProperties("", "whsec_prod", "eur", 30, "prod"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.stripe.secret-key");
    }

    @Test
    void prodProfile_blankWebhookSecret_throwsIllegalArgument() {
        assertThatThrownBy(() -> new StripeProperties("sk_prod", "", "eur", 30, "prod"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.stripe.webhook-secret");
    }

    @Test
    void devProfile_blankSecrets_doesNotThrow() {
        StripeProperties devProps = new StripeProperties("", "", "eur", 30, "dev");
        assertThat(devProps.getSecretKey()).isEmpty();
        assertThat(devProps.getWebhookSecret()).isEmpty();
    }
}
