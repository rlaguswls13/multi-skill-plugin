package com.msp.phonetic;

import java.util.Locale;
import java.util.Objects;

/**
 * Phonetic algorithms codec implementing Soundex and Metaphone algorithms.
 * Used for sound-alike matching, name deduplication, and spelling tolerance in pure Java 21.
 */
public final class PhoneticCodec {

    private static final String SOUNDEX_MAPPING = "01230120022455012623010202";
    //                                             ABCDEFGHIJKLMNOPQRSTUVWXYZ

    private PhoneticCodec() {
        // Utility class
    }

    // =========================================================================
    // Soundex
    // =========================================================================

    /**
     * Computes the 4-character Soundex code for the given text.
     * Follows the US Census Odell &amp; Russell Soundex rule.
     *
     * @param text input string (e.g. "Smith", "Pfister")
     * @return 4-character Soundex code (e.g. "S530", "P236"), or empty string if no valid letters
     */
    public static String soundex(String text) {
        if (text == null) {
            return "";
        }
        String clean = cleanAlphabet(text);
        if (clean.isEmpty()) {
            return "";
        }

        StringBuilder code = new StringBuilder();
        char firstChar = clean.charAt(0);
        code.append(firstChar);

        char lastDigit = getSoundexDigit(firstChar);

        for (int i = 1; i < clean.length() && code.length() < 4; i++) {
            char c = clean.charAt(i);
            char digit = getSoundexDigit(c);

            if (c == 'H' || c == 'W') {
                // H and W are ignored completely and do not separate adjacent same-coded letters
                continue;
            }

            if (digit != '0') {
                if (digit != lastDigit) {
                    code.append(digit);
                }
            }
            lastDigit = digit;
        }

        // Pad with '0' to ensure 4 characters
        while (code.length() < 4) {
            code.append('0');
        }

        return code.toString();
    }

    /**
     * Checks if two strings produce the exact same Soundex code.
     *
     * @param s1 first string
     * @param s2 second string
     * @return true if soundex codes match and are non-empty
     */
    public static boolean isSoundexEqual(String s1, String s2) {
        String c1 = soundex(s1);
        String c2 = soundex(s2);
        return !c1.isEmpty() && c1.equals(c2);
    }

    private static char getSoundexDigit(char c) {
        if (c >= 'A' && c <= 'Z') {
            return SOUNDEX_MAPPING.charAt(c - 'A');
        }
        return '0';
    }

    // =========================================================================
    // Metaphone
    // =========================================================================

