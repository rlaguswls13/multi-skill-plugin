package com.msp.morse;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper utility for decomposing Hangul syllables into elementary Morse Jamo
 * and assembling Jamo sequences back into valid Hangul Unicode syllables.
 */
final class HangulJamoHelper {

    private static final char HANGUL_BASE = 0xAC00;
    private static final char HANGUL_END = 0xD7A3;

    // Choseong (19)
    private static final String[] CHOSEONG = {
            "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅅ",
            "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    };

    // Jungseong (21)
    private static final String[] JUNGSEONG = {
            "ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ", "ㅖ", "ㅗ", "ㅘ",
            "ㅙ", "ㅚ", "ㅛ", "ㅜ", "ㅝ", "ㅞ", "ㅟ", "ㅠ", "ㅡ", "ㅢ", "ㅣ"
    };

    // Jongseong (28)
    private static final String[] JONGSEONG = {
            "", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ",
            "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ",
            "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    };

    private HangulJamoHelper() {
        // Utility
    }

    public static boolean isHangulSyllable(char c) {
        return c >= HANGUL_BASE && c <= HANGUL_END;
    }

    /**
     * Decomposes a Hangul syllable into elementary Jamo characters suitable for Morse encoding.
     */
    public static List<String> decompose(char c) {
        if (!isHangulSyllable(c)) {
            return List.of(String.valueOf(c));
        }

        int syllableIndex = c - HANGUL_BASE;
        int choIdx = syllableIndex / (21 * 28);
        int jungIdx = (syllableIndex % (21 * 28)) / 28;
        int jongIdx = syllableIndex % 28;

        List<String> result = new ArrayList<>();

        // Decompose Choseong
        decomposeConsonant(CHOSEONG[choIdx], result);

        // Decompose Jungseong
        decomposeVowel(JUNGSEONG[jungIdx], result);

        // Decompose Jongseong
        if (jongIdx > 0) {
            decomposeConsonant(JONGSEONG[jongIdx], result);
        }

        return result;
    }

    private static void decomposeConsonant(String con, List<String> out) {
        switch (con) {
            case "ㄲ" -> { out.add("ㄱ"); out.add("ㄱ"); }
            case "ㄸ" -> { out.add("ㄷ"); out.add("ㄷ"); }
            case "ㅃ" -> { out.add("ㅂ"); out.add("ㅂ"); }
            case "ㅆ" -> { out.add("ㅅ"); out.add("ㅅ"); }
            case "ㅉ" -> { out.add("ㅈ"); out.add("ㅈ"); }
            case "ㄳ" -> { out.add("ㄱ"); out.add("ㅅ"); }
            case "ㄵ" -> { out.add("ㄴ"); out.add("ㅈ"); }
            case "ㄶ" -> { out.add("ㄴ"); out.add("ㅎ"); }
            case "ㄺ" -> { out.add("ㄹ"); out.add("ㄱ"); }
            case "ㄻ" -> { out.add("ㄹ"); out.add("ㅁ"); }
            case "ㄼ" -> { out.add("ㄹ"); out.add("ㅂ"); }
            case "ㄽ" -> { out.add("ㄹ"); out.add("ㅅ"); }
            case "ㄾ" -> { out.add("ㄹ"); out.add("ㅌ"); }
            case "ㄿ" -> { out.add("ㄹ"); out.add("ㅍ"); }
            case "ㅀ" -> { out.add("ㄹ"); out.add("ㅎ"); }
            case "ㅄ" -> { out.add("ㅂ"); out.add("ㅅ"); }
            default -> out.add(con);
        }
    }

    private static void decomposeVowel(String vowel, List<String> out) {
        switch (vowel) {
            case "ㅒ" -> { out.add("ㅑ"); out.add("ㅣ"); }
            case "ㅖ" -> { out.add("ㅕ"); out.add("ㅣ"); }
            case "ㅘ" -> { out.add("ㅗ"); out.add("ㅏ"); }
            case "ㅙ" -> { out.add("ㅗ"); out.add("ㅐ"); }
            case "ㅚ" -> { out.add("ㅗ"); out.add("ㅣ"); }
            case "ㅝ" -> { out.add("ㅜ"); out.add("ㅓ"); }
            case "ㅞ" -> { out.add("ㅜ"); out.add("ㅔ"); }
            case "ㅟ" -> { out.add("ㅜ"); out.add("ㅣ"); }
            case "ㅢ" -> { out.add("ㅡ"); out.add("ㅣ"); }
            default -> out.add(vowel);
        }
    }

    public static boolean isConsonant(String s) {
        if (s == null || s.isEmpty()) return false;
        char c = s.charAt(0);
        return (c >= 0x3131 && c <= 0x314E); // Hangul Compatibility Jamo consonants
    }

    public static boolean isVowel(String s) {
        if (s == null || s.isEmpty()) return false;
        char c = s.charAt(0);
        return (c >= 0x314F && c <= 0x3163); // Hangul Compatibility Jamo vowels
    }

    /**
     * Assembles a sequence of Jamo strings back into Hangul syllables.
     */
    public static String assemble(List<String> jamos) {
        StringBuilder result = new StringBuilder();
        int idx = 0;
        int n = jamos.size();

        while (idx < n) {
            String token = jamos.get(idx);
            if (!isConsonant(token)) {
                result.append(token);
                idx++;
                continue;
            }

            // Candidate choseong
            String cho = token;
            idx++;

            // Check if next token is consonant forming double consonant (ㄲ, ㄸ, etc.)
            if (idx < n && isConsonant(jamos.get(idx)) && canDoubleCho(cho, jamos.get(idx))) {
                // Peek if a vowel follows: double consonant is choseong only if a vowel follows it
                if (idx + 1 < n && isVowel(jamos.get(idx + 1))) {
                    cho = makeDoubleCho(cho, jamos.get(idx));
                    idx++;
                }
            }

            // Next must be a vowel to form a syllable
            if (idx >= n || !isVowel(jamos.get(idx))) {
                result.append(cho);
                continue;
            }

            String jung = jamos.get(idx);
            idx++;

            // Check if next is a vowel forming a composite vowel (ㅘ, ㅢ, etc.)
            if (idx < n && isVowel(jamos.get(idx)) && canCombineVowel(jung, jamos.get(idx))) {
                jung = combineVowel(jung, jamos.get(idx));
                idx++;
            }

            // Check for jongseong
            String jong = "";
            if (idx < n && isConsonant(jamos.get(idx))) {
                // If a vowel follows this consonant (or double consonant), this consonant is the NEXT choseong!
                boolean followedByVowel = (idx + 1 < n && isVowel(jamos.get(idx + 1)));
                boolean followedByDoubleThenVowel = (idx + 2 < n
                        && isConsonant(jamos.get(idx + 1))
                        && canDoubleCho(jamos.get(idx), jamos.get(idx + 1))
                        && isVowel(jamos.get(idx + 2)));

                if (!followedByVowel && !followedByDoubleThenVowel) {
                    // This consonant is jongseong candidate
                    String firstJong = jamos.get(idx);
                    idx++;

                    // Check if another consonant follows that forms a composite jongseong (ㄳ, ㄶ, etc.)
                    boolean nextFollowedByVowel = (idx + 1 < n && isVowel(jamos.get(idx + 1)));
                    boolean nextFollowedByDouble = (idx + 2 < n
                            && isConsonant(jamos.get(idx + 1))
                            && canDoubleCho(jamos.get(idx), jamos.get(idx + 1))
                            && isVowel(jamos.get(idx + 2)));

                    if (idx < n && isConsonant(jamos.get(idx))
                            && canCombineJong(firstJong, jamos.get(idx))
                            && !nextFollowedByVowel
                            && !nextFollowedByDouble) {
                        jong = combineJong(firstJong, jamos.get(idx));
                        idx++;
                    } else {
                        jong = firstJong;
                    }
                }
            }

            // Compose Hangul syllable
            int choIdx = indexOf(CHOSEONG, cho);
            int jungIdx = indexOf(JUNGSEONG, jung);
            int jongIdx = indexOf(JONGSEONG, jong);

            if (choIdx >= 0 && jungIdx >= 0) {
                int unicode = HANGUL_BASE + (choIdx * 21 + jungIdx) * 28 + Math.max(0, jongIdx);
                result.append((char) unicode);
            } else {
                result.append(cho).append(jung).append(jong);
            }
        }

        return result.toString();
    }

    private static boolean canDoubleCho(String c1, String c2) {
        return c1.equals(c2) && ("ㄱ".equals(c1) || "ㄷ".equals(c1) || "ㅂ".equals(c1) || "ㅅ".equals(c1) || "ㅈ".equals(c1));
    }

    private static String makeDoubleCho(String c1, String c2) {
        return switch (c1) {
            case "ㄱ" -> "ㄲ";
            case "ㄷ" -> "ㄸ";
            case "ㅂ" -> "ㅃ";
            case "ㅅ" -> "ㅆ";
            case "ㅈ" -> "ㅉ";
            default -> c1 + c2;
        };
    }

    private static boolean canCombineVowel(String v1, String v2) {
        return combineVowel(v1, v2) != null;
    }

    private static String combineVowel(String v1, String v2) {
        if ("ㅗ".equals(v1) && "ㅏ".equals(v2)) return "ㅘ";
        if ("ㅗ".equals(v1) && "ㅐ".equals(v2)) return "ㅙ";
        if ("ㅗ".equals(v1) && "ㅣ".equals(v2)) return "ㅚ";
        if ("ㅜ".equals(v1) && "ㅓ".equals(v2)) return "ㅝ";
        if ("ㅜ".equals(v1) && "ㅔ".equals(v2)) return "ㅞ";
        if ("ㅜ".equals(v1) && "ㅣ".equals(v2)) return "ㅟ";
        if ("ㅡ".equals(v1) && "ㅣ".equals(v2)) return "ㅢ";
        if ("ㅑ".equals(v1) && "ㅣ".equals(v2)) return "ㅒ";
        if ("ㅕ".equals(v1) && "ㅣ".equals(v2)) return "ㅖ";
        return null;
    }

    private static boolean canCombineJong(String j1, String j2) {
        return combineJong(j1, j2) != null;
    }

    private static String combineJong(String j1, String j2) {
        if ("ㄱ".equals(j1) && "ㅅ".equals(j2)) return "ㄳ";
        if ("ㄴ".equals(j1) && "ㅈ".equals(j2)) return "ㄵ";
        if ("ㄴ".equals(j1) && "ㅎ".equals(j2)) return "ㄶ";
        if ("ㄹ".equals(j1) && "ㄱ".equals(j2)) return "ㄺ";
        if ("ㄹ".equals(j1) && "ㅁ".equals(j2)) return "ㄻ";
        if ("ㄹ".equals(j1) && "ㅂ".equals(j2)) return "ㄼ";
        if ("ㄹ".equals(j1) && "ㅅ".equals(j2)) return "ㄽ";
        if ("ㄹ".equals(j1) && "ㅌ".equals(j2)) return "ㄾ";
        if ("ㄹ".equals(j1) && "ㅍ".equals(j2)) return "ㄿ";
        if ("ㄹ".equals(j1) && "ㅎ".equals(j2)) return "ㅀ";
        if ("ㅂ".equals(j1) && "ㅅ".equals(j2)) return "ㅄ";
        if ("ㅅ".equals(j1) && "ㅅ".equals(j2)) return "ㅆ";
        if ("ㄱ".equals(j1) && "ㄱ".equals(j2)) return "ㄲ";
        return null;
    }

    private static int indexOf(String[] array, String target) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(target)) return i;
        }
        return -1;
    }
}
