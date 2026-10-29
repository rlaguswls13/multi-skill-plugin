package com.msp.ssn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SsnMaskerTest {

    @Test
    @DisplayName("Standard masking preserving gender digit")
    void standardMasking() {
        assertEquals("900101-1******", SsnMasker.mask("900101-1234567"));
        assertEquals("900101-1******", SsnMasker.mask("9001011234567"));
        assertEquals("051225-4******", SsnMasker.mask("0512254123456"));
    }

    @Test
    @DisplayName("Full back-part masking")
    void fullMasking() {
        assertEquals("900101-*******", SsnMasker.maskFull("900101-1234567"));
        assertEquals("900101-*******", SsnMasker.maskFull("9001011234567"));
    }

    @Test
    @DisplayName("Birth date only masking")
    void birthOnlyMasking() {
        assertEquals("******-1234567", SsnMasker.maskBirthOnly("900101-1234567"));
        assertEquals("******-1234567", SsnMasker.maskBirthOnly("9001011234567"));
    }

    @Test
    @DisplayName("Custom masking")
    void customMasking() {
        // Keep 6 prefix, 2 suffix -> "900101-*****67"
        assertEquals("900101-*****67", SsnMasker.maskCustom("900101-1234567", 6, 2, '*'));
        assertEquals("900101-#####67", SsnMasker.maskCustom("900101-1234567", 6, 2, '#'));
    }

    @Test
    @DisplayName("Boundary and null cases")
    void boundaryCases() {
        assertNull(SsnMasker.mask(null));
        assertNull(SsnMasker.maskFull(null));
        assertNull(SsnMasker.maskBirthOnly(null));
        assertNull(SsnMasker.maskCustom(null, 6, 1, '*'));

        // Wrong lengths returned untouched
        assertEquals("123", SsnMasker.mask("123"));
        assertEquals("123456789012345", SsnMasker.mask("123456789012345"));
    }
}
