package com.myhive.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig {

    /** Single source of truth for the default allow-list — also consumed by
     *  {@link FrontendUrlResolver}, so CORS and Stripe return-URL validation cannot drift. */
    public static final String DEFAULT_ALLOWED_ORIGINS =
            "https://trivlu.com,https://www.trivlu.com,https://*.trivlu.com,"
                    + "https://myhive-frontend.onrender.com,http://localhost:3000,http://127.0.0.1:3000";

    @Value("${CORS_ALLOWED_ORIGINS:" + DEFAULT_ALLOWED_ORIGINS + "}")
    private String[] allowedOrigins;

    /**
     * Added to the list above, and empty everywhere but the dev profile. The frontend calls the API
     * through its own /api proxy, which passes the browser's Origin header on: a phone on the same
     * Wi-Fi opens the dev server by its LAN address, and every POST it made was refused here with
     * "Invalid CORS request" (GETs carry no Origin, so the pages loaded and only writes failed).
     * Kept apart from CORS_ALLOWED_ORIGINS so a .env that sets that one cannot switch this off.
     */
    @Value("${app.cors.dev-lan-origins:}")
    private String[] devLanOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Patterns (not exact origins) so wildcard subdomains like
        // https://prague.trivlu.com match https://*.trivlu.com.
        List<String> patterns = new ArrayList<>(Arrays.asList(allowedOrigins));
        patterns.addAll(Arrays.asList(devLanOrigins));
        configuration.setAllowedOriginPatterns(patterns);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
