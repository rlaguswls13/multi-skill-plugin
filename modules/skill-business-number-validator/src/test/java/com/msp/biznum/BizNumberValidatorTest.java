package com.msp.biznum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BizNumberValidatorTest {

    @Test
    @DisplayName("Valid business numbers with hyphens and plain digits")
    void validBusinessNumbers() {
        // Samsung Electronics: 120-81-47521
        assertTrue(BizNumberValidator.isValid("120-81-47521"));
        assertTrue(BizNumberValidator.isValid("1208147521"));

        // Naver: 220-81-62517
        assertTrue(BizNumberValidator.isValid("220-81-62517"));
        assertTrue(BizNumberValidator.isValid("2208162517"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "120-81-47520", // wrong check digit
            "120-81-47522", // wrong check digit
            "220-81-62518",
            "123-45-67890",
            "000-00-00001"
    })
    @DisplayName("Invalid checksum business numbers")
    void invalidChecksum(String number) {
        assertFalse(BizNumberValidator.isValid(number));
    }

    @Test
    @DisplayName("Validate detailed result")
    void validateDetailedResult() {
        BizNumberValidator.Result result = BizNumberValidator.validate("120-81-47521");
        assertTrue(result.valid());
        assertEquals("1208147521", result.raw());
        assertEquals("120-81-47521", result.formatted());
        assertEquals(BizType.PROFIT_CORP, result.bizType());
        assertNull(result.errorMessage());

        BizNumberValidator.Result failed = BizNumberValidator.validate("120-81-47522");
        assertFalse(failed.valid());
        assertNotNull(failed.errorMessage());
    }

    @Test
    @DisplayName("Calculate check digit")
    void calculateCheckDigit() {
        assertEquals(1, BizNumberValidator.calculateCheckDigit("120814752"));
        assertEquals(7, BizNumberValidator.calculateCheckDigit("220816251"));
    }

    @Test
    @DisplayName("BizType classification")
    void bizTypeClassification() {
        // 01-79: Individual taxable
        assertEquals(BizType.INDIVIDUAL_TAXABLE, BizNumberValidator.getBizType("101-05-12345"));
        // 80: Non-profit
        assertEquals(BizType.NON_PROFIT, BizNumberValidator.getBizType("101-80-12345"));
        // 81-87: Profit corporation
        assertEquals(BizType.PROFIT_CORP, BizNumberValidator.getBizType("120-81-47521"));
        // 88: Public institution
        assertEquals(BizType.PUBLIC_INSTITUTION, BizNumberValidator.getBizType("101-88-12345"));
        // 89: Foreign corporation
        assertEquals(BizType.FOREIGN_CORP, BizNumberValidator.getBizType("101-89-12345"));
        // 90-99: Individual tax-exempt
        assertEquals(BizType.INDIVIDUAL_TAX_EXEMPT, BizNumberValidator.getBizType("101-95-12345"));
    }

    @Test
    @DisplayName("Format and unformat")
    void formatting() {
        assertEquals("120-81-47521", BizNumberValidator.format("1208147521"));
        assertEquals("1208147521", BizNumberValidator.unformat("120-81-47521"));
        assertTrue(BizNumberValidator.hasValidPattern("120-81-47521"));
        assertFalse(BizNumberValidator.hasValidPattern("1208147521"));
    }

    @Test
    @DisplayName("Null and boundary inputs")
    void boundaryInputs() {
        assertFalse(BizNumberValidator.isValid(null));
        assertFalse(BizNumberValidator.isValid(""));
        assertFalse(BizNumberValidator.isValid("12345"));
        assertFalse(BizNumberValidator.isValid("120-81-4752199"));
        assertFalse(BizNumberValidator.isValid("ABC-DE-FGHIJ"));

        assertThrows(IllegalArgumentException.class, () -> BizNumberValidator.calculateCheckDigit("123"));
        assertThrows(NullPointerException.class, () -> BizNumberValidator.calculateCheckDigit(null));
    }
}
