package com.myhive.backend.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the frontend base URL used for browser redirects (e.g. Stripe Checkout success/cancel
 * URLs).
 *
 * <p>With destination subdomains (prague.trivlu.com, ...) the buyer must be sent back to the exact
 * origin they paid from — their trip state lives in that origin's localStorage. The Origin request
 * header is client-controlled, so it is only honored when it matches the CORS allow-list; anything
 * else falls back to the configured apex URL (open-redirect guard).
 */
@Component
public class FrontendUrlResolver {

    private final List<Pattern> allowedOriginPatterns;
    private final String defaultFrontendUrl;

    public FrontendUrlResolver(
            @Value("${CORS_ALLOWED_ORIGINS:" + WebConfig.DEFAULT_ALLOWED_ORIGINS + "}")
            String[] allowedOrigins,
            @Value("${app.frontend.url:https://trivlu.com}") String defaultFrontendUrl) {
        this.allowedOriginPatterns = Arrays.stream(allowedOrigins)
                .map(FrontendUrlResolver::toPattern)
                .collect(Collectors.toList());
        this.defaultFrontendUrl = defaultFrontendUrl;
    }

    /**
     * Returns the caller's origin when it is on the allow-list, otherwise the configured default
     * frontend URL. Accepts the raw {@code Origin} header value (may be null).
     */
    public String resolve(String originHeader) {
        if (originHeader == null || originHeader.isBlank()) {
            return defaultFrontendUrl;
        }
        String origin = originHeader.trim().toLowerCase(Locale.ROOT);
        if (origin.endsWith("/")) {
            origin = origin.substring(0, origin.length() - 1);
        }
        for (Pattern pattern : allowedOriginPatterns) {
            if (pattern.matcher(origin).matches()) {
                return origin;
            }
        }
        return defaultFrontendUrl;
    }

    /**
     * Compiles an allow-list entry to an anchored regex. {@code *} matches exactly one hostname
     * label (no dots), so {@code https://*.trivlu.com} matches {@code https://prague.trivlu.com}
     * but not {@code https://prague.trivlu.com.evil.com} or {@code https://a.b.trivlu.com}.
     *
     * <p>Intentionally narrower than Spring's CORS pattern matching (which expands {@code *} to
     * {@code .*}): a multi-label host that CORS would accept falls back to the apex here, which
     * is fail-safe for a redirect target.
     */
    private static Pattern toPattern(String allowedOrigin) {
        String[] literals = allowedOrigin.trim().toLowerCase(Locale.ROOT).split("\\*", -1);
        String regex = Arrays.stream(literals)
                .map(Pattern::quote)
                .collect(Collectors.joining("[a-z0-9-]+"));
        return Pattern.compile(regex);
    }
}
