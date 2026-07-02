package com.myhive.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    @Test
    void doFilter_underLimit_passesThrough() throws Exception {
        when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_overLimit_returns429() throws Exception {
        when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        // Send 101 requests — first 100 should pass, 101st should be blocked
        for (int i = 0; i < 101; i++) {
            filter.doFilter(request, response, chain);
        }

        verify(chain, times(100)).doFilter(request, response);
        verify(response).setStatus(429);
    }

    @Test
    void doFilter_differentIPs_trackedSeparately() throws Exception {
        when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        // Fill up IP A to 100
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        for (int i = 0; i < 100; i++) {
            filter.doFilter(request, response, chain);
        }

        // IP B should still pass
        when(request.getRemoteAddr()).thenReturn("10.0.0.3");
        filter.doFilter(request, response, chain);

        // 100 from A + 1 from B = 101
        verify(chain, times(101)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void getClientIp_prefersCfConnectingIpOverXForwardedFor() throws Exception {
        String expectedIp = "203.0.113.50";
        when(request.getHeader("CF-Connecting-IP")).thenReturn(expectedIp);
        // X-Forwarded-For is NOT stubbed — the method must not reach it
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        for (int i = 0; i < 101; i++) {
            filter.doFilter(request, response, chain);
        }

        verify(chain, times(100)).doFilter(request, response);
        verify(response).setStatus(429);
        verify(request, never()).getHeader("X-Forwarded-For");
        verify(request, never()).getRemoteAddr();
    }

    @Test
    void getClientIp_usesLastXForwardedForEntryWhenCfHeaderAbsent() throws Exception {
        String expectedIp = "70.41.3.18";
        when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
        // Last entry is the closest real proxy; first entry may be client-supplied
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.50, " + expectedIp);
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        for (int i = 0; i < 101; i++) {
            filter.doFilter(request, response, chain);
        }

        verify(chain, times(100)).doFilter(request, response);
        verify(response).setStatus(429);
        verify(request, never()).getRemoteAddr();
    }

    @Test
    void getClientIp_fallsBackToRemoteAddrWhenNoHeaders() throws Exception {
        when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.0.2.1");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(request).getRemoteAddr();
    }

    @Test
    void doFilter_webhookUnderBodyCap_passesThroughWithoutRateLimiting() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn("/payments/webhook");
        when(request.getContentLengthLong()).thenReturn(4_096L);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
        // Webhook path must never consult the per-IP rate-limit machinery.
        verify(request, never()).getHeader("CF-Connecting-IP");
    }

    @Test
    void doFilter_webhookWithoutContentLength_passesThrough() throws Exception {
        // A chunked request reports length -1; leave it to Spring/Tomcat's own limits, don't reject.
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn("/payments/webhook");
        when(request.getContentLengthLong()).thenReturn(-1L);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilter_webhookOverBodyCap_returns413AndDoesNotProcess() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn("/payments/webhook");
        when(request.getContentLengthLong()).thenReturn(512L * 1024L + 1L);
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(413);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void getClientIp_ignoresBlankCfConnectingIp() throws Exception {
        String expectedIp = "70.41.3.18";
        when(request.getHeader("CF-Connecting-IP")).thenReturn("   ");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.50, " + expectedIp);
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        for (int i = 0; i < 101; i++) {
            filter.doFilter(request, response, chain);
        }

        // Should have rate-limited based on the last XFF entry, not the blank CF header
        verify(chain, times(100)).doFilter(request, response);
        verify(response).setStatus(429);
    }
}
