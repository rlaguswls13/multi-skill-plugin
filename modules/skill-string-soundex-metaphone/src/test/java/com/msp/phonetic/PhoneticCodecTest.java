package com.msp.phonetic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneticCodecTest {

    @Test
    @DisplayName("Soundex standard test cases")
    void testSoundexKnownWords() {
        assertEquals("S530", PhoneticCodec.soundex("Smith"));
        assertEquals("S530", PhoneticCodec.soundex("Smythe"));
        assertTrue(PhoneticCodec.isSoundexEqual("Smith", "Smythe"));

        assertEquals("R163", PhoneticCodec.soundex("Robert"));
        assertEquals("R163", PhoneticCodec.soundex("Rupert"));
        assertTrue(PhoneticCodec.isSoundexEqual("Robert", "Rupert"));

        assertEquals("P236", PhoneticCodec.soundex("Pfister"));
        assertEquals("A261", PhoneticCodec.soundex("Ashcraft"));
        assertEquals("T522", PhoneticCodec.soundex("Tymczak"));
        assertEquals("H555", PhoneticCodec.soundex("Honeyman"));
    }

    @Test
    @DisplayName("Soundex handles empty, null, and non-alpha inputs")
    void testSoundexEdgeCases() {
        assertEquals("", PhoneticCodec.soundex(null));
        assertEquals("", PhoneticCodec.soundex(""));
        assertEquals("", PhoneticCodec.soundex("12345!@#$"));
        assertFalse(PhoneticCodec.isSoundexEqual("", ""));
        assertFalse(PhoneticCodec.isSoundexEqual(null, "Smith"));
    }

    @Test
    @DisplayName("Metaphone standard test cases")
    void testMetaphoneKnownWords() {
        // Silent K
        assertEquals("NT", PhoneticCodec.metaphone("Knight"));
        assertEquals("NT", PhoneticCodec.metaphone("Night"));
        assertTrue(PhoneticCodec.isMetaphoneEqual("Knight", "Night"));

        // PH -> F
        assertEquals("FN", PhoneticCodec.metaphone("Phone"));

        // TH -> 0
        assertEquals("SM0", PhoneticCodec.metaphone("Smith"));

        // DGE -> J
        assertEquals("EJ", PhoneticCodec.metaphone("Edge"));

        // CH -> X, SCH -> SK
        assertEquals("XNJ", PhoneticCodec.metaphone("Change"));
        assertEquals("SKL", PhoneticCodec.metaphone("School"));

        // Silent B after M at end of word
        assertEquals("KLM", PhoneticCodec.metaphone("Climb"));
        assertEquals("TM", PhoneticCodec.metaphone("Dumb"));
    }

    @Test
    @DisplayName("Metaphone handles empty, null, and non-alpha inputs")
    void testMetaphoneEdgeCases() {
        assertEquals("", PhoneticCodec.metaphone(null));
        assertEquals("", PhoneticCodec.metaphone(""));
        assertEquals("", PhoneticCodec.metaphone("!@#123"));
        assertFalse(PhoneticCodec.isMetaphoneEqual("", ""));
        assertFalse(PhoneticCodec.isMetaphoneEqual(null, "Knight"));
    }
}
