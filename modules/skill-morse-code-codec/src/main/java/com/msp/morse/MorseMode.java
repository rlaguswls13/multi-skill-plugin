package com.msp.morse;

/**
 * Morse decoding mode indicating whether letter codes represent
 * standard International Morse alphabet or Korean telegraph (Hangul) Jamo.
 */
public enum MorseMode {
    /**
     * Standard International Morse Code (A-Z, 0-9, punctuation).
     */
    INTERNATIONAL,

    /**
     * Korean Telegraphic Morse Code (Hangul Jamo assembled into syllables).
     */
    KOREAN
}
