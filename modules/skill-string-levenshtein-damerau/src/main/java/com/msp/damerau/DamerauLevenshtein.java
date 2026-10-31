package com.msp.damerau;

import java.util.Objects;

/**
 * Pure Java 21 Damerau-Levenshtein distance and string similarity matcher.
 * <p>
 * Supports 4 basic single-cost edit operations:
 * <ul>
 *   <li><b>Insertion</b>: adding a character (cost = 1)</li>
 *   <li><b>Deletion</b>: removing a character (cost = 1)</li>
 *   <li><b>Substitution</b>: replacing a character (cost = 1)</li>
 *   <li><b>Transposition</b>: swapping adjacent characters e.g. "ab" &lt;-&gt; "ba" (cost = 1)</li>
 * </ul>
 * <p>
 * Highly optimized with early branch pruning for threshold searches.
 * Thread-safe and zero-allocation for short-circuit checks.
 */
public final class DamerauLevenshtein {

    private DamerauLevenshtein() {
        // Utility class
    }

    /**
     * Calculates the Damerau-Levenshtein edit distance between two character sequences.
     *
     * @param s1 first sequence
     * @param s2 second sequence
     * @return edit distance (0 if identical)
     * @throws IllegalArgumentException if either sequence is null
     */
    public static int distance(CharSequence s1, CharSequence s2) {
        if (s1 == null || s2 == null) {
            throw new IllegalArgumentException("Sequences cannot be null");
        }

        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        if (charSequenceEquals(s1, s2)) {
            return 0;
        }

        // DP Matrix: (len1 + 1) x (len2 + 1)
        int[][] d = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            d[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;

                int del = d[i - 1][j] + 1;
                int ins = d[i][j - 1] + 1;
                int sub = d[i - 1][j - 1] + cost;

                int min = Math.min(del, Math.min(ins, sub));

                // Adjacent transposition check: "ab" <-> "ba"
                if (i > 1 && j > 1 && c1 == s2.charAt(j - 2) && s1.charAt(i - 2) == c2) {
                    min = Math.min(min, d[i - 2][j - 2] + 1);
                }

                d[i][j] = min;
            }
        }

        return d[len1][len2];
    }

    /**
     * Calculates normalized similarity ratio between two character sequences, from 0.0 (completely distinct)
     * to 1.0 (identical).
     *
     * @param s1 first sequence
     * @param s2 second sequence
     * @return similarity score between 0.0 and 1.0
     * @throws IllegalArgumentException if either sequence is null
     */
    public static double similarity(CharSequence s1, CharSequence s2) {
        if (s1 == null || s2 == null) {
            throw new IllegalArgumentException("Sequences cannot be null");
        }

        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0 && len2 == 0) {
            return 1.0;
        }

        int maxLen = Math.max(len1, len2);
        int dist = distance(s1, s2);
        return Math.max(0.0, 1.0 - ((double) dist / maxLen));
    }

    /**
     * Determines whether the edit distance between two sequences is within {@code maxDistance},
     * utilizing early branch pruning for high throughput.
     *
     * @param s1 first sequence
     * @param s2 second sequence
     * @param maxDistance maximum allowed edit distance (inclusive)
     * @return {@code true} if distance &lt;= maxDistance, {@code false} otherwise
     * @throws IllegalArgumentException if either sequence is null
     */
    public static boolean isWithinDistance(CharSequence s1, CharSequence s2, int maxDistance) {
        if (s1 == null || s2 == null) {
            throw new IllegalArgumentException("Sequences cannot be null");
        }
        if (maxDistance < 0) {
            return false;
        }

        int len1 = s1.length();
        int len2 = s2.length();

        // 1. Length difference pruning (O(1))
        if (Math.abs(len1 - len2) > maxDistance) {
            return false;
        }

        // 2. Exact match check (O(N))
        if (maxDistance == 0) {
            return charSequenceEquals(s1, s2);
        }

        if (len1 == 0 || len2 == 0) {
            return Math.max(len1, len2) <= maxDistance;
        }

        // 3. Matrix computation with row-minimum early exit
        int[][] d = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            d[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            char c1 = s1.charAt(i - 1);
            int rowMin = Integer.MAX_VALUE;

            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;

                int del = d[i - 1][j] + 1;
                int ins = d[i][j - 1] + 1;
                int sub = d[i - 1][j - 1] + cost;

                int min = Math.min(del, Math.min(ins, sub));

                if (i > 1 && j > 1 && c1 == s2.charAt(j - 2) && s1.charAt(i - 2) == c2) {
                    min = Math.min(min, d[i - 2][j - 2] + 1);
                }

                d[i][j] = min;
                if (min < rowMin) {
                    rowMin = min;
                }
            }

            // Pruning: if lowest distance in this entire row already exceeds maxDistance, break
            if (rowMin > maxDistance) {
                return false;
            }
        }

        return d[len1][len2] <= maxDistance;
    }

    private static boolean charSequenceEquals(CharSequence s1, CharSequence s2) {
        if (s1.length() != s2.length()) return false;
        for (int i = 0; i < s1.length(); i++) {
            if (s1.charAt(i) != s2.charAt(i)) return false;
        }
        return true;
    }
}
