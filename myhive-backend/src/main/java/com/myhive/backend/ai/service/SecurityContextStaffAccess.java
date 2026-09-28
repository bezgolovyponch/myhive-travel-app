package com.myhive.backend.ai.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Reads the request's authentication. The {@code /ai/**} endpoints are {@code permitAll}, but a bearer
 * token sent to them is still validated and mapped to authorities by the resource server filter, so an
 * admin console call carries {@code ROLE_ADMIN}/{@code ROLE_MANAGER} here and an anonymous one nothing.
 * Only ever read on the request thread: the generation job runs without a security context, and never
 * needs one.
 */
@Component
public class SecurityContextStaffAccess implements StaffAccess {

    private static final Set<String> STAFF_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_MANAGER");

    @Override
    public boolean isStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (STAFF_AUTHORITIES.contains(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
