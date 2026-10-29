package com.msp.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LuhnChecksum Algorithm Tests")
class LuhnChecksumTest {

    @Test
    @DisplayName("Wikipedia canonical test vector 7992739871x")
    void testCanonicalVector() {
        // Payload: 7992739871 -> check digit should be 3 -> full valid number: 79927398713
        // Note: 79927398713 is 11 digits, Luhn algorithm calculates check digit:
        int checkDigit = LuhnChecksum.calculateCheckDigit("7992739871");
        assertEquals(3, checkDigit);
    }

    @ParameterizedTest
    @CsvSource({
            "4992739871, 6",
            "123456781234567, 0",
            "411111111111111, 1"
    })
    @DisplayName("Calculate check digit tests")
    void testCheckDigitCalculation(String payload, int expectedCheckDigit) {
        assertEquals(expectedCheckDigit, LuhnChecksum.calculateCheckDigit(payload));
        assertTrue(LuhnChecksum.validate(payload + expectedCheckDigit));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "49927398716",
            "4111-1111-1111-1111",
            "4111 1111 1111 1111",
            "1234567812345670"
    })
    @DisplayName("Valid Luhn numbers")
    void testValidLuhn(String number) {
        assertTrue(LuhnChecksum.validate(number));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "49927398717", // wrong check digit
            "4111-1111-1111-1112",
            "1234567812345679",
            "not-a-number",
            ""
    })
    @DisplayName("Invalid Luhn numbers")
    void testInvalidLuhn(String number) {
        assertFalse(LuhnChecksum.validate(number));
    }
}
