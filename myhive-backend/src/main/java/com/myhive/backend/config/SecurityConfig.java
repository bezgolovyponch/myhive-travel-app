package com.myhive.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    @Value("${app.auth.roles-claim}")
    private String rolesClaim;

    private final AudienceValidator audienceValidator;

    public SecurityConfig(AudienceValidator audienceValidator) {
        this.audienceValidator = audienceValidator;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin) // Allow H2 console iframes in dev
                )
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints
                        .requestMatchers("/auth/**").permitAll()
                        // Public API endpoints
                        .requestMatchers("/destinations/**").permitAll()
                        .requestMatchers("/activities/**").permitAll()
                        .requestMatchers("/categories/**").permitAll()
                        .requestMatchers("/blog/**").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/bookings/*/status").hasRole("ADMIN")
                        .requestMatchers("/bookings/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/contact").permitAll()
                        // Sitemap
                        .requestMatchers("/sitemap.xml").permitAll()
                        // Health & info
                        .requestMatchers("/", "/health/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // H2 console (dev only, disabled in prod)
                        .requestMatchers("/h2-console/**").permitAll()
                        // Admin endpoints — CSV import/export: ADMIN only (must come before /admin/activities/**)
                        .requestMatchers("/admin/activities/export").hasRole("ADMIN")
                        .requestMatchers("/admin/activities/import/**").hasRole("ADMIN")
                        // Admin endpoints — activities & blog: ADMIN or MANAGER
                        .requestMatchers("/admin/activities/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/admin/blog/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/admin/upload").hasAnyRole("ADMIN", "MANAGER")
                        // Admin endpoints — everything else: ADMIN only
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new Auth0RolesConverter());
        return converter;
    }

    @Bean
    @ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.issuer-uri")
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                audienceValidator
        ));
        return decoder;
    }

    private class Auth0RolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            List<String> roles = jwt.getClaimAsStringList(rolesClaim);
            if (roles == null) {
                return Collections.emptyList();
            }
            return roles.stream()
                    .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        }
    }
}
