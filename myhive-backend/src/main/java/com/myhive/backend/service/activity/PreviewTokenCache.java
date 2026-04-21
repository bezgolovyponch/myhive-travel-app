package com.myhive.backend.service.activity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, one-shot token cache for preview→apply CSV imports.
 * Tokens are random UUIDs with a 10-minute TTL. Each call to
 * {@link #consume(UUID)} removes the entry.
 *
 * Package-private by design — only ActivityCsvImporter holds and
 * uses an instance (composed in its constructor). Not a Spring bean.
 */
final class PreviewTokenCache {

    static final Duration TTL = Duration.ofMinutes(10);

    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    record Entry(List<ValidatedRow> rows, Instant expiresAt) {
    }

    /** Stores rows under a fresh token, returns the token as a string. */
    String store(List<ValidatedRow> rows) {
        UUID token = UUID.randomUUID();
        cache.put(token, new Entry(rows, Instant.now().plus(TTL)));
        return token.toString();
    }

    /** Removes and returns the entry, or empty if missing. Caller checks expiry. */
    Optional<Entry> consume(UUID token) {
        return Optional.ofNullable(cache.remove(token));
    }

    /** Drops every entry whose TTL has elapsed. O(n) sweep — call opportunistically. */
    void evictExpired() {
        cache.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(Instant.now()));
    }

    /** Visible for testing: force a token's expiry into the past. */
    void expireTokenForTest(UUID token) {
        Entry e = cache.get(token);
        if (e != null) {
            cache.put(token, new Entry(e.rows(), Instant.now().minusSeconds(1)));
        }
    }

    /** Visible for testing: drop everything. */
    void clearForTest() {
        cache.clear();
    }
}
