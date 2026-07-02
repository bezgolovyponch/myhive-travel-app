package com.myhive.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FrontendUrlResolverTest {

    private static final String DEFAULT_URL = "https://trivlu.com";

    private FrontendUrlResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new FrontendUrlResolver(
                new String[] {
                        "https://trivlu.com",
                        "https://www.trivlu.com",
                        "https://*.trivlu.com",
                        "http://localhost:3000"
                },
                DEFAULT_URL);
    }

    @Test
    void resolve_returnsDefault_whenOriginAbsentOrBlank() {
        assertThat(resolver.resolve(null)).isEqualTo(DEFAULT_URL);
        assertThat(resolver.resolve("")).isEqualTo(DEFAULT_URL);
        assertThat(resolver.resolve("   ")).isEqualTo(DEFAULT_URL);
    }

    @Test
    void resolve_returnsOrigin_whenExactMatch() {
        assertThat(resolver.resolve("https://trivlu.com")).isEqualTo("https://trivlu.com");
        assertThat(resolver.resolve("https://www.trivlu.com")).isEqualTo("https://www.trivlu.com");
        assertThat(resolver.resolve("http://localhost:3000")).isEqualTo("http://localhost:3000");
    }

    @Test
    void resolve_returnsOrigin_whenSubdomainMatchesWildcard() {
        String expectedOrigin = "https://prague.trivlu.com";
        assertThat(resolver.resolve(expectedOrigin)).isEqualTo(expectedOrigin);
        assertThat(resolver.resolve("https://barcelona.trivlu.com")).isEqualTo("https://barcelona.trivlu.com");
    }

    @Test
    void resolve_normalizesCaseAndTrailingSlash() {
        assertThat(resolver.resolve("HTTPS://PRAGUE.TRIVLU.COM")).isEqualTo("https://prague.trivlu.com");
        assertThat(resolver.resolve("https://prague.trivlu.com/")).isEqualTo("https://prague.trivlu.com");
    }

    @Test
    void resolve_returnsDefault_whenOriginForeign() {
        assertThat(resolver.resolve("https://evil.com")).isEqualTo(DEFAULT_URL);
        assertThat(resolver.resolve("http://trivlu.com")).isEqualTo(DEFAULT_URL); // scheme downgrade
        assertThat(resolver.resolve("https://trivlu.com:8443")).isEqualTo(DEFAULT_URL); // unexpected port
    }

    @Test
    void resolve_returnsDefault_whenHostMerelyContainsAllowedDomain() {
        // The wildcard is a single hostname label — suffix/prefix tricks must not match.
        assertThat(resolver.resolve("https://prague.trivlu.com.evil.com")).isEqualTo(DEFAULT_URL);
        assertThat(resolver.resolve("https://eviltrivlu.com")).isEqualTo(DEFAULT_URL);
        assertThat(resolver.resolve("https://a.b.trivlu.com")).isEqualTo(DEFAULT_URL);
    }

    @Test
    void resolve_returnsDefault_whenWildcardLabelEmpty() {
        assertThat(resolver.resolve("https://.trivlu.com")).isEqualTo(DEFAULT_URL);
    }
}
