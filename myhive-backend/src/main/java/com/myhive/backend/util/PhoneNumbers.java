package com.myhive.backend.util;

/**
 * The one place a phone number is normalized and masked, so the vote API, the address book and the
 * logs agree on what a number looks like.
 */
public final class PhoneNumbers {

    /** E.164 allows up to 15 digits; 8 is the shortest real number with a country code. */
    static final int MIN_DIGITS = 8;
    static final int MAX_DIGITS = 15;

    private PhoneNumbers() {
    }

    /**
     * {@code "+44 7700 900-123"} -> {@code "+447700900123"}. The number must start with "+" and a
     * country code; spaces, dashes, dots and brackets are dropped. Returns null for null/blank or for
     * anything that is not 8–15 digits after the "+".
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("+")) {
            return null;
        }
        String rest = trimmed.substring(1).replaceAll("[\\s().\\-]", "");
        if (!rest.matches("\\d{" + MIN_DIGITS + "," + MAX_DIGITS + "}") || rest.startsWith("0")) {
            return null;
        }
        return "+" + rest;
    }

    /** {@code "+447700900123" -> "+44*******123"}: enough to tell numbers apart in a log, never the whole number. */
    public static String mask(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        if (phone.length() <= 6) {
            return "***";
        }
        return phone.substring(0, 3) + "*".repeat(phone.length() - 6) + phone.substring(phone.length() - 3);
    }
}
