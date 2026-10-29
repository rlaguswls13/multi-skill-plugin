package com.msp.similarity;

import java.util.Objects;

/**
 * Levenshtein distance and normalized similarity algorithm.
 * Space complexity: O(min(m, n)).
 */
public final class LevenshteinDistance {

    private LevenshteinDistance() {
    }

    /**
     * Calculates the standard Levenshtein edit distance between two char sequences.
     *
     * @param s1 first sequence
     * @param s2 second sequence
     * @return minimum number of insertions, deletions, or substitutions
     */
    public static int distance(CharSequence s1, CharSequence s2) {
        Objects.requireNonNull(s1, "s1 must not be null");
        Objects.requireNonNull(s2, "s2 must not be null");

        if (s1.equals(s2)) {
            return 0;
        }

        int m = s1.length();
        int n = s2.length();

        if (m == 0) return n;
        if (n == 0) return m;

        // Ensure s2 is the shorter sequence to minimize memory allocation
        if (m < n) {
            CharSequence tempSeq = s1;
            s1 = s2;
            s2 = tempSeq;
            int tempLen = m;
            m = n;
            n = tempLen;
        }

        int[] v0 = new int[n + 1];
        int[] v1 = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            v0[j] = j;
        }

        for (int i = 0; i < m; i++) {
            v1[0] = i + 1;
            char c1 = s1.charAt(i);

            for (int j = 0; j < n; j++) {
                char c2 = s2.charAt(j);
                int cost = (c1 == c2) ? 0 : 1;
                v1[j + 1] = Math.min(v1[j] + 1, Math.min(v0[j + 1] + 1, v0[j] + cost));
            }

            int[] swap = v0;
            v0 = v1;
            v1 = swap;
        }

        return v0[n];
    }

    /**
     * Calculates normalized Levenshtein similarity in the range [0.0, 1.0].
     * 1.0 represents identical strings; 0.0 represents completely disjoint strings.
     */
    public static double similarity(CharSequence s1, CharSequence s2) {
        Objects.requireNonNull(s1, "s1 must not be null");
        Objects.requireNonNull(s2, "s2 must not be null");

        if (s1.equals(s2)) {
            return 1.0;
        }

        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }

        int dist = distance(s1, s2);
        return Math.max(0.0, 1.0 - ((double) dist / maxLen));
    }
}
