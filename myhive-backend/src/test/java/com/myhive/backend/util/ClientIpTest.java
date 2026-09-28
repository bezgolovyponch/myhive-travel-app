package com.myhive.backend.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The shared per-IP rule: Cloudflare first, then the closest proxy hop, then the socket address. */
class ClientIpTest {

    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void cloudflareHeader_winsAndIsNotSecondGuessed() {
        String expectedIp = "203.0.113.7";
        when(request.getHeader("CF-Connecting-IP")).thenReturn("  " + expectedIp + "  ");

        assertThat(ClientIp.resolve(request)).isEqualTo(expectedIp);
        // Cloudflare's own header is authoritative; a forwarded chain behind it must not be consulted.
        verify(request, never()).getHeader("X-Forwarded-For");
    }

    @Test
    void withoutCloudflare_theLastForwardedHopWins() {
        String expectedIp = "198.51.100.23";
        when(request.getHeader("CF-Connecting-IP")).thenReturn("   ");
        when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 10.0.0.1, " + expectedIp);

        assertThat(ClientIp.resolve(request)).isEqualTo(expectedIp);
    }

    @Test
    void withoutAnyProxyHeader_theSocketAddressIsUsed() {
        String expectedIp = "127.0.0.1";
        when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(expectedIp);

        assertThat(ClientIp.resolve(request)).isEqualTo(expectedIp);
    }
}
