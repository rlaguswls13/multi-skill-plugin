package com.msp.morse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MorseCodecTest {

    @Test
    @DisplayName("Encode and decode international distress signal SOS")
    void testSos() {
        String encoded = MorseCodec.encode("SOS");
        assertEquals("... --- ...", encoded);

        String decoded = MorseCodec.decode(encoded);
        assertEquals("SOS", decoded);
    }

    @Test
    @DisplayName("Encode and decode standard English words with word separator")
    void testEnglishWords() {
        String text = "HELLO WORLD";
        String encoded = MorseCodec.encode(text);
        assertEquals(".... . .-.. .-.. --- / .-- --- .-. .-.. -..", encoded);

        String decoded = MorseCodec.decode(encoded);
        assertEquals("HELLO WORLD", decoded);
    }

    @Test
    @DisplayName("Encode and decode numbers and common punctuation")
    void testNumbersAndPunctuation() {
        String text = "2026, JAVA!";
        String encoded = MorseCodec.encode(text);
        String decoded = MorseCodec.decode(encoded);
        assertEquals(text, decoded);
    }

    @Test
    @DisplayName("Encode and decode Korean simple syllables (가나다)")
    void testKoreanSimpleSyllables() {
        String text = "가나다";
        String encoded = MorseCodec.encode(text);
        // 가: ㄱ(.-..) + ㅏ(.) -> ".-.. ."
        // 나: ㄴ(..-.) + ㅏ(.) -> "..-. ."
        // 다: ㄷ(-...) + ㅏ(.) -> "-... ."
        assertEquals(".-.. . ..-. . -... .", encoded);

        String decoded = MorseCodec.decodeHangul(encoded);
        assertEquals(text, decoded);
    }

    @Test
    @DisplayName("Round-trip Korean words with batchim (한글)")
    void testKoreanBatchimRoundTrip() {
        String text = "한글 사랑";
        String encoded = MorseCodec.encode(text);
        String decoded = MorseCodec.decodeHangul(encoded);
        assertEquals(text, decoded);
    }

    @Test
    @DisplayName("Round-trip Korean syllables with double consonants (꼬마, 빠르다)")
    void testKoreanDoubleConsonants() {
        String text = "꼬마 빠르다";
        String encoded = MorseCodec.encode(text);
        String decoded = MorseCodec.decodeHangul(encoded);
        assertEquals(text, decoded);
    }

    @Test
    @DisplayName("Round-trip Korean syllables with composite vowels and complex batchim (과자, 닭, 값)")
    void testKoreanComplexVowelsAndBatchim() {
        String text = "과자 닭 값";
        String encoded = MorseCodec.encode(text);
        String decoded = MorseCodec.decodeHangul(encoded);
        assertEquals(text, decoded);
    }

    @Test
    @DisplayName("Encode and decode mixed Korean and numbers (자바 21)")
    void testKoreanAndDigits() {
        String text = "자바 21";
        String encoded = MorseCodec.encode(text);
        String decoded = MorseCodec.decodeHangul(encoded);
        assertEquals(text, decoded);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("Null or blank input returns empty string")
    void testEmptyInputs(String input) {
        assertEquals("", MorseCodec.encode(input));
        assertEquals("", MorseCodec.decode(input));
        assertEquals("", MorseCodec.decodeHangul(input));
    }
}
