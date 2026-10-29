package com.msp.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CreditCardValidator and CardMasker Tests")
class CreditCardValidatorTest {

    @Test
    @DisplayName("Valid VISA card")
    void testValidVisa() {
        String visa = "4111-1111-1111-1111";
        CardValidationResult result = CreditCardValidator.validate(visa);
        assertTrue(result.isValid());
        assertEquals(CardBrand.VISA, result.brand());
        assertEquals("4111111111111111", result.cleanNumber());
        assertNull(result.errorMessage());
    }

    @Test
    @DisplayName("Valid Mastercard")
    void testValidMastercard() {
        String mastercard = "5555-5555-5555-5557";
        CardValidationResult result = CreditCardValidator.validate(mastercard);
        assertTrue(result.isValid());
        assertEquals(CardBrand.MASTERCARD, result.brand());
    }

    @Test
    @DisplayName("Valid American Express")
    void testValidAmex() {
        String amex = "3782-822463-10005";
        CardValidationResult result = CreditCardValidator.validate(amex);
        assertTrue(result.isValid());
        assertEquals(CardBrand.AMEX, result.brand());
    }

    @Test
    @DisplayName("Invalid checksum card")
    void testInvalidChecksum() {
        String invalidCard = "4111-1111-1111-1119";
        CardValidationResult result = CreditCardValidator.validate(invalidCard);
        assertFalse(result.isValid());
        assertTrue(result.errorMessage().contains("Invalid Luhn checksum"));
    }

    @Test
    @DisplayName("Invalid length card")
    void testInvalidLength() {
        CardValidationResult result = CreditCardValidator.validate("12345");
        assertFalse(result.isValid());
        assertTrue(result.errorMessage().contains("length"));
    }

    @ParameterizedTest
    @CsvSource({
            "4111111111111111, VISA",
            "5555555555555557, MASTERCARD",
            "378282246310005, AMEX",
            "3528123456789012, JCB",
            "6011123456789012, DISCOVER"
    })
    @DisplayName("Brand detection from numbers")
    void testBrandDetection(String number, CardBrand expected) {
        assertEquals(expected, CreditCardValidator.detectBrand(number));
    }

    @Test
    @DisplayName("Card masking tests")
    void testCardMasking() {
        String visa = "4111111111111111";
        assertEquals("4111********1111", CardMasker.mask(visa));
        assertEquals("4111-****-****-1111", CardMasker.maskDashed(visa));

        String amex = "378282246310005";
        assertEquals("3782*******0005", CardMasker.mask(amex));
        assertEquals("3782-******-10005", CardMasker.maskDashed(amex));
    }

    @Test
    @DisplayName("Card dashed formatting")
    void testFormatDashed() {
        assertEquals("4111-1111-1111-1111", CardMasker.formatDashed("4111111111111111"));
        assertEquals("3782-822463-10005", CardMasker.formatDashed("378282246310005"));
    }
}
