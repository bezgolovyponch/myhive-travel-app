package com.myhive.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @Test
    void normalize_keepsPlusAndDigits() {
        assertThat(PhoneNumbers.normalize(" +44 7700 900-123 ")).isEqualTo("+447700900123");
        assertThat(PhoneNumbers.normalize("+420 (602) 123.456")).isEqualTo("+420602123456");
    }

    @Test
    void normalize_rejectsNumbersWithoutCountryCodeOrWithWrongLength() {
        assertThat(PhoneNumbers.normalize("07700900123")).isNull();
        assertThat(PhoneNumbers.normalize("+1234567")).isNull();
        assertThat(PhoneNumbers.normalize("+1234567890123456")).isNull();
        assertThat(PhoneNumbers.normalize("+0447700900123")).isNull();
        assertThat(PhoneNumbers.normalize("+44 77OO 900123")).isNull();
        assertThat(PhoneNumbers.normalize("  ")).isNull();
        assertThat(PhoneNumbers.normalize(null)).isNull();
    }

    @Test
    void mask_hidesTheMiddle() {
        assertThat(PhoneNumbers.mask("+447700900123")).isEqualTo("+44*******123");
        assertThat(PhoneNumbers.mask("+4412")).isEqualTo("***");
        assertThat(PhoneNumbers.mask(null)).isNull();
    }
}
