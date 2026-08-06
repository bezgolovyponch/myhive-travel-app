package com.myhive.backend.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// IP resolution order (Cloudflare deployment):
// 1. CF-Connecting-IP — set by Cloudflare, cannot be forged by clients
// 2. Last entry of X-Forwarded-For — closest real proxy when CF-Connecting-IP is absent
// 3. request.getRemoteAddr() — direct connection (dev/testing)

@Component
public class RateLimitFilter implements Filter {

    private static final int MAX_REQUESTS_PER_MINUTE = 100;

    /** Stripe webhook payloads are a few KB (largest event objects stay well under this). Reject
     *  anything larger before the body is read/HMAC'd, so the un-rate-limited webhook can't be a
     *  cheap unauthenticated DoS (large-body flood). */
    private static final long MAX_WEBHOOK_BODY_BYTES = 512L * 1024L;
    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    /** Shared secret for server-to-server callers (the Next SSR service). Cold ISR fills render
     *  the whole catalog from one egress IP and would exhaust the per-IP bucket; a matching
     *  X-Internal-Token bypasses the counter. Blank (default) disables the exemption. */
    private final byte[] internalToken;
    private final ScheduledExecutorService scheduler;
    private final long cleanupDelayMillis;

    /** Spring instantiates the bean through this constructor. {@code internal.api.token} is blank
     *  by default (exemption disabled); the cleanup scheduler is the real single-thread daemon.
     *  {@code @Autowired} is required because the test-seam constructors below make this a
     *  multi-constructor class, so Spring cannot auto-select one on its own. */
    @Autowired
    public RateLimitFilter(@Value("${internal.api.token:}") String internalToken) {
        this(internalToken,
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "rate-limit-cleanup");
                    t.setDaemon(true);
                    return t;
                }),
                TimeUnit.MINUTES.toMillis(1));
    }

    /** Test seam: inject a controllable cleanup scheduler/delay. The internal-token exemption is
     *  disabled (blank token), matching the pre-existing rate-limit tests. */
    RateLimitFilter(ScheduledExecutorService scheduler, long cleanupDelayMillis) {
        this("", scheduler, cleanupDelayMillis);
    }

    private RateLimitFilter(String internalToken, ScheduledExecutorService scheduler,
            long cleanupDelayMillis) {
        this.internalToken = internalToken == null || internalToken.isBlank()
                ? null
                : internalToken.getBytes(StandardCharsets.UTF_8);
        this.scheduler = scheduler;
        this.cleanupDelayMillis = cleanupDelayMillis;
    }

    private boolean isInternalRequest(HttpServletRequest request) {
        if (internalToken == null) {
            return false;
        }
        String header = request.getHeader("X-Internal-Token");
        return header != null
                && MessageDigest.isEqual(internalToken, header.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Exempt the Stripe webhook from rate limiting. Deliveries come from a few Stripe source
        // IPs and are bursty (plus retries), so a shared bucket would 429 and drop source-of-truth
        // events. The endpoint is safe: authenticated by Stripe signature + idempotent processing.
        // The prod /api context-path is already stripped by the container, so the servlet path is
        // /payments/webhook in both dev and prod.
        if ("POST".equalsIgnoreCase(httpRequest.getMethod())
                && "/payments/webhook".equals(httpRequest.getServletPath())) {
            // Rate-limit-exempt, so guard against a large-body flood: reject an oversized declared
            // Content-Length before Spring buffers the body or Stripe's HMAC runs. A chunked request
            // with no length (-1) is left to Spring/Tomcat's own limits.
            long declaredLength = httpRequest.getContentLengthLong();
            if (declaredLength > MAX_WEBHOOK_BODY_BYTES) {
                httpResponse.setStatus(413);
                httpResponse.getWriter().write("Payload Too Large");
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        if (isInternalRequest(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(httpRequest);
        // Cleanup is scheduled inside the atomic computeIfAbsent so it runs exactly
        // once per bucket. Scheduling it afterwards behind a count == 1 check had a
        // race: two concurrent first requests could both increment before either
        // checked the count, nobody scheduled the removal, and the IP stayed
        // rate-limited until an application restart.
        AtomicInteger count = requestCounts.computeIfAbsent(clientIp, key -> {
            // Block body keeps the lambda void-compatible only, pinning the
            // schedule(Runnable, ...) overload (an expression body would return
            // remove()'s value and silently select schedule(Callable, ...)).
            scheduler.schedule(() -> {
                requestCounts.remove(key);
            }, cleanupDelayMillis, TimeUnit.MILLISECONDS);
            return new AtomicInteger(0);
        });

        if (count.incrementAndGet() > MAX_REQUESTS_PER_MINUTE) {
            httpResponse.setStatus(429);
            httpResponse.getWriter().write("Too Many Requests");
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }

    private String getClientIp(HttpServletRequest request) {
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isBlank()) {
            return cfConnectingIp.trim();
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] parts = xForwardedFor.split(",");
            return parts[parts.length - 1].trim();
        }

        return request.getRemoteAddr();
    }
}
