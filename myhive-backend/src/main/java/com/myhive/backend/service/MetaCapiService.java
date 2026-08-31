package com.myhive.backend.service;

import com.myhive.backend.config.MetaCapiProperties;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Server-side Meta Conversions API. Fire-and-forget like {@link AsyncMailSender}: a Meta outage
 * must never fail a booking/vote/payment request.
 *
 * <p>{@code sendEvent} calls the package-private {@code dispatch} from within this same class, so
 * a {@code @Async}-proxied method would not actually go async (Spring self-invocation bypasses
 * the proxy). Instead the HTTP call is submitted directly to an injected {@link Executor}: request
 * context (client IP / user agent) is captured synchronously on the calling thread, then the
 * blocking POST runs on the {@code metaCapiTaskExecutor} pool.
 */
@Service
@Slf4j
public class MetaCapiService {

    private final MetaCapiProperties properties;
    private final RestClient restClient;
    private final Executor executor;

    public MetaCapiService(
            MetaCapiProperties properties,
            RestClient.@Nullable Builder builder,
            @Qualifier("metaCapiTaskExecutor") @Nullable Executor executor) {
        this.properties = properties;
        this.restClient = (builder != null ? builder : RestClient.builder())
                .baseUrl(properties.getApiUrl())
                .build();
        this.executor = executor;
    }

    public boolean isEnabled() {
        return properties.isConfigured();
    }

    /** Capture request context (ip/ua) synchronously, then dispatch off-thread. */
    public void sendEvent(MetaCapiEvent event) {
        if (!isEnabled()) {
            return;
        }
        enrichFromCurrentRequest(event);
        Map<String, Object> payload = buildPayload(event);
        String eventName = event.eventName;
        if (executor != null) {
            executor.execute(() -> dispatch(payload, eventName));
        } else {
            dispatch(payload, eventName);
        }
    }

    void dispatch(Map<String, Object> payload, String eventName) {
        try {
            restClient.post()
                    .uri("/{pixelId}/events?access_token={token}",
                            properties.getPixelId(), properties.getAccessToken())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Meta CAPI event sent: {}", eventName);
        } catch (Exception e) {
            log.error("Meta CAPI send failed ({}): {}", eventName, e.getMessage());
        }
    }

    Map<String, Object> buildPayload(MetaCapiEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("event_name", event.eventName);
        data.put("event_time", Instant.now().getEpochSecond());
        data.put("event_id", event.eventId);
        data.put("action_source", "website");
        if (event.sourceUrl != null) data.put("event_source_url", event.sourceUrl);

        Map<String, Object> userData = new HashMap<>();
        if (event.email != null && !event.email.isBlank()) {
            userData.put("em", List.of(sha256(event.email.trim().toLowerCase())));
        }
        if (event.fbp != null) userData.put("fbp", event.fbp);
        if (event.fbc != null) userData.put("fbc", event.fbc);
        if (event.clientIp != null) userData.put("client_ip_address", event.clientIp);
        if (event.userAgent != null) userData.put("client_user_agent", event.userAgent);
        data.put("user_data", userData);

        if (event.value != null) {
            Map<String, Object> custom = new HashMap<>();
            custom.put("value", event.value);
            custom.put("currency", event.currency);
            data.put("custom_data", custom);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("data", new ArrayList<>(List.of(data)));
        if (properties.getTestEventCode() != null && !properties.getTestEventCode().isBlank()) {
            payload.put("test_event_code", properties.getTestEventCode());
        }
        return payload;
    }

    private void enrichFromCurrentRequest(MetaCapiEvent event) {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                var request = attrs.getRequest();
                if (event.clientIp == null) {
                    String forwarded = request.getHeader("X-Forwarded-For");
                    event.clientIp = forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
                }
                if (event.userAgent == null) {
                    event.userAgent = request.getHeader("User-Agent");
                }
            }
        } catch (Exception ignored) {
            // webhook/async threads have no request context — fine.
        }
    }

    /**
     * Resolves the {@code fbc} click-id cookie value for the user_data payload.
     *
     * <p>An explicit {@code fbc} (already in Meta's {@code fb.1.<creation-time-ms>.<fbclid>}
     * format, typically read from the {@code _fbc} cookie) always wins. When only the raw
     * {@code fbclid} query parameter is available — e.g. the {@code _fbc} cookie hasn't been set
     * yet by the pixel — derive the same format so ad click attribution still works.
     */
    public static String fbcFrom(String fbc, String fbclid) {
        if (fbc != null && !fbc.isBlank()) return fbc;
        if (fbclid == null || fbclid.isBlank()) return null;
        return "fb.1." + System.currentTimeMillis() + "." + fbclid;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest(s.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static class MetaCapiEvent {
        final String eventName;
        final String eventId;
        BigDecimal value;
        String currency;
        String email;
        String fbp;
        String fbc;
        String clientIp;
        String userAgent;
        String sourceUrl;

        private MetaCapiEvent(String eventName, String eventId) {
            this.eventName = eventName;
            this.eventId = eventId;
        }

        public static MetaCapiEvent of(String eventName, String eventId) {
            return new MetaCapiEvent(eventName, eventId);
        }

        public MetaCapiEvent value(BigDecimal value, String currency) {
            this.value = value;
            this.currency = currency;
            return this;
        }

        public MetaCapiEvent email(String email) { this.email = email; return this; }
        public MetaCapiEvent fbp(String fbp) { this.fbp = fbp; return this; }
        public MetaCapiEvent fbc(String fbc) { this.fbc = fbc; return this; }
        public MetaCapiEvent clientIp(String ip) { this.clientIp = ip; return this; }
        public MetaCapiEvent userAgent(String ua) { this.userAgent = ua; return this; }
        public MetaCapiEvent sourceUrl(String url) { this.sourceUrl = url; return this; }
    }
}
