package com.msp.josa;

/**
 * Detects final consonant (종성 / 받침) and special 'ㄹ' consonant traits from Korean words,
 * numbers, and English terminal sounds.
 */
public final class KoreanJongseongDetector {

    private static final int HANGUL_BASE = 0xAC00;
    private static final int HANGUL_LAST = 0xD7A3;
    private static final int RIEUL_JONGSEONG_INDEX = 8; // ㄹ 받침

    private KoreanJongseongDetector() {
    }

    /**
     * Trait record for the terminal sound of a word.
     *
     * @param hasJongseong whether the terminal syllable has a final consonant (받침)
     * @param isRieul      whether the final consonant is specifically 'ㄹ'
     */
    public record SoundTrait(boolean hasJongseong, boolean isRieul) {
        public static final SoundTrait NONE = new SoundTrait(false, false);
        public static final SoundTrait GENERAL_JONGSEONG = new SoundTrait(true, false);
        public static final SoundTrait RIEUL_JONGSEONG = new SoundTrait(true, true);
    }

    /**
     * Analyzes the phonetic end sound of a given word.
     *
     * @param word input string
     * @return {@link SoundTrait} containing whether it has 받침 and whether it's 'ㄹ'
     */
    public static SoundTrait detect(String word) {
        if (word == null || word.isBlank()) {
            return SoundTrait.NONE;
        }

        // Scan backwards to find the last significant character (skipping punctuation/whitespace)
        char lastChar = 0;
        for (int i = word.length() - 1; i >= 0; i--) {
            char c = word.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                lastChar = c;
                break;
            }
        }

        if (lastChar == 0) {
            return SoundTrait.NONE;
        }

        // 1. Hangul syllable
        if (lastChar >= HANGUL_BASE && lastChar <= HANGUL_LAST) {
            int jongseongIndex = (lastChar - HANGUL_BASE) % 28;
            if (jongseongIndex == 0) {
                return SoundTrait.NONE;
            } else if (jongseongIndex == RIEUL_JONGSEONG_INDEX) {
                return SoundTrait.RIEUL_JONGSEONG;
            } else {
                return SoundTrait.GENERAL_JONGSEONG;
            }
        }

        // 2. Numbers
        if (Character.isDigit(lastChar)) {
            return switch (lastChar) {
                case '1', '7', '8' -> SoundTrait.RIEUL_JONGSEONG; // 일, 칠, 팔 (ㄹ 받침)
                case '0', '3', '6' -> SoundTrait.GENERAL_JONGSEONG; // 영(ㅇ), 삼(ㅁ), 육(ㄱ)
                default -> SoundTrait.NONE; // 2(이), 4(사), 5(오), 9(구)
            };
        }

        // 3. English alphabet (phonetic ending sound in Korean)
        if ((lastChar >= 'a' && lastChar <= 'z') || (lastChar >= 'A' && lastChar <= 'Z')) {
            char upper = Character.toUpperCase(lastChar);
            return switch (upper) {
                case 'L', 'R' -> SoundTrait.RIEUL_JONGSEONG; // 엘, 알 (ㄹ 받침)
                case 'M', 'N' -> SoundTrait.GENERAL_JONGSEONG; // 엠, 엔 (ㅁ, ㄴ 받침)
                default -> SoundTrait.NONE;
            };
        }

        return SoundTrait.NONE;
    }
}
