package com.msp.hangul;

/**
 * Immutable record representing a decomposed Korean Hangul syllable.
 *
 * @param original  original character
 * @param choseong  initial consonant (초성, e.g. 'ㄱ')
 * @param jungseong medial vowel (중성, e.g. 'ㅏ')
 * @param jongseong final consonant (종성, e.g. 'ㅇ' or '\0' if absent)
 * @param isHangul  whether original character is a valid composite Hangul syllable
 */
public record HangulSyllable(
        char original,
        char choseong,
        char jungseong,
        char jongseong,
        boolean isHangul
) {
    /**
     * Checks if this syllable has a final consonant (받침).
     */
    public boolean hasJongseong() {
        return jongseong != '\0';
    }

    /**
     * Creates a non-Hangul fallback syllable representation.
     */
    public static HangulSyllable nonHangul(char c) {
        return new HangulSyllable(c, c, '\0', '\0', false);
    }
}
