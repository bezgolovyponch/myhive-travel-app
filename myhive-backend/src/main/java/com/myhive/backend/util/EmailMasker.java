package com.myhive.backend.util;

/**
 * Single source of truth for masking an email address before it is logged or returned in a response,
 * so the two callers (EmailService, PaymentService) cannot diverge. Never echoes back an unmasked value:
 * an input with no '@' yields {@code "***"} rather than the original string (I1).
 */
public final class EmailMasker {

    private EmailMasker() {
    }

    /** {@code "alice@x.com" -> "a****@x.com"}, single-char local {@code "a@x.com" -> "a*@x.com"},
     *  empty local {@code "@x.com" -> "***@x.com"}, no '@' -> {@code "***"}, null/blank -> {@code null}. */
    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at < 0) {
            return "***";
        }
        if (at == 0) {
            return "***" + email.substring(at);
        }
        String local = email.substring(0, at);
        String masked = local.charAt(0) + "*".repeat(Math.max(1, local.length() - 1));
        return masked + email.substring(at);
    }
}
