package com.myhive.backend.ai.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityContextStaffAccessTest {

    private final SecurityContextStaffAccess access = new SecurityContextStaffAccess();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousRequest_isNotStaff() {
        assertThat(access.isStaff()).isFalse();
    }

    @Test
    void adminAndManager_areStaff() {
        for (String expectedRole : List.of("ROLE_ADMIN", "ROLE_MANAGER")) {
            authenticatedWith(expectedRole);

            assertThat(access.isStaff()).as(expectedRole).isTrue();
        }
    }

    @Test
    void aSignedInUserWithoutAStaffRole_isNotStaff() {
        authenticatedWith("ROLE_USER");

        assertThat(access.isStaff()).isFalse();
    }

    private static void authenticatedWith(String authority) {
        TestingAuthenticationToken token = new TestingAuthenticationToken("someone", "n/a",
                List.of(new SimpleGrantedAuthority(authority)));
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
