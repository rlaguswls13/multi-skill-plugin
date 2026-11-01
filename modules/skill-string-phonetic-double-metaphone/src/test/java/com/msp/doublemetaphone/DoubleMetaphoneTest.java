package com.msp.doublemetaphone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DoubleMetaphoneTest {

    @Test
    @DisplayName("Canonical canonical test cases match expected Double Metaphone codes")
    void testCanonicalCases() {
        // "Smith" -> primary "SM0", secondary "XMT"
        DoubleMetaphoneResult smith = DoubleMetaphone.encode("Smith");
        assertEquals("SM0", smith.getPrimary());
        assertEquals("XMT", smith.getSecondary());

        // "Schmidt" -> primary "XMT", secondary "SMT"
        DoubleMetaphoneResult schmidt = DoubleMetaphone.encode("Schmidt");
        assertEquals("XMT", schmidt.getPrimary());
        assertEquals("SMT", schmidt.getSecondary());

        // "Knight" -> "NT"
        DoubleMetaphoneResult knight = DoubleMetaphone.encode("Knight");
        assertEquals("NT", knight.getPrimary());

        // "Wrestle" -> "RSL"
        DoubleMetaphoneResult wrestle = DoubleMetaphone.encode("Wrestle");
        assertEquals("RSL", wrestle.getPrimary());

        // "Alexander" -> "ALKS"
        DoubleMetaphoneResult alexander = DoubleMetaphone.encode("Alexander");
        assertEquals("ALKS", alexander.getPrimary());
    }

    @Test
    @DisplayName("Phonetic similarity matches similar names and homophones")
    void testPhoneticSimilarity() {
        // "Smith" and "Schmidt" match via secondary key "SMT"
        assertTrue(DoubleMetaphone.isSimilar("Smith", "Schmidt"));
        assertTrue(DoubleMetaphone.isSimilar("Smith", "Smyth"));

        // "Knight" and "Night" have identical phonetic codes
        assertTrue(DoubleMetaphone.isSimilar("Knight", "Night"));

        // "Catherine" and "Katherine"
        assertTrue(DoubleMetaphone.isSimilar("Catherine", "Katherine"));

        // "Stephen" and "Steven"
        assertTrue(DoubleMetaphone.isSimilar("Stephen", "Steven"));

        // Completely dissimilar words
        assertFalse(DoubleMetaphone.isSimilar("Apple", "Orange"));
        assertFalse(DoubleMetaphone.isSimilar("Smith", "Johnson"));
    }

    @Test
    @DisplayName("Alternate match control works as expected")
    void testAlternateMatch() {
        // Smith (primary SM0, secondary SMT) and Schmidt (primary XMT, secondary SMT)
        // With alternate = false: SM0 != XMT -> false
        // With alternate = true: SMT == SMT -> true
        assertFalse(DoubleMetaphone.isSimilar("Smith", "Schmidt", false));
        assertTrue(DoubleMetaphone.isSimilar("Smith", "Schmidt", true));
    }

    @Test
    @DisplayName("Supports configurable max code length")
    void testMaxCodeLength() {
        DoubleMetaphoneResult shortCode = DoubleMetaphone.encode("Alexander", 4);
        assertEquals("ALKS", shortCode.getPrimary());

        DoubleMetaphoneResult longCode = DoubleMetaphone.encode("Alexander", 8);
        assertTrue(longCode.getPrimary().startsWith("ALKS"));
        assertTrue(longCode.getPrimary().length() > 4);
    }

    @Test
    @DisplayName("Handles silent initial letter combinations")
    void testSilentStartingCombinations() {
        assertEquals(DoubleMetaphone.encode("Gnome").getPrimary(), DoubleMetaphone.encode("Nome").getPrimary());
        assertEquals(DoubleMetaphone.encode("Pneumonia").getPrimary(), DoubleMetaphone.encode("Neumonia").getPrimary());
        assertEquals(DoubleMetaphone.encode("Psychic").getPrimary(), DoubleMetaphone.encode("Sychic").getPrimary());
    }

    @Test
    @DisplayName("Handles null, empty, and punctuated inputs safely")
    void testEdgeCases() {
        assertEquals("", DoubleMetaphone.encode(null).getPrimary());
        assertEquals("", DoubleMetaphone.encode("").getPrimary());
        assertEquals("", DoubleMetaphone.encode("   ").getPrimary());
        assertEquals("", DoubleMetaphone.encode("12345").getPrimary());

        assertFalse(DoubleMetaphone.isSimilar(null, "Smith"));
        assertFalse(DoubleMetaphone.isSimilar("Smith", null));

        // Punctuated name
        assertEquals("SM0", DoubleMetaphone.encode("Smith!").getPrimary());
        assertEquals("SM0J", DoubleMetaphone.encode("Smith-Jones").getPrimary());
    }
}
