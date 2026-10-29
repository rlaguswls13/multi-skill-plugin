package com.msp.biznum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CorpNumberValidatorTest {

    @Test
    @DisplayName("Valid corporate registration number")
    void validCorpNumber() {
        // First 12 digits: 110111-123456 -> check digit 9
        String validNumber = "110111-1234569";
        assertTrue(CorpNumberValidator.isValid(validNumber));
        assertTrue(CorpNumberValidator.isValid("1101111234569"));

        CorpNumberValidator.Result result = CorpNumberValidator.validate(validNumber);
        assertTrue(result.valid());
        assertEquals("1101111234569", result.raw());
        assertEquals("110111-1234569", result.formatted());
        assertEquals(CorporateType.STOCK_COMPANY, result.corporateType());
        assertEquals("1101", result.registryOfficeCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "110111-1234560",
            "110111-1234568",
            "123456-7890123",
            "000000-0000000"
    })
    @DisplayName("Invalid checksum corporate numbers")
    void invalidChecksum(String number) {
        assertFalse(CorpNumberValidator.isValid(number));
    }

    @Test
    @DisplayName("CorporateType classification")
    void corporateTypes() {
        // 11: 주식회사
        assertEquals(CorporateType.STOCK_COMPANY, CorpNumberValidator.identifyCorporateType("110111-1234569"));
        // 12: 유한회사
        assertEquals(CorporateType.LIMITED_COMPANY, CorpNumberValidator.identifyCorporateType("110112-1234560"));
        // 13: 합명회사
        assertEquals(CorporateType.GENERAL_PARTNERSHIP, CorpNumberValidator.identifyCorporateType("110113-1234560"));
        // 14: 합자회사
        assertEquals(CorporateType.LIMITED_PARTNERSHIP, CorpNumberValidator.identifyCorporateType("110114-1234560"));
        // 15: 유한책임회사
        assertEquals(CorporateType.LIMITED_LIABILITY_COMPANY, CorpNumberValidator.identifyCorporateType("110115-1234560"));
        // 21: 사단법인
        assertEquals(CorporateType.INCORPORATED_ASSOCIATION, CorpNumberValidator.identifyCorporateType("110121-1234560"));
        // 22: 재단법인
        assertEquals(CorporateType.INCORPORATED_FOUNDATION, CorpNumberValidator.identifyCorporateType("110122-1234560"));
        // 31: 학교법인
        assertEquals(CorporateType.SCHOOL_CORPORATION, CorpNumberValidator.identifyCorporateType("110131-1234560"));
        // 32: 의료법인
        assertEquals(CorporateType.MEDICAL_CORPORATION, CorpNumberValidator.identifyCorporateType("110132-1234560"));
        // 41: 영농조합법인
        assertEquals(CorporateType.AGRICULTURAL_COOPERATIVE, CorpNumberValidator.identifyCorporateType("110141-1234560"));
        // 51: 외국법인
        assertEquals(CorporateType.FOREIGN_CORPORATION, CorpNumberValidator.identifyCorporateType("110151-1234560"));
        // other
        assertEquals(CorporateType.OTHER, CorpNumberValidator.identifyCorporateType("110199-1234560"));
    }

    @Test
    @DisplayName("Calculate check digit")
    void calculateCheckDigit() {
        assertEquals(9, CorpNumberValidator.calculateCheckDigit("110111123456"));
    }

    @Test
    @DisplayName("Format and unformat")
    void formatting() {
        assertEquals("110111-1234569", CorpNumberValidator.format("1101111234569"));
        assertEquals("1101111234569", CorpNumberValidator.unformat("110111-1234569"));
        assertTrue(CorpNumberValidator.hasValidPattern("110111-1234569"));
        assertFalse(CorpNumberValidator.hasValidPattern("1101111234569"));
    }

    @Test
    @DisplayName("Null and boundary inputs")
    void boundaryInputs() {
        assertFalse(CorpNumberValidator.isValid(null));
        assertFalse(CorpNumberValidator.isValid(""));
        assertFalse(CorpNumberValidator.isValid("12345"));
        assertFalse(CorpNumberValidator.isValid("110111-123456900"));

        assertThrows(IllegalArgumentException.class, () -> CorpNumberValidator.calculateCheckDigit("12345"));
        assertThrows(NullPointerException.class, () -> CorpNumberValidator.calculateCheckDigit(null));
    }

    @Test
    @DisplayName("Facade KoreanRegistrationValidators test")
    void facadeTest() {
        assertTrue(KoreanRegistrationValidators.isValidBizNumber("120-81-47521"));
        assertTrue(KoreanRegistrationValidators.isValidCorpNumber("110111-1234569"));
        assertTrue(KoreanRegistrationValidators.validateBizNumber("120-81-47521").valid());
        assertTrue(KoreanRegistrationValidators.validateCorpNumber("110111-1234569").valid());
    }
}
