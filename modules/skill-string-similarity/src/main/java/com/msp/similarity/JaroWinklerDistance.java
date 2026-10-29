package com.msp.similarity;

import java.util.Objects;

/**
 * Jaro-Winkler distance and similarity metric.
 * Particularly well-suited for short strings, typos, and name matching.
 */
public final class JaroWinklerDistance {

    public static final double DEFAULT_PREFIX_SCALE = 0.1;
    public static final double DEFAULT_BOOST_THRESHOLD = 0.7;

    private JaroWinklerDistance() {
    }

    /**
     * Calculates the Jaro similarity score between two char sequences in range [0.0, 1.0].
     */
    public static double jaroDistance(CharSequence s1, CharSequence s2) {
        Objects.requireNonNull(s1, "s1 must not be null");
        Objects.requireNonNull(s2, "s2 must not be null");

        if (s1.equals(s2)) {
            return 1.0;
        }

        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0 || len2 == 0) {
            return 0.0;
        }

        int matchWindow = Math.max(len1, len2) / 2 - 1;
        if (matchWindow < 0) {
            matchWindow = 0;
        }

        boolean[] s1Matches = new boolean[len1];
        boolean[] s2Matches = new boolean[len2];

        int matches = 0;
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchWindow);
            int end = Math.min(i + matchWindow + 1, len2);
            for (int j = start; j < end; j++) {
                if (s2Matches[j]) {
                    continue;
                }
                if (s1.charAt(i) == s2.charAt(j)) {
                    s1Matches[i] = true;
                    s2Matches[j] = true;
                    matches++;
                    break;
                }
            }
        }

        if (matches == 0) {
            return 0.0;
        }

        // Count half transpositions
        int transpositions = 0;
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matches[i]) {
                continue;
            }
            while (!s2Matches[k]) {
                k++;
            }
            if (s1.charAt(i) != s2.charAt(k)) {
                transpositions++;
            }
            k++;
        }

        double m = matches;
        return (m / len1 + m / len2 + (m - (transpositions / 2.0)) / m) / 3.0;
    }

    /**
     * Calculates the Jaro-Winkler similarity with default prefix scale (0.1) and boost threshold (0.7).
     *
     * @return similarity score in range [0.0, 1.0]
     */
    public static double similarity(CharSequence s1, CharSequence s2) {
        return similarity(s1, s2, DEFAULT_PREFIX_SCALE, DEFAULT_BOOST_THRESHOLD);
    }

    /**
     * Calculates the Jaro-Winkler similarity with custom prefix scale and threshold.
     *
     * @param s1             first sequence
     * @param s2             second sequence
     * @param prefixScale    prefix scaling factor (standard is 0.1, must not exceed 0.25)
     * @param boostThreshold minimum Jaro score threshold before applying prefix bonus
     * @return similarity score in range [0.0, 1.0]
     */
    public static double similarity(CharSequence s1, CharSequence s2, double prefixScale, double boostThreshold) {
        Objects.requireNonNull(s1, "s1 must not be null");
        Objects.requireNonNull(s2, "s2 must not be null");

        if (s1.equals(s2)) {
            return 1.0;
        }

        double jaro = jaroDistance(s1, s2);
        if (jaro < boostThreshold) {
            return jaro;
        }

        int prefix = 0;
        int maxPrefix = Math.min(4, Math.min(s1.length(), s2.length()));
        for (int i = 0; i < maxPrefix; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                prefix++;
            } else {
                break;
            }
        }

        double result = jaro + prefix * prefixScale * (1.0 - jaro);
        return Math.min(1.0, Math.max(0.0, result));
    }
}