    /**
     * Computes variable-length Metaphone phonetic string for the given text.
     * Implements Lawrence Philips' original 1990 Metaphone algorithm.
     *
     * @param text input string (e.g. "Knight", "Smith", "Phone")
     * @return Metaphone phonetic string (e.g. "NT", "SM0", "FN")
     */
    public static String metaphone(String text) {
        if (text == null) {
            return "";
        }
        String in = cleanAlphabet(text);
        int len = in.length();
        if (len == 0) {
            return "";
        }

        // Initial transformations
        if (in.startsWith("KN") || in.startsWith("GN") || in.startsWith("PN") || in.startsWith("AE") || in.startsWith("WR")) {
            in = in.substring(1);
            len = in.length();
        } else if (in.startsWith("X")) {
            in = "S" + in.substring(1);
        } else if (in.startsWith("WH")) {
            in = "W" + in.substring(2);
            len = in.length();
        }

        StringBuilder out = new StringBuilder();
        int i = 0;

        // Vowel at start is retained
        if (isVowel(in.charAt(0))) {
            out.append(in.charAt(0));
            i = 1;
        }

        while (i < len) {
            char curr = in.charAt(i);

            // Skip consecutive identical letters except 'C'
            if (curr != 'C' && i > 0 && curr == in.charAt(i - 1)) {
                i++;
                continue;
            }

            switch (curr) {
                case 'B' -> {
                    // Silent B after M at end of word
                    if (i == len - 1 && i > 0 && in.charAt(i - 1) == 'M') {
                        // silent
                    } else {
                        out.append('B');
                    }
                }
                case 'C' -> {
                    if (i > 0 && in.charAt(i - 1) == 'S' && isNext(in, i, 'I', 'E', 'Y')) {
                        // silent C in -SCI-, -SCE-, -SCY-
                    } else if (hasPrefix(in, i, "CIA")) {
                        out.append('X');
                        i += 2;
                    } else if (hasPrefix(in, i, "CH")) {
                        if (i > 0 && in.charAt(i - 1) == 'S') {
                            out.append('K');
                        } else {
                            out.append('X');
                        }
                        i++;
                    } else if (isNext(in, i, 'I', 'E', 'Y')) {
                        out.append('S');
                    } else {
                        out.append('K');
                    }
                }
                case 'D' -> {
                    if (hasPrefix(in, i + 1, "GE") || hasPrefix(in, i + 1, "GI") || hasPrefix(in, i + 1, "GY")) {
                        out.append('J');
                        i += 2;
                    } else {
                        out.append('T');
                    }
                }
                case 'G' -> {
                    if (hasPrefix(in, i, "GH") && (i + 2 == len || !isVowel(in.charAt(i + 2)))) {
                        // Silent GH
                        i++;
                    } else if (hasPrefix(in, i, "GN") || hasPrefix(in, i, "GNED")) {
                        // Silent G in -GN- or -GNED
                    } else if (isNext(in, i, 'I', 'E', 'Y')) {
                        out.append('J');
                    } else {
                        out.append('K');
                    }
                }
                case 'H' -> {
                    if (isVowelNext(in, i)) {
                        if (i == 0 || !isOneOf(in.charAt(i - 1), 'C', 'S', 'P', 'T', 'G')) {
                            out.append('H');
                        }
                    }
                }
                case 'F', 'J', 'L', 'M', 'N', 'R' -> out.append(curr);
                case 'K' -> {
                    if (i == 0 || in.charAt(i - 1) != 'C') {
                        out.append('K');
                    }
                }
                case 'P' -> {
                    if (hasPrefix(in, i, "PH")) {
                        out.append('F');
                        i++;
                    } else {
                        out.append('P');
                    }
                }
                case 'Q' -> out.append('K');
                case 'S' -> {
                    if (hasPrefix(in, i, "SH") || hasPrefix(in, i, "SIO") || hasPrefix(in, i, "SIA")) {
                        out.append('X');
                        if (hasPrefix(in, i, "SH")) {
                            i++;
                        } else {
                            i += 2;
                        }
                    } else {
                        out.append('S');
                    }
                }
                case 'T' -> {
                    if (hasPrefix(in, i, "TIA") || hasPrefix(in, i, "TIO")) {
                        out.append('X');
                        i += 2;
                    } else if (hasPrefix(in, i, "TH")) {
                        out.append('0');
                        i++;
                    } else if (hasPrefix(in, i, "TCH")) {
                        // Silent T in -TCH-
                    } else {
                        out.append('T');
                    }
                }
                case 'V' -> out.append('F');
                case 'W', 'Y' -> {
                    if (isVowelNext(in, i)) {
                        out.append(curr);
                    }
                }
                case 'X' -> {
                    out.append("KS");
                }
                case 'Z' -> out.append('S');
                default -> {
                    // Other characters, vowels inside word are ignored
                }
            }
            i++;
        }

        return out.toString();
    }

    /**
     * Checks if two strings produce the exact same Metaphone code.
     *
     * @param s1 first string
     * @param s2 second string
     * @return true if metaphone codes match and are non-empty
     */
    public static boolean isMetaphoneEqual(String s1, String s2) {
        String c1 = metaphone(s1);
        String c2 = metaphone(s2);
        return !c1.isEmpty() && c1.equals(c2);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String cleanAlphabet(String s) {
        StringBuilder sb = new StringBuilder();
        String upper = s.toUpperCase(Locale.ROOT);
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isVowel(char c) {
        return c == 'A' || c == 'E' || c == 'I' || c == 'O' || c == 'U';
    }

    private static boolean isVowelNext(String s, int index) {
        return index + 1 < s.length() && isVowel(s.charAt(index + 1));
    }

    private static boolean isNext(String s, int index, char... chars) {
        if (index + 1 >= s.length()) {
            return false;
        }
        char next = s.charAt(index + 1);
        for (char c : chars) {
            if (next == c) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOneOf(char target, char... chars) {
        for (char c : chars) {
            if (target == c) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPrefix(String s, int offset, String prefix) {
        return s.startsWith(prefix, offset);
    }
}
