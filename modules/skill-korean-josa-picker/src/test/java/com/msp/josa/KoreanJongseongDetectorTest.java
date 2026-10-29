package com.msp.josa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KoreanJongseongDetector Tests")
class KoreanJongseongDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {"사과", "바다", "나무", "자동차", "소", "비"})
    @DisplayName("Words without jongseong (받침 없음)")
    void testWithoutJongseong(String word) {
        KoreanJongseongDetector.SoundTrait trait = KoreanJongseongDetector.detect(word);
        assertFalse(trait.hasJongseong(), () -> "Expected no jongseong for: " + word);
        assertFalse(trait.isRieul());
    }

    @ParameterizedTest
    @ValueSource(strings = {"집", "밥", "선생님", "책", "꽃", "음악", "한국"})
    @DisplayName("Words with general jongseong (일반 받침)")
    void testWithGeneralJongseong(String word) {
        KoreanJongseongDetector.SoundTrait trait = KoreanJongseongDetector.detect(word);
        assertTrue(trait.hasJongseong(), () -> "Expected jongseong for: " + word);
        assertFalse(trait.isRieul(), () -> "Expected not rieul for: " + word);
    }

    @ParameterizedTest
    @ValueSource(strings = {"서울", "칼", "길", "물", "달", "별", "마을"})
    @DisplayName("Words ending in Rieul jongseong (ㄹ 받침)")
    void testWithRieulJongseong(String word) {
        KoreanJongseongDetector.SoundTrait trait = KoreanJongseongDetector.detect(word);
        assertTrue(trait.hasJongseong(), () -> "Expected jongseong for: " + word);
        assertTrue(trait.isRieul(), () -> "Expected rieul for: " + word);
    }

    @Test
    @DisplayName("Digit ending sounds")
    void testDigitEndingSounds() {
        // 1, 7, 8 end in 'ㄹ' (일, 칠, 팔)
        assertTrue(KoreanJongseongDetector.detect("1").isRieul());
        assertTrue(KoreanJongseongDetector.detect("7").isRieul());
        assertTrue(KoreanJongseongDetector.detect("8").isRieul());

        // 0, 3, 6 end in general 받침 (영, 삼, 육)
        KoreanJongseongDetector.SoundTrait zero = KoreanJongseongDetector.detect("0");
        assertTrue(zero.hasJongseong());
        assertFalse(zero.isRieul());

        KoreanJongseongDetector.SoundTrait three = KoreanJongseongDetector.detect("3");
        assertTrue(three.hasJongseong());
        assertFalse(three.isRieul());

        // 2, 4, 5, 9 have no 받침 (이, 사, 오, 구)
        assertFalse(KoreanJongseongDetector.detect("2").hasJongseong());
        assertFalse(KoreanJongseongDetector.detect("4").hasJongseong());
        assertFalse(KoreanJongseongDetector.detect("5").hasJongseong());
        assertFalse(KoreanJongseongDetector.detect("9").hasJongseong());
    }

    @Test
    @DisplayName("English alphabet ending sounds")
    void testAlphabetEndingSounds() {
        // L, R end in 'ㄹ' (엘, 알)
        assertTrue(KoreanJongseongDetector.detect("URL").isRieul());
        assertTrue(KoreanJongseongDetector.detect("Color").isRieul());

        // M, N end in general 받침 (엠, 엔)
        KoreanJongseongDetector.SoundTrait ram = KoreanJongseongDetector.detect("RAM");
        assertTrue(ram.hasJongseong());
        assertFalse(ram.isRieul());

        // Others (A, K, P, T, etc.)
        assertFalse(KoreanJongseongDetector.detect("API").hasJongseong());
        assertFalse(KoreanJongseongDetector.detect("SDK").hasJongseong());
    }

    @Test
    @DisplayName("Trailing punctuation should be skipped")
    void testPunctuationHandling() {
        KoreanJongseongDetector.SoundTrait trait = KoreanJongseongDetector.detect("사과!)");
        assertFalse(trait.hasJongseong());

        KoreanJongseongDetector.SoundTrait seoulTrait = KoreanJongseongDetector.detect("서울...");
        assertTrue(seoulTrait.isRieul());
    }
}
