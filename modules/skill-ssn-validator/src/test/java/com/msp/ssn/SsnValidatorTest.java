package com.msp.ssn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SsnValidatorTest {

    @Test
    @DisplayName("Valid domestic Korean SSN validation")
    void validDomesticSsn() {
        // 900515-1234569: 1990-05-15, Male, Korean, check digit 9
        int checkDigit = SsnValidator.calculateCheckDigit("900515123456", false);
        assertEquals(9, checkDigit);

        String ssn = "900515-123456" + checkDigit;
        assertTrue(SsnValidator.isValid(ssn));
        assertTrue(SsnValidator.isValid(SsnValidator.unformat(ssn)));

        SsnInfo info = SsnValidator.validate(ssn);
        assertTrue(info.valid());
        assertEquals("9005151234569", info.raw());
        assertEquals("900515-1234569", info.formatted());
        assertEquals(LocalDate.of(1990, 5, 15), info.birthDate());
        assertEquals(Gender.MALE, info.gender());
        assertFalse(info.foreigner());
        assertEquals(1900, info.century());
        assertTrue(info.internationalAge() >= 30);
        assertTrue(info.koreanAge() >= 31);
        assertNull(info.errorMessage());
    }

    @Test
    @DisplayName("Valid 2000s born citizen validation")
    void valid2000sCitizen() {
        // Born 2005-12-25, Female, code 4
        int checkDigit = SsnValidator.calculateCheckDigit("051225412345", false);
        String ssn = "051225-412345" + checkDigit;

        assertTrue(SsnValidator.isValid(ssn));
        SsnInfo info = SsnValidator.validate(ssn);
        assertTrue(info.valid());
        assertEquals(LocalDate.of(2005, 12, 25), info.birthDate());
        assertEquals(Gender.FEMALE, info.gender());
        assertFalse(info.foreigner());
        assertEquals(2000, info.century());
    }

    @Test
    @DisplayName("Valid Foreigner registration number validation")
    void validForeignerRegistrationNumber() {
        // Born 1985-03-20, Male foreigner, code 5
        int checkDigit = SsnValidator.calculateCheckDigit("850320512345", true);
        String foreignerSsn = "850320-512345" + checkDigit;

        assertTrue(SsnValidator.isValid(foreignerSsn));
        SsnInfo info = SsnValidator.validate(foreignerSsn);
        assertTrue(info.valid());
        assertTrue(info.foreigner());
        assertEquals(Gender.MALE, info.gender());
        assertEquals(LocalDate.of(1985, 3, 20), info.birthDate());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "900515-1234560", // wrong check digit
            "900515-1234568", // wrong check digit
            "900515-2234569", // wrong gender code with check digit calculated for male
            "000000-0000000"  // all zeros
    })
    @DisplayName("Invalid checksum SSN rejection")
    void invalidChecksum(String ssn) {
        assertFalse(SsnValidator.isValid(ssn));
        SsnInfo info = SsnValidator.validate(ssn);
        assertFalse(info.valid());
        assertNotNull(info.errorMessage());
    }

    @Test
    @DisplayName("Invalid birth dates in SSN")
    void invalidBirthDates() {
        // Feb 31st is invalid date
        assertFalse(SsnValidator.isValid("900231-1234567"));
        SsnInfo info = SsnValidator.validate("900231-1234567");
        assertFalse(info.valid());
        assertTrue(info.errorMessage().contains("Invalid birth date"));

        // Month 13 is invalid
        assertFalse(SsnValidator.isValid("901301-1234567"));
    }

    @Test
    @DisplayName("Metadata extractors without full checksum check")
    void metadataExtractors() {
        assertEquals(LocalDate.of(1995, 8, 15), SsnValidator.extractBirthDate("950815-1234567").orElseThrow());
        assertEquals(Gender.MALE, SsnValidator.extractGender("950815-1234567").orElseThrow());
        assertEquals(Gender.FEMALE, SsnValidator.extractGender("950815-2234567").orElseThrow());
        assertFalse(SsnValidator.isForeigner("950815-1234567"));
        assertTrue(SsnValidator.isForeigner("950815-5234567"));
        assertTrue(SsnValidator.isForeigner("020815-8234567"));
    }

    @Test
    @DisplayName("Boundary and malformed inputs")
    void boundaryInputs() {
        assertFalse(SsnValidator.isValid(null));
        assertFalse(SsnValidator.isValid(""));
        assertFalse(SsnValidator.isValid("123456"));
        assertFalse(SsnValidator.isValid("900515-123456789"));
        assertFalse(SsnValidator.isValid("ABCDEF-GHIJKLM"));

        assertThrows(IllegalArgumentException.class, () -> SsnValidator.calculateCheckDigit("123", false));
        assertThrows(NullPointerException.class, () -> SsnValidator.calculateCheckDigit(null, false));
    }
}
