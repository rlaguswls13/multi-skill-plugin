package com.msp.romanizer;

import java.util.Locale;

/**
 * Korean Hangul to Revised Romanization converter and URL Slug generator.
 * Uses official National Institute of Korean Language Revised Romanization rules without external dependencies.
 */
public final class KoreanRomanizer {

    private static final int HANGUL_BASE = 0xAC00;
    private static final int HANGUL_LAST = 0xD7A3;

    // 19 Initial Consonants (초성)
    private static final String[] CHO = {
            "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s",
            "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
    };

    // 21 Vowels (중성)
    private static final String[] JUNG = {
            "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa",
            "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
    };

    // 28 Final Consonants (종성)
    private static final String[] JONG = {
            "", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lg",
            "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "bs", "s",
            "ss", "ng", "t", "t", "k", "t", "p", "h"
    };

    private KoreanRomanizer() {}

    /**
     * Converts Korean Hangul syllables in the text to their Revised Romanization equivalent.
     * Non-Hangul characters are preserved.
     */
    public static String romanize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= HANGUL_BASE && ch <= HANGUL_LAST) {
                int code = ch - HANGUL_BASE;
                int jongIndex = code % 28;
                int jungIndex = (code / 28) % 21;
                int choIndex = (code / 28) / 21;

                sb.append(CHO[choIndex]);
                sb.append(JUNG[jungIndex]);
                sb.append(JONG[jongIndex]);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Converts any text containing Korean and English into a clean, URL-safe slug.
     * Example: "안녕하세요 World! 2026" -> "annyeonghaseyo-world-2026"
     */
    public static String toSlug(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String romanized = romanize(text);
        String lower = romanized.toLowerCase(Locale.ROOT);

        // Replace non-alphanumeric chars with hyphens
        String slug = lower.replaceAll("[^a-z0-9]+", "-");

        // Trim leading and trailing hyphens
        slug = slug.replaceAll("^-+|-+$", "");

        return slug;
    }
}
