package com.myhive.backend.service;

import com.myhive.backend.config.MetaCapiProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetaCapiServiceTest {

    private MetaCapiService disabled() {
        return new MetaCapiService(new MetaCapiProperties("", "", "", "https://graph.facebook.com/v21.0"), null, null);
    }

    private MetaCapiService enabled() {
        return new MetaCapiService(new MetaCapiProperties("1482052533162342", "token", "", "https://graph.facebook.com/v21.0"), null, null);
    }

    @Test
    void disabledWithoutCredentials() {
        assertThat(disabled().isEnabled()).isFalse();
        // must not throw
        disabled().sendEvent(MetaCapiService.MetaCapiEvent.of("Lead", "e-1"));
    }

    @Test
    void buildsPayloadWithHashedEmailAndDedupeId() {
        var event = MetaCapiService.MetaCapiEvent.of("Purchase", "evt-42")
                .value(new BigDecimal("133.50"), "EUR")
                .email("User@Example.com ")
                .fbp("fb.1.123.456")
                .fbc("fb.1.123.AbC");
        Map<String, Object> payload = enabled().buildPayload(event);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) ((java.util.List<?>) payload.get("data")).get(0);
        assertThat(data.get("event_name")).isEqualTo("Purchase");
        assertThat(data.get("event_id")).isEqualTo("evt-42");
        assertThat(data.get("action_source")).isEqualTo("website");
        @SuppressWarnings("unchecked")
        var userData = (Map<String, Object>) data.get("user_data");
        // sha256 of "user@example.com" (trimmed, lowercased)
        assertThat(userData.get("em")).isEqualTo(
                java.util.List.of("b4c9a289323b21a01c3e940f150eb9b8c542587f1abfd8f0e1cc1ffc5e475514"));
        assertThat(userData.get("fbp")).isEqualTo("fb.1.123.456");
        @SuppressWarnings("unchecked")
        var custom = (Map<String, Object>) data.get("custom_data");
        assertThat(custom.get("value")).isEqualTo(new BigDecimal("133.50"));
        assertThat(custom.get("currency")).isEqualTo("EUR");
    }

    @Test
    void fbcFromPrefersExplicitFbcOverFbclid() {
        assertThat(MetaCapiService.fbcFrom("fb.1.123.AbC", "AbC")).isEqualTo("fb.1.123.AbC");
    }

    @Test
    void fbcFromDerivesFromFbclidWhenFbcMissing() {
        String fbc = MetaCapiService.fbcFrom(null, "AbC123");
        assertThat(fbc).matches("fb\\.1\\.\\d+\\.AbC123");

        String fbcFromBlank = MetaCapiService.fbcFrom("  ", "AbC123");
        assertThat(fbcFromBlank).matches("fb\\.1\\.\\d+\\.AbC123");
    }

    @Test
    void fbcFromReturnsNullWhenBothMissing() {
        assertThat(MetaCapiService.fbcFrom(null, null)).isNull();
        assertThat(MetaCapiService.fbcFrom("", " ")).isNull();
    }
}
