package com.msp.phone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PhoneNumberValidator Tests")
class PhoneNumberValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "010-1234-5678",
            "01012345678",
            "011-123-4567",
            "016-987-6543",
            "019-111-2222",
            "+82-10-1234-5678",
            "+821012345678"
    })
    @DisplayName("Valid Korean mobile numbers")
    void testValidKoreanMobile(String phone) {
        assertTrue(PhoneNumberValidator.isValidKoreanMobile(phone));
        assertTrue(PhoneNumberValidator.isValidKorean(phone));
        assertTrue(PhoneNumberValidator.isValid(phone));
        assertEquals(PhoneType.MOBILE, PhoneNumberValidator.detectType(phone));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "02-123-4567",
            "02-1234-5678",
            "031-123-4567",
            "051-987-6543",
            "064-123-4567"
    })
    @DisplayName("Valid Korean landline numbers")
    void testValidKoreanLandline(String phone) {
        assertTrue(PhoneNumberValidator.isValidKorean(phone));
        assertEquals(PhoneType.LANDLINE, PhoneNumberValidator.detectType(phone));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1588-1234",
            "1577-1234",
            "1544-0000",
            "1688-9999",
            "1800-1111"
    })
    @DisplayName("Valid Korean representative numbers")
    void testValidKoreanRepresentative(String phone) {
        assertTrue(PhoneNumberValidator.isValidKorean(phone));
        assertEquals(PhoneType.REPRESENTATIVE, PhoneNumberValidator.detectType(phone));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "070-1234-5678",
            "07012345678"
    })
    @DisplayName("Valid Korean VoIP numbers")
    void testValidKoreanVoip(String phone) {
        assertTrue(PhoneNumberValidator.isValidKorean(phone));
        assertEquals(PhoneType.VOIP, PhoneNumberValidator.detectType(phone));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+14155552671",
            "+81312345678",
            "+442071838750",
            "+861012345678"
    })
    @DisplayName("Valid International E.164 numbers")
    void testValidInternationalE164(String phone) {
        assertTrue(PhoneNumberValidator.isValidE164(phone));
        assertTrue(PhoneNumberValidator.isValid(phone));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "014-123-4567", // invalid mobile prefix
            "010-12-34",    // too short
            "not-a-number",
            "+01234567",   // dial code cannot start with 0
            "",
            "   "
    })
    @DisplayName("Invalid phone numbers")
    void testInvalidPhoneNumbers(String phone) {
        assertFalse(PhoneNumberValidator.isValid(phone));
    }

    @Test
    @DisplayName("Country detection test")
    void testCountryDetection() {
        assertEquals(CountryCode.US, PhoneNumberValidator.detectCountry("+1 (415) 555-2671"));
        assertEquals(CountryCode.JP, PhoneNumberValidator.detectCountry("+81-3-1234-5678"));
        assertEquals(CountryCode.KR, PhoneNumberValidator.detectCountry("+82-10-1234-5678"));
        assertEquals(CountryCode.KR, PhoneNumberValidator.detectCountry("010-1234-5678"));
    }

    @Test
    @DisplayName("Full parse test")
    void testParse() {
        PhoneNumber kr = PhoneNumberValidator.parse("01012345678", CountryCode.KR);
        assertTrue(kr.isValid());
        assertEquals("+821012345678", kr.e164());
        assertEquals("010-1234-5678", kr.formatted());
        assertEquals(CountryCode.KR, kr.country());
        assertEquals(PhoneType.MOBILE, kr.type());

        PhoneNumber us = PhoneNumberValidator.parse("+14155552671", CountryCode.US);
        assertTrue(us.isValid());
        assertEquals("+14155552671", us.e164());
        assertEquals(CountryCode.US, us.country());
    }
}
