package com.msp.casefold;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * High-performance Unicode Case Folding normalizer and comparator conforming to the Unicode Standard.
 * <p>
 * Implements full Unicode Case Folding rules:
 * <ul>
 *     <li>German sharp S ({@code 'ß'} / {@code 'ẞ'}) &rarr; {@code "ss"}</li>
 *     <li>Greek final sigma ({@code 'ς'} U+03C2) and capital sigma ({@code 'Σ'} U+03A3) &rarr; {@code 'σ'} (U+03C3)</li>
 *     <li>Latin long s ({@code 'ſ'} U+017F) &rarr; {@code 's'}</li>
 *     <li>Standard ligatures: {@code 'ﬀ'} &rarr; {@code "ff"}, {@code 'ﬁ'} &rarr; {@code "fi"}, {@code 'ﬂ'} &rarr; {@code "fl"}, {@code 'ﬃ'} &rarr; {@code "ffi"}, {@code 'ﬄ'} &rarr; {@code "ffl"}, {@code 'ﬅ'} &rarr; {@code "st"}, {@code 'ﬆ'} &rarr; {@code "st"}</li>
 *     <li>Micro sign ({@code 'µ'} U+00B5) &rarr; {@code 'μ'} (U+03BC)</li>
 *     <li>Angstrom ({@code 'Å'} U+212B) &rarr; {@code 'å'}, Kelvin ({@code 'K'} U+212A) &rarr; {@code 'k'}</li>
 * </ul>
 */
public final class UnicodeCaseFolder {

    private static final Map<Integer, String> EXPLICIT_FOLDS = new HashMap<>();

    static {
        // German Sharp S
        EXPLICIT_FOLDS.put(0x00DF, "ss"); // ß
        EXPLICIT_FOLDS.put(0x1E9E, "ss"); // ẞ

        // Greek Sigma
        EXPLICIT_FOLDS.put(0x03C2, "\u03C3"); // ς (final sigma) -> σ
        EXPLICIT_FOLDS.put(0x03A3, "\u03C3"); // Σ (capital sigma) -> σ
        EXPLICIT_FOLDS.put(0x03F2, "\u03C3"); // ϲ (lunate sigma) -> σ
        EXPLICIT_FOLDS.put(0x0390, "\u03B9\u0308\u0301"); // ΐ
        EXPLICIT_FOLDS.put(0x03B0, "\u03C5\u0308\u0301"); // ΰ

        // Latin special folds
        EXPLICIT_FOLDS.put(0x017F, "s");       // ſ (long s)
        EXPLICIT_FOLDS.put(0x1E9B, "\u1E61"); // ẛ (long s with dot above) -> ṡ
        EXPLICIT_FOLDS.put(0x00B5, "\u03BC"); // µ (micro sign) -> μ (greek small mu)
        EXPLICIT_FOLDS.put(0x212A, "k");       // K (kelvin sign) -> k
        EXPLICIT_FOLDS.put(0x212B, "\u00E5"); // Å (angstrom) -> å
        EXPLICIT_FOLDS.put(0x0149, "\u02BCn"); // ŉ

        // Standard Ligatures
        EXPLICIT_FOLDS.put(0xFB00, "ff");  // ﬀ
        EXPLICIT_FOLDS.put(0xFB01, "fi");  // ﬁ
        EXPLICIT_FOLDS.put(0xFB02, "fl");  // ﬂ
        EXPLICIT_FOLDS.put(0xFB03, "ffi"); // ﬃ
        EXPLICIT_FOLDS.put(0xFB04, "ffl"); // ﬄ
        EXPLICIT_FOLDS.put(0xFB05, "st");  // ﬅ
        EXPLICIT_FOLDS.put(0xFB06, "st");  // ﬆ
        EXPLICIT_FOLDS.put(0x0587, "\u0565\u0582"); // Armenian ligature և -> եւ
        EXPLICIT_FOLDS.put(0xFB13, "\u0574\u0576"); // Armenian ligature ﬓ -> մն
    }

