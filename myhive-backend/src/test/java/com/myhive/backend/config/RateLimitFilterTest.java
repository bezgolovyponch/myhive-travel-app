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
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_overLimit_returns429() throws Exception {
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
    void getClientIp_usesXForwardedForIfPresent() throws Exception {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.50, 70.41.3.18");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        // Fill up the rate limit using the X-Forwarded-For IP
        for (int i = 0; i < 100; i++) {
            filter.doFilter(request, response, chain);
        }

        // 101st request with same X-Forwarded-For should be blocked with 429
        filter.doFilter(request, response, chain);

        verify(chain, times(100)).doFilter(request, response);
        verify(response).setStatus(429);
        // Verify remoteAddr was never called (it's not needed when X-Forwarded-For is present)
        verify(request, never()).getRemoteAddr();
    }
}
