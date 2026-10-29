package com.msp.hangul;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * High-performance Korean Hangul syllable decomposer.
 * Breaks down composite syllables (U+AC00 ~ U+D7A3) into Choseong (초성),
 * Jungseong (중성), and Jongseong (종성).
 */
public final class HangulDecomposer {

    public static final char HANGUL_BASE = 0xAC00; // '가'
    public static final char HANGUL_END = 0xD7A3;  // '힣'

    public static final int CHOSEONG_COUNT = 19;
    public static final int JUNGSEONG_COUNT = 21;
    public static final int JONGSEONG_COUNT = 28;

    /**
     * Standard 19 Korean initial consonants (초성).
     */
    public static final char[] CHOSEONG = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    /**
     * Standard 21 Korean medial vowels (중성).
     */
    public static final char[] JUNGSEONG = {
            'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
            'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
    };

    /**
     * Standard 28 Korean final consonants (종성). Index 0 is '\0' representing no final consonant.
     */
    public static final char[] JONGSEONG = {
            '\0', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ',
            'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    private static final boolean[] CHOSEONG_LOOKUP = new boolean[0x318F - 0x3130 + 1];

    static {
        for (char c : CHOSEONG) {
            if (c >= 0x3130 && c <= 0x318F) {
                CHOSEONG_LOOKUP[c - 0x3130] = true;
            }
        }
    }

    private HangulDecomposer() {
    }

    /**
     * Returns true if the character is within the composite Hangul syllable block ('가' ~ '힣').
     */
    public static boolean isHangulSyllable(char c) {
        return c >= HANGUL_BASE && c <= HANGUL_END;
    }

    /**
     * Returns true if the character is one of the 19 valid Korean initial consonants (초성).
     */
    public static boolean isChoseong(char c) {
        if (c >= 0x3130 && c <= 0x318F) {
            return CHOSEONG_LOOKUP[c - 0x3130];
        }
        return false;
    }

    /**
     * Decomposes a single character. If not a composite Hangul syllable, returns non-Hangul representation.
     */
    public static HangulSyllable decompose(char c) {
        if (!isHangulSyllable(c)) {
            return HangulSyllable.nonHangul(c);
        }

        int syllableIndex = c - HANGUL_BASE;
        int choIndex = syllableIndex / (JUNGSEONG_COUNT * JONGSEONG_COUNT);
        int jungIndex = (syllableIndex % (JUNGSEONG_COUNT * JONGSEONG_COUNT)) / JONGSEONG_COUNT;
        int jongIndex = syllableIndex % JONGSEONG_COUNT;

        return new HangulSyllable(
                c,
                CHOSEONG[choIndex],
                JUNGSEONG[jungIndex],
                JONGSEONG[jongIndex],
                true
        );
    }

    /**
     * Decomposes an entire string into a list of {@link HangulSyllable}s.
     */
    public static List<HangulSyllable> decompose(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<HangulSyllable> result = new ArrayList<>(text.length());
        for (int i = 0; i < text.length(); i++) {
            result.add(decompose(text.charAt(i)));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Extracts choseong (초성) string from the input text.
     * Hangul syllables are replaced by their initial consonants, while non-Hangul characters are preserved.
     */
    public static String extractChoseong(String text) {
        if (text == null) {
            return null;
        }
        if (text.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isHangulSyllable(c)) {
                int choIndex = (c - HANGUL_BASE) / (JUNGSEONG_COUNT * JONGSEONG_COUNT);
                sb.append(CHOSEONG[choIndex]);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
