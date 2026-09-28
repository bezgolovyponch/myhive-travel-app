package com.myhive.backend.ai.service;

/**
 * Whether the current request comes from a member of staff - an ADMIN or MANAGER signed in to the admin
 * console. Staff test the planner from {@code /admin/ai-planner} with their own Auth0 token on the public
 * {@code /ai/**} endpoints; the anonymous guards (captcha, the per-network daily cap) and, while
 * {@code app.ai.staff-preview} is on, the public kill switch do not apply to them.
 */
@FunctionalInterface
public interface StaffAccess {

    boolean isStaff();
}
