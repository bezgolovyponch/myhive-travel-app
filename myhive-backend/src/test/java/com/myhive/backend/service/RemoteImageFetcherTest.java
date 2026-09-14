package com.myhive.backend.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteImageFetcherTest {

    private static final long TEST_MAX_BYTES = 1024;
    private static final Duration TEST_TIMEOUT = Duration.ofMillis(700);

    private HttpServer server;
    private String baseUrl;
    private RemoteImageFetcher fetcher;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        fetcher = new RemoteImageFetcher(client, address -> true, TEST_MAX_BYTES, TEST_TIMEOUT);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void serve(String path, int status, String contentType, byte[] body) {
        server.createContext(path, exchange -> {
            if (contentType != null) {
                exchange.getResponseHeaders().add("Content-Type", contentType);
            }
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    private void redirect(String path, String location) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
    }

    @Test
    void fetch_imageResponse_returnsBytesAndContentType() {
        byte[] expectedBytes = {1, 2, 3, 4};
        serve("/photo.jpg", 200, "image/jpeg", expectedBytes);

        RemoteImageFetcher.FetchedImage fetched = fetcher.fetch(baseUrl + "/photo.jpg");

        assertThat(fetched.bytes()).isEqualTo(expectedBytes);
        assertThat(fetched.contentType()).isEqualTo("image/jpeg");
        assertThat(fetched.fileName()).isEqualTo("photo.jpg");
    }

    @Test
    void fetch_contentTypeWithCharsetParameter_isStillAccepted() {
        serve("/p", 200, "image/png; charset=binary", new byte[]{9});

        RemoteImageFetcher.FetchedImage fetched = fetcher.fetch(baseUrl + "/p");

        assertThat(fetched.contentType()).isEqualTo("image/png");
    }

    @Test
    void fetch_followsRedirectToImage() {
        byte[] expectedBytes = {7, 7};
        redirect("/old", baseUrl + "/new.png");
        serve("/new.png", 200, "image/png", expectedBytes);

        RemoteImageFetcher.FetchedImage fetched = fetcher.fetch(baseUrl + "/old");

        assertThat(fetched.bytes()).isEqualTo(expectedBytes);
    }

    @Test
    void fetch_redirectLoop_throwsAfterMaxRedirects() {
        redirect("/a", baseUrl + "/b");
        redirect("/b", baseUrl + "/a");

        assertThatThrownBy(() -> fetcher.fetch(baseUrl + "/a"))
                .isInstanceOf(RemoteImageFetcher.FetchException.class)
                .hasMessageContaining("redirect");
    }

    @Test
    void fetch_nonImageContentType_throws() {
        serve("/page", 200, "text/html", "<html/>".getBytes());

        assertThatThrownBy(() -> fetcher.fetch(baseUrl + "/page"))
                .isInstanceOf(RemoteImageFetcher.FetchException.class)
                .hasMessageContaining("text/html");
    }

    @Test
    void fetch_missingContentType_throws() {
        serve("/blob", 200, null, new byte[]{1});

        assertThatThrownBy(() -> fetcher.fetch(baseUrl + "/blob"))
                .isInstanceOf(RemoteImageFetcher.FetchException.class);
    }

    @Test
    void fetch_notFound_throwsWithStatus() {
        serve("/missing.jpg", 404, "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> fetcher.fetch(baseUrl + "/missing.jpg"))
                .isInstanceOf(RemoteImageFetcher.FetchException.class)
                .hasMessageContaining("404");
    }

    @Test
    void fetch_bodyLargerThanCap_throws() {
        serve("/huge.jpg", 200, "image/jpeg", new byte[(int) TEST_MAX_BYTES + 1]);

        assertThatThrownBy(() -> fetcher.fetch(baseUrl + "/huge.jpg"))
                .isInstanceOf(RemoteImageFetcher.FetchException.class)
                .hasMessageContaining("large");
    }

    @Test
    void fetch_bodyWithoutContentLengthOverCap_throws() {
        server.createContext("/chunked.jpg", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, 0); // chunked: no Content-Length to check up front
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(new byte[(int) TEST_MAX_BYTES + 1]);
            }
        });

        assertThatThrownBy(() -> fetcher.fetch(baseUrl + "/chunked.jpg"))
                .isInstanceOf(RemoteImageFetcher.FetchException.class)
                .hasMessageContaining("large");
    }

    @Test
    void fetch_serverStallsAfterHeaders_timesOutInsteadOfBlocking() {
        server.createContext("/slow.jpg", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, 64);
            try {
                Thread.sleep(TEST_TIMEOUT.toMillis() * 3);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });

        long started = System.nanoTime();
        assertThatThrownBy(() -> fetcher.fetch(baseUrl + "/slow.jpg"))
                .isInstanceOf(RemoteImageFetcher.FetchException.class)
                .hasMessageContaining("timed out");
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        assertThat(elapsedMillis).isLessThan(TEST_TIMEOUT.toMillis() * 3);
    }

    @Test
    void fetch_disallowedAddress_throwsBeforeConnecting() {
        RemoteImageFetcher strict = new RemoteImageFetcher(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                address -> false, TEST_MAX_BYTES, TEST_TIMEOUT);
        serve("/photo.jpg", 200, "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> strict.fetch(baseUrl + "/photo.jpg"))
                .isInstanceOf(RemoteImageFetcher.FetchException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void fetch_unresolvableHost_throws() {
        assertThatThrownBy(() -> fetcher.fetch("http://nonexistent.invalid/x.jpg"))
                .isInstanceOf(RemoteImageFetcher.FetchException.class);
    }

    @Test
    void fetch_invalidUrl_throws() {
        assertThatThrownBy(() -> fetcher.fetch("ftp://example.com/x.jpg"))
                .isInstanceOf(RemoteImageFetcher.FetchException.class);
    }

    @Test
    void validate_acceptsHttpAndHttps() {
        assertThat(RemoteImageFetcher.validate("https://example.com/a.jpg")).isEmpty();
        assertThat(RemoteImageFetcher.validate("http://example.com/a.jpg")).isEmpty();
    }

    @Test
    void validate_rejectsOtherSchemesAndMalformedUrls() {
        assertThat(RemoteImageFetcher.validate("ftp://example.com/a.jpg")).isPresent();
        assertThat(RemoteImageFetcher.validate("javascript:alert(1)")).isPresent();
        assertThat(RemoteImageFetcher.validate("example.com/a.jpg")).isPresent();
        assertThat(RemoteImageFetcher.validate("https://")).isPresent();
        assertThat(RemoteImageFetcher.validate("http://exa mple.com/a.jpg")).isPresent();
    }

    @Test
    void isPublicAddress_rejectsLoopbackPrivateAndLinkLocal() throws Exception {
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("127.0.0.1"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("10.0.0.5"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("192.168.1.1"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("172.16.0.1"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("169.254.169.254"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("0.0.0.0"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("100.64.0.1"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("0.1.2.3"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("192.0.0.8"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("198.19.255.1"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("240.0.0.1"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("64:ff9b::a00:1"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("64:ff9b::7f00:1"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("::1"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("fd12::1"))).isFalse();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("fe80::1"))).isFalse();
    }

    @Test
    void isPublicAddress_acceptsPublicAddresses() throws Exception {
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("93.184.216.34"))).isTrue();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("2606:2800:220:1:248:1893:25c8:1946"))).isTrue();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("64:ff9b::5db8:d822"))).isTrue();
        assertThat(RemoteImageFetcher.isPublicAddress(InetAddress.getByName("198.20.0.1"))).isTrue();
    }
}
