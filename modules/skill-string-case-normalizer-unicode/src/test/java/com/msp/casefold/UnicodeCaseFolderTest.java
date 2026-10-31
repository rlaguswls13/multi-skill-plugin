package com.msp.casefold;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UnicodeCaseFolder Tests")
class UnicodeCaseFolderTest {

    @Test
    @DisplayName("German sharp S: 'ß' and 'ẞ' fold to 'ss'")
    void testGermanSharpS() {
        assertEquals("ss", UnicodeCaseFolder.fold("ß"));
        assertEquals("ss", UnicodeCaseFolder.fold("ẞ"));
        assertEquals("strasse", UnicodeCaseFolder.fold("Straße"));
        assertEquals("gross", UnicodeCaseFolder.fold("Groß"));

        assertTrue(UnicodeCaseFolder.equalsIgnoreCase("Straße", "STRASSE"));
        assertTrue(UnicodeCaseFolder.equalsIgnoreCase("groß", "GROSS"));
        assertTrue(UnicodeCaseFolder.equalsIgnoreCase("WEISS", "weiß"));
        assertTrue(UnicodeCaseFolder.equalsIgnoreCase("WEISS", "weiẞ"));
    }

    @Test
    @DisplayName("Greek sigma folding: capital Σ, small σ, and final ς are unified")
    void testGreekSigmas() {
        assertEquals("σ", UnicodeCaseFolder.fold("Σ"));
        assertEquals("σ", UnicodeCaseFolder.fold("σ"));
        assertEquals("σ", UnicodeCaseFolder.fold("ς"));

        // "ὈΔΥΣΣΕΎΣ" (Odysseus in Greek capitals with accent) vs "ὀδυσσεύς" (with final sigma)
        assertTrue(UnicodeCaseFolder.equalsIgnoreCase("ὈΔΥΣΣΕΎΣ", "ὀδυσσεύς"));
        assertTrue(UnicodeCaseFolder.equalsIgnoreCase("σ", "ς"));
        assertTrue(UnicodeCaseFolder.equalsIgnoreCase("Σ", "ς"));
    }

    @Test
    @DisplayName("Standard ligatures folding (ff, fi, fl, ffi, ffl, st)")
    void testLigatures() {
        assertEquals("ff", UnicodeCaseFolder.fold("ﬀ"));
        assertEquals("fi", UnicodeCaseFolder.fold("ﬁ"));
        assertEquals("fl", UnicodeCaseFolder.fold("ﬂ"));
        assertEquals("ffi", UnicodeCaseFolder.fold("ﬃ"));
        assertEquals("ffl", UnicodeCaseFolder.fold("ﬄ"));
        assertEquals("st", UnicodeCaseFolder.fold("ﬅ"));
        assertEquals("st", UnicodeCaseFolder.fold("ﬆ"));

        assertTrue(UnicodeCaseFolder.equalsIgnoreCase("office", "oﬃce"));
        assertTrue(UnicodeCaseFolder.equalsIgnoreCase("FLY", "ﬂy"));
        assertTrue(UnicodeCaseFolder.equalsIgnoreCase("FIRST", "ﬁrst"));
    }

    @Test
    @DisplayName("Special Latin and unit signs (long s, micro sign, Kelvin, Angstrom)")
    void testSpecialLatinAndSymbols() {
        assertEquals("s", UnicodeCaseFolder.fold("ſ")); // long s
        assertEquals("k", UnicodeCaseFolder.fold("K")); // kelvin
        assertEquals("å", UnicodeCaseFolder.fold("Å")); // angstrom
        assertEquals("μ", UnicodeCaseFolder.fold("µ")); // micro sign to greek small mu

        assertTrue(UnicodeCaseFolder.equalsIgnoreCase("paradiſe", "PARADISE"));
        assertTrue(UnicodeCaseFolder.equalsIgnoreCase("100K", "100k"));
        assertTrue(UnicodeCaseFolder.equalsIgnoreCase("100µs", "100μs"));
    }

    @Test
    @DisplayName("containsIgnoreCase works accurately across complex Unicode foldings")
    void testContainsIgnoreCase() {
        String doc = "Das ist eine große Chance im Frühling!";
        assertTrue(UnicodeCaseFolder.containsIgnoreCase(doc, "GROSS"));
        assertTrue(UnicodeCaseFolder.containsIgnoreCase(doc, "CHANCE"));
        assertTrue(UnicodeCaseFolder.containsIgnoreCase(doc, "frühling"));

        String textWithLigature = "The oﬃce is closed for holidays.";
        assertTrue(UnicodeCaseFolder.containsIgnoreCase(textWithLigature, "OFFICE"));
        assertTrue(UnicodeCaseFolder.containsIgnoreCase(textWithLigature, "closed"));
        assertFalse(UnicodeCaseFolder.containsIgnoreCase(textWithLigature, "banana"));
    }

    @Test
    @DisplayName("startsWithIgnoreCase and endsWithIgnoreCase validations")
    void testStartsAndEndsWith() {
        assertTrue(UnicodeCaseFolder.startsWithIgnoreCase("Großartig", "gross"));
        assertTrue(UnicodeCaseFolder.endsWithIgnoreCase("Flughafen oﬃce", "OFFICE"));
        assertFalse(UnicodeCaseFolder.startsWithIgnoreCase("hello", "world"));
    }

    @Test
    @DisplayName("ASCII fast-path behaves identically to full folding")
    void testAsciiPath() {
        assertEquals("hello world 123!", UnicodeCaseFolder.fold("Hello World 123!"));
        assertTrue(UnicodeCaseFolder.equalsIgnoreCase("Java_Standard_21", "java_standard_21"));
        assertFalse(UnicodeCaseFolder.equalsIgnoreCase("apple", "banana"));
    }

    @Test
    @DisplayName("Null and empty string edge cases")
    void testNullAndEmpty() {
        assertNull(UnicodeCaseFolder.fold(null));
        assertEquals("", UnicodeCaseFolder.fold(""));

        assertTrue(UnicodeCaseFolder.equalsIgnoreCase(null, null));
        assertFalse(UnicodeCaseFolder.equalsIgnoreCase("abc", null));
        assertFalse(UnicodeCaseFolder.equalsIgnoreCase(null, "abc"));

        assertFalse(UnicodeCaseFolder.containsIgnoreCase(null, "test"));
        assertFalse(UnicodeCaseFolder.containsIgnoreCase("test", null));
        assertTrue(UnicodeCaseFolder.containsIgnoreCase("test", ""));
    }
}
