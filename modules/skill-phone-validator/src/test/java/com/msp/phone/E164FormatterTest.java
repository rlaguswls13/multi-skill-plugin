package com.msp.phone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("E164Formatter Tests")
class E164FormatterTest {

    @Test
    @DisplayName("Clean digits and plus")
    void testClean() {
        assertEquals("+821012345678", E164Formatter.clean("+82 (10) 1234-5678"));
        assertEquals("01012345678", E164Formatter.clean("010-1234-5678"));
        assertEquals("01012345678", E164Formatter.digitsOnly("010-1234-5678"));
        assertEquals("8201012345678", E164Formatter.digitsOnly("+82 010-1234-5678"));
    }

    @Test
    @DisplayName("Korean E.164 normalization")
    void testToE164Korean() {
        assertEquals("+821012345678", E164Formatter.toE164("010-1234-5678", CountryCode.KR));
        assertEquals("+821012345678", E164Formatter.toE164("01012345678", CountryCode.KR));
        assertEquals("+82212345678", E164Formatter.toE164("02-1234-5678", CountryCode.KR));
        assertEquals("+8215881234", E164Formatter.toE164("1588-1234", CountryCode.KR));
    }

    @Test
    @DisplayName("US / International E.164 normalization")
    void testToE164International() {
        assertEquals("+14155552671", E164Formatter.toE164("(415) 555-2671", CountryCode.US));
        assertEquals("+14155552671", E164Formatter.toE164("+1-415-555-2671", CountryCode.US));
        assertEquals("+81312345678", E164Formatter.toE164("+81 3-1234-5678", CountryCode.JP));
    }

    @Test
    @DisplayName("Korean dashed phone formatting")
    void testFormatKorean() {
        assertEquals("010-1234-5678", E164Formatter.formatKorean("01012345678"));
        assertEquals("011-123-4567", E164Formatter.formatKorean("0111234567"));
        assertEquals("02-1234-5678", E164Formatter.formatKorean("0212345678"));
        assertEquals("02-123-4567", E164Formatter.formatKorean("021234567"));
        assertEquals("031-123-4567", E164Formatter.formatKorean("0311234567"));
        assertEquals("031-1234-5678", E164Formatter.formatKorean("03112345678"));
        assertEquals("1588-1234", E164Formatter.formatKorean("15881234"));
        assertEquals("010-1234-5678", E164Formatter.formatKorean("+821012345678"));
    }
}
