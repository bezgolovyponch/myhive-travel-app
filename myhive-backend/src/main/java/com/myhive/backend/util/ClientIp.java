package com.myhive.backend.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The one answer to "who is calling", shared by every per-IP counter in the application, so a
 * request can never be throttled as one caller and quota'd as another.
 *
 * <p>Resolution order for the Cloudflare deployment:
 * <ol>
 *   <li>{@code CF-Connecting-IP} — set by Cloudflare, cannot be forged by a client.</li>
 *   <li>The <em>last</em> {@code X-Forwarded-For} entry — the closest real proxy when Cloudflare is
 *       not in front. Deliberately not the first: everything ahead of the closest hop is whatever
 *       the client chose to send, so counting on it would let anyone mint a fresh bucket per
 *       request.</li>
 *   <li>{@code getRemoteAddr()} — a direct connection (dev and tests).</li>
 * </ol>
 */
public final class ClientIp {

    private static final String CLOUDFLARE_HEADER = "CF-Connecting-IP";
    private static final String FORWARDED_HEADER = "X-Forwarded-For";

    private ClientIp() {}

    public static String resolve(HttpServletRequest request) {
        String cloudflareIp = request.getHeader(CLOUDFLARE_HEADER);
        if (cloudflareIp != null && !cloudflareIp.isBlank()) {
            return cloudflareIp.trim();
        }

        String forwarded = request.getHeader(FORWARDED_HEADER);
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }

        return request.getRemoteAddr();
    }
}
