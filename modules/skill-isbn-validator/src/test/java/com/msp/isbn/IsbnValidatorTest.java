package com.msp.isbn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IsbnValidatorTest {

    @Test
    @DisplayName("Valid ISBN-10 with numeric check digit")
    void validIsbn10Numeric() {
        assertTrue(IsbnValidator.isValidIsbn10("0-306-40615-2"));
        assertTrue(IsbnValidator.isValidIsbn10("0306406152"));
        assertTrue(IsbnValidator.isValid("0-306-40615-2"));
    }

    @Test
    @DisplayName("Valid ISBN-10 with 'X' / 'x' check digit")
    void validIsbn10WithX() {
        assertTrue(IsbnValidator.isValidIsbn10("0-8044-2957-X"));
        assertTrue(IsbnValidator.isValidIsbn10("0-8044-2957-x"));
        assertTrue(IsbnValidator.isValidIsbn10("080442957X"));
        assertTrue(IsbnValidator.isValid("080442957x"));
    }

    @Test
    @DisplayName("Valid ISBN-13 standard 978 prefix")
    void validIsbn13() {
        assertTrue(IsbnValidator.isValidIsbn13("978-0-306-40615-7"));
        assertTrue(IsbnValidator.isValidIsbn13("9780306406157"));
        assertTrue(IsbnValidator.isValid("978-0-306-40615-7"));
    }

    @Test
    @DisplayName("Valid ISBN-13 with 979 prefix")
    void validIsbn13With979() {
        // First 12: 979118833179 -> calculate check digit
        int checkDigit = IsbnValidator.calculateCheckDigit13("979118833179");
        String isbn13 = "979-11-88331-79-" + checkDigit;
        assertTrue(IsbnValidator.isValidIsbn13(isbn13));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0-306-40615-3", // wrong check digit
            "0-8044-2957-9", // should be X
            "978-0-306-40615-0", // wrong check digit
            "123-456-789-0",
            "977-0-306-40615-7"  // non-bookland prefix 977
    })
    @DisplayName("Invalid ISBN checksums and prefixes")
    void invalidChecksums(String isbn) {
        assertFalse(IsbnValidator.isValid(isbn));
    }

    @Test
    @DisplayName("Convert ISBN-10 to ISBN-13")
    void convert10To13() {
        String isbn10 = "0-306-40615-2";
        String isbn13 = IsbnValidator.convertIsbn10To13(isbn10);
        assertEquals("9780306406157", isbn13);
        assertTrue(IsbnValidator.isValidIsbn13(isbn13));

        // With X check digit
        String isbn10WithX = "0-8044-2957-X";
        String converted = IsbnValidator.convertIsbn10To13(isbn10WithX);
        assertTrue(IsbnValidator.isValidIsbn13(converted));
    }

    @Test
    @DisplayName("Convert ISBN-13 to ISBN-10")
    void convert13To10() {
        String isbn13 = "978-0-306-40615-7";
        Optional<String> isbn10 = IsbnValidator.convertIsbn13To10(isbn13);
        assertTrue(isbn10.isPresent());
        assertEquals("0306406152", isbn10.get());

        // 979 cannot be converted to ISBN-10
        int checkDigit = IsbnValidator.calculateCheckDigit13("979118833179");
        String isbn979 = "979118833179" + checkDigit;
        assertEquals(Optional.empty(), IsbnValidator.convertIsbn13To10(isbn979));
    }

    @Test
    @DisplayName("Calculate check digit utilities")
    void calculateCheckDigits() {
        assertEquals('2', IsbnValidator.calculateCheckDigit10("030640615"));
        assertEquals('X', IsbnValidator.calculateCheckDigit10("080442957"));
        assertEquals(7, IsbnValidator.calculateCheckDigit13("978030640615"));
    }

    @Test
    @DisplayName("Validate detailed metadata")
    void validateDetailedMetadata() {
        IsbnInfo info10 = IsbnValidator.validate("0-306-40615-2");
        assertTrue(info10.valid());
        assertEquals(IsbnType.ISBN_10, info10.type());
        assertEquals("0306406152", info10.clean());
        assertEquals("0306406152", info10.isbn10());
        assertEquals("9780306406157", info10.isbn13());

        IsbnInfo info13 = IsbnValidator.validate("978-0-306-40615-7");
        assertTrue(info13.valid());
        assertEquals(IsbnType.ISBN_13, info13.type());
        assertEquals("0306406152", info13.isbn10());

        IsbnInfo invalid = IsbnValidator.validate("0-306-40615-9");
        assertFalse(invalid.valid());
        assertNotNull(invalid.errorMessage());
    }

    @Test
    @DisplayName("Boundary and malformed inputs")
    void boundaryInputs() {
        assertFalse(IsbnValidator.isValid(null));
        assertFalse(IsbnValidator.isValid(""));
        assertFalse(IsbnValidator.isValid("123"));
        assertFalse(IsbnValidator.isValid("123456789012345"));

        assertThrows(IllegalArgumentException.class, () -> IsbnValidator.calculateCheckDigit10("123"));
        assertThrows(IllegalArgumentException.class, () -> IsbnValidator.calculateCheckDigit13("123"));
        assertThrows(IllegalArgumentException.class, () -> IsbnValidator.convertIsbn10To13("0-000-00000-0"));
    }
}
