package com.myhive.backend.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", "test-secret-key-that-is-at-least-32-bytes-long");
        ReflectionTestUtils.setField(jwtUtil, "jwtExpiration", 86400000L);
    }

    @Test
    void generateToken_containsEmailAndRole() {
        String token = jwtUtil.generateToken("user@test.com", "ADMIN");

        assertThat(token).isNotBlank();
        Claims claims = jwtUtil.extractClaims(token);
        assertThat(claims.getSubject()).isEqualTo("user@test.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void extractEmail_returnsSubject() {
        String token = jwtUtil.generateToken("admin@test.com", "ADMIN");

        assertThat(jwtUtil.extractEmail(token)).isEqualTo("admin@test.com");
    }

    @Test
    void extractRole_returnsRoleClaim() {
        String token = jwtUtil.generateToken("user@test.com", "ADMIN");

        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void validateToken_withValidToken_returnsTrue() {
        String token = jwtUtil.generateToken("user@test.com", "ADMIN");

        assertThat(jwtUtil.validateToken(token, "user@test.com")).isTrue();
    }

    @Test
    void validateToken_withWrongEmail_returnsFalse() {
        String token = jwtUtil.generateToken("user@test.com", "ADMIN");

        assertThat(jwtUtil.validateToken(token, "other@test.com")).isFalse();
    }

    @Test
    void isTokenExpired_withFreshToken_returnsFalse() {
        String token = jwtUtil.generateToken("user@test.com", "ADMIN");

        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    }

    @Test
    void isTokenExpired_withExpiredToken_returnsTrue() {
        ReflectionTestUtils.setField(jwtUtil, "jwtExpiration", 0L);
        String token = jwtUtil.generateToken("user@test.com", "ADMIN");

        assertThat(jwtUtil.isTokenExpired(token)).isTrue();
    }

    @Test
    void validateToken_withTamperedToken_returnsFalse() {
        String token = jwtUtil.generateToken("user@test.com", "ADMIN");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(jwtUtil.validateToken(tampered, "user@test.com")).isFalse();
    }

    @Test
    void extractClaims_withInvalidToken_throwsException() {
        assertThatThrownBy(() -> jwtUtil.extractClaims("not.a.valid.token"))
                .isInstanceOf(Exception.class);
    }
}