    private UnicodeCaseFolder() {
        // utility class
    }

    /**
     * Folds the input text according to Unicode Full Case Folding rules.
     *
     * @param text input character sequence, may be null
     * @return case-folded string, or null if input was null
     */
    public static String fold(CharSequence text) {
        if (text == null) {
            return null;
        }

        int len = text.length();
        if (len == 0) {
            return "";
        }

        // Fast-path: check if ASCII only
        boolean isAscii = true;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c > 127) {
                isAscii = false;
                break;
            }
        }

        if (isAscii) {
            char[] result = new char[len];
            for (int i = 0; i < len; i++) {
                char c = text.charAt(i);
                result[i] = (c >= 'A' && c <= 'Z') ? (char) (c + 32) : c;
            }
            return new String(result);
        }

        // Full Unicode Case Folding path
        StringBuilder sb = new StringBuilder(len + 8);
        int i = 0;
        while (i < len) {
            int codePoint = Character.codePointAt(text, i);
            i += Character.charCount(codePoint);

            String explicitFold = EXPLICIT_FOLDS.get(codePoint);
            if (explicitFold != null) {
                sb.append(explicitFold);
            } else {
                // Apply standard full root casing: uppercase then lowercase
                String s = Character.toString(codePoint);
                String cased = s.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);

                // Ensure final sigma 'ς' (U+03C2) inside expanded sequences is canonicalized to 'σ' (U+03C3)
                if (cased.indexOf('\u03C2') >= 0) {
                    cased = cased.replace('\u03C2', '\u03C3');
                }
                sb.append(cased);
            }
        }

        return sb.toString();
    }

    /**
     * Compares two character sequences for equality using Unicode Case Folding.
     *
     * @param s1 first sequence
     * @param s2 second sequence
     * @return true if equal under Unicode Case Folding rules
     */
    public static boolean equalsIgnoreCase(CharSequence s1, CharSequence s2) {
        if (s1 == s2) {
            return true;
        }
        if (s1 == null || s2 == null) {
            return false;
        }

        // Fast length / ASCII comparison check
        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == len2) {
            boolean allAscii = true;
            boolean asciiMatches = true;
            for (int i = 0; i < len1; i++) {
                char c1 = s1.charAt(i);
                char c2 = s2.charAt(i);
                if (c1 > 127 || c2 > 127) {
                    allAscii = false;
                    break;
                }
                char lc1 = (c1 >= 'A' && c1 <= 'Z') ? (char) (c1 + 32) : c1;
                char lc2 = (c2 >= 'A' && c2 <= 'Z') ? (char) (c2 + 32) : c2;
                if (lc1 != lc2) {
                    asciiMatches = false;
                    break;
                }
            }
            if (allAscii) {
                return asciiMatches;
            }
        }

        String f1 = fold(s1);
        String f2 = fold(s2);
        return Objects.equals(f1, f2);
    }

    /**
     * Checks if {@code text} contains {@code search} under Unicode Case Folding rules.
     *
     * @param text   the full text to search within
     * @param search the sequence to find
     * @return true if search is found inside text
     */
    public static boolean containsIgnoreCase(CharSequence text, CharSequence search) {
        if (text == null || search == null) {
            return false;
        }
        if (search.isEmpty()) {
            return true;
        }

        String foldedText = fold(text);
        String foldedSearch = fold(search);
        return foldedText.contains(foldedSearch);
    }

    /**
     * Checks if {@code text} starts with {@code prefix} under Unicode Case Folding rules.
     */
    public static boolean startsWithIgnoreCase(CharSequence text, CharSequence prefix) {
        if (text == null || prefix == null) {
            return false;
        }
        return fold(text).startsWith(fold(prefix));
    }

    /**
     * Checks if {@code text} ends with {@code suffix} under Unicode Case Folding rules.
     */
    public static boolean endsWithIgnoreCase(CharSequence text, CharSequence suffix) {
        if (text == null || suffix == null) {
            return false;
        }
        return fold(text).endsWith(fold(suffix));
    }
}
