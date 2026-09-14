package com.myhive.backend.service;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Downloads an image from a public http(s) URL so it can be re-hosted in our own bucket.
 *
 * <p>Runs server-side on behalf of an admin, so it is an SSRF surface: only http/https, every
 * hop (including redirects) must resolve to a public address, bodies are capped, the whole
 * download of one hop is bounded by {@link #TIMEOUT} (headers <em>and</em> body — a host that
 * trickles bytes cannot pin the request thread), and the response must declare an
 * {@code image/*} content type. Redirects are followed manually so each hop passes the same
 * checks.
 *
 * <p>Residual risk: the address check resolves the host once and the client resolves it again
 * to connect, so a DNS-rebinding host could in theory answer differently in between. The JVM's
 * positive DNS cache (30 s by default) closes that window in practice; the surface is
 * admin-only.
 */
@Component
public class RemoteImageFetcher {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    static final int MAX_REDIRECTS = 3;

    /** The downloaded body plus what we know about it. {@code fileName} is the URL's last path segment. */
    public record FetchedImage(byte[] bytes, String contentType, String fileName) {
    }

    /** Anything that stops a URL turning into an image: bad URL, blocked host, network, size, type. */
    public static class FetchException extends RuntimeException {
        public FetchException(String message) {
            super(message);
        }
    }

    private final HttpClient client;
    private final Predicate<InetAddress> addressAllowed;
    private final long maxBytes;
    private final Duration timeout;

    public RemoteImageFetcher() {
        this(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(TIMEOUT)
                        .build(),
                RemoteImageFetcher::isPublicAddress,
                MAX_BYTES,
                TIMEOUT);
    }

    /** Visible for testing: inject the client, the address policy, the body cap and the per-hop timeout. */
    RemoteImageFetcher(HttpClient client, Predicate<InetAddress> addressAllowed, long maxBytes, Duration timeout) {
        this.client = client;
        this.addressAllowed = addressAllowed;
        this.maxBytes = maxBytes;
        this.timeout = timeout;
    }

    /**
     * Cheap, offline check that a string is an http(s) URL with a host. Returns the problem,
     * or empty when the URL is acceptable. Does not resolve DNS — that happens at fetch time.
     */
    public static Optional<String> validate(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return Optional.of("not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Optional.of("must start with http:// or https://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return Optional.of("has no host");
        }
        return Optional.empty();
    }

    public FetchedImage fetch(String url) {
        URI current = toUri(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            assertAllowed(current);
            HttpResponse<byte[]> response = download(current);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                current = redirectTarget(current, response);
                continue;
            }
            if (status != 200) {
                throw new FetchException("HTTP " + status);
            }
            return new FetchedImage(response.body(), imageContentType(response), fileNameOf(current));
        }
        throw new FetchException("too many redirects (max " + MAX_REDIRECTS + ")");
    }

    private URI toUri(String url) {
        Optional<String> problem = validate(url);
        if (problem.isPresent()) {
            throw new FetchException(problem.get());
        }
        return URI.create(url);
    }

    private void assertAllowed(URI uri) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new FetchException("host could not be resolved: " + uri.getHost());
        }
        for (InetAddress address : addresses) {
            if (!addressAllowed.test(address)) {
                throw new FetchException("host is not allowed: " + uri.getHost());
            }
        }
    }

    /**
     * One request whose <em>entire</em> lifetime (connect, headers, body) is bounded by
     * {@link #timeout}. Non-200 and non-image responses are not buffered at all; an image body
     * is collected up to {@link #maxBytes} and the download is cancelled the moment it exceeds
     * the cap.
     */
    private HttpResponse<byte[]> download(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "image/*")
                .GET()
                .build();
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request, this::bodyHandler);
        try {
            return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new FetchException("download timed out after " + timeout.toSeconds() + "s");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof FetchException fetchException) {
                throw fetchException;
            }
            throw new FetchException("download failed: " + cause.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.cancel(true);
            throw new FetchException("download interrupted");
        }
    }

    private HttpResponse.BodySubscriber<byte[]> bodyHandler(HttpResponse.ResponseInfo info) {
        boolean isImage = info.statusCode() == 200 && mediaType(info.headers()).startsWith("image/");
        if (!isImage) {
            // Redirects, errors and HTML pages: the status/headers are all fetch() needs.
            return HttpResponse.BodySubscribers.replacing(new byte[0]);
        }
        long declaredLength = info.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (declaredLength > maxBytes) {
            throw tooLarge();
        }
        return new CappedByteArraySubscriber(maxBytes);
    }

    private URI redirectTarget(URI from, HttpResponse<byte[]> response) {
        String location = response.headers().firstValue("Location")
                .orElseThrow(() -> new FetchException("redirect without Location header"));
        URI target;
        try {
            target = from.resolve(location);
        } catch (IllegalArgumentException e) {
            throw new FetchException("redirect to an invalid URL");
        }
        Optional<String> problem = validate(target.toString());
        if (problem.isPresent()) {
            throw new FetchException("redirect target " + problem.get());
        }
        return target;
    }

    private String imageContentType(HttpResponse<byte[]> response) {
        String mediaType = mediaType(response.headers());
        if (!mediaType.startsWith("image/")) {
            throw new FetchException("not an image (Content-Type: "
                    + (mediaType.isEmpty() ? "missing" : mediaType) + ")");
        }
        return mediaType;
    }

    private static String mediaType(HttpHeaders headers) {
        String declared = headers.firstValue("Content-Type").orElse("");
        return declared.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private FetchException tooLarge() {
        return new FetchException("image is too large (max " + maxBytes + " bytes)");
    }

    private static String fileNameOf(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash < 0 ? path : path.substring(slash + 1);
        return name.isEmpty() ? "image" : name;
    }

    /**
     * Rejects everything an internal service could hide behind: loopback, RFC 1918 and
     * unique-local ranges, link-local (cloud metadata endpoints live there), CGNAT, multicast,
     * the unspecified/"this network" block, IETF protocol assignments, benchmarking and
     * reserved IPv4 ranges, and NAT64-mapped IPv6.
     */
    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] raw = address.getAddress();
        if (raw.length == 4) {
            return isPublicIpv4(raw[0] & 0xFF, raw[1] & 0xFF);
        }
        // fc00::/7 unique-local IPv6 is not flagged by isSiteLocalAddress (that covers only fec0::/10).
        if ((raw[0] & 0xFE) == 0xFC) {
            return false;
        }
        // 64:ff9b::/96 NAT64: an IPv4 address in disguise — judge the embedded IPv4.
        boolean nat64 = raw[0] == 0x00 && raw[1] == 0x64 && (raw[2] & 0xFF) == 0xFF && (raw[3] & 0xFF) == 0x9B;
        if (nat64) {
            for (int i = 4; i < 12; i++) {
                if (raw[i] != 0) {
                    return true;
                }
            }
            try {
                byte[] embedded = {raw[12], raw[13], raw[14], raw[15]};
                return isPublicAddress(InetAddress.getByAddress(embedded));
            } catch (UnknownHostException e) {
                return false; // cannot happen for a 4-byte literal; fail closed anyway
            }
        }
        return true;
    }

    private static boolean isPublicIpv4(int first, int second) {
        if (first == 0 || first >= 240) {
            return false; // 0.0.0.0/8 "this network", 240.0.0.0/4 reserved + broadcast
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return false; // 100.64.0.0/10 carrier-grade NAT
        }
        if (first == 192 && second == 0) {
            return false; // 192.0.0.0/24 IETF protocol assignments (192.0.2.0/24 TEST-NET is harmless)
        }
        return !(first == 198 && (second == 18 || second == 19)); // 198.18.0.0/15 benchmarking
    }

    /**
     * Collects the body into memory and cancels the subscription as soon as the cap is
     * exceeded, so a chunked response with no Content-Length cannot exhaust the heap.
     */
    private final class CappedByteArraySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final long cap;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private CappedByteArraySubscriber(long cap) {
            this.cap = cap;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer item : items) {
                if (buffer.size() + item.remaining() > cap) {
                    subscription.cancel();
                    result.completeExceptionally(tooLarge());
                    return;
                }
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                buffer.write(chunk, 0, chunk.length);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(buffer.toByteArray());
        }
    }
}
