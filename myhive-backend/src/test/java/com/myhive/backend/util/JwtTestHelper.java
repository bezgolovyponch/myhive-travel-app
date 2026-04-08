package com.myhive.backend.util;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public final class JwtTestHelper {
    private static final String ROLES_CLAIM = "https://trivlu.com/roles";

    public static RequestPostProcessor adminJwt() {
        return jwt().jwt(j -> j
                .subject("admin@test.com")
                .claim("email", "admin@test.com")
                .claim(ROLES_CLAIM, List.of("ADMIN"))
        ).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    public static RequestPostProcessor managerJwt() {
        return jwt().jwt(j -> j
                .subject("manager@test.com")
                .claim("email", "manager@test.com")
                .claim(ROLES_CLAIM, List.of("MANAGER"))
        ).authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    public static RequestPostProcessor userJwt() {
        return jwt().jwt(j -> j
                .subject("user@test.com")
                .claim("email", "user@test.com")
                .claim(ROLES_CLAIM, List.of("USER"))
        ).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private JwtTestHelper() {
    }
}
