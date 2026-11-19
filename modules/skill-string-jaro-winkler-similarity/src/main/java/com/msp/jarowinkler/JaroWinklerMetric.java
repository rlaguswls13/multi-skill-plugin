package com.msp.jarowinkler;

import java.util.Locale;

/**
 * Configurable Jaro and Jaro-Winkler string similarity metric.
 */
public record JaroWinklerMetric(
        double scalingFactor,
        int maxPrefixLength,
        double boostThreshold,
        boolean caseSensitive
) {
    public static final double DEFAULT_SCALING_FACTOR = 0.1;
    public static final int DEFAULT_MAX_PREFIX_LENGTH = 4;
    public static final double DEFAULT_BOOST_THRESHOLD = 0.7;
    public static final boolean DEFAULT_CASE_SENSITIVE = true;

    public JaroWinklerMetric {
        if (scalingFactor < 0.0 || scalingFactor * maxPrefixLength > 1.0) {
            throw new IllegalArgumentException("scalingFactor must satisfy: 0 <= scalingFactor * maxPrefixLength <= 1");
        }
        if (maxPrefixLength < 0) {
            throw new IllegalArgumentException("maxPrefixLength cannot be negative");
        }
        if (boostThreshold < 0.0 || boostThreshold > 1.0) {
            throw new IllegalArgumentException("boostThreshold must be between 0.0 and 1.0");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static JaroWinklerMetric standard() {
        return new JaroWinklerMetric(
                DEFAULT_SCALING_FACTOR,
                DEFAULT_MAX_PREFIX_LENGTH,
                DEFAULT_BOOST_THRESHOLD,
                DEFAULT_CASE_SENSITIVE
        );
    }

    /**
     * Calculates the Jaro-Winkler similarity between two character sequences.
     *
     * @return a score between 0.0 (no similarity) and 1.0 (exact match)
     */
    public double similarity(CharSequence s1, CharSequence s2) {
        if (s1 == null && s2 == null) {
            return 1.0;
        }
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        String str1 = caseSensitive ? s1.toString() : s1.toString().toLowerCase(Locale.ROOT);
        String str2 = caseSensitive ? s2.toString() : s2.toString().toLowerCase(Locale.ROOT);

        if (str1.equals(str2)) {
            return 1.0;
        }

        int len1 = str1.length();
        int len2 = str2.length();
        if (len1 == 0 || len2 == 0) {
            return 0.0;
        }

        double jaroSim = calculateJaro(str1, str2, len1, len2);
        if (jaroSim < boostThreshold) {
            return jaroSim;
        }

        // Common prefix length up to maxPrefixLength
        int prefix = 0;
        int maxPrefix = Math.min(Math.min(len1, len2), maxPrefixLength);
        for (int i = 0; i < maxPrefix; i++) {
            if (str1.charAt(i) == str2.charAt(i)) {
                prefix++;
            } else {
                break;
            }
        }

        return jaroSim + prefix * scalingFactor * (1.0 - jaroSim);
    }

    /**
     * Calculates the standard Jaro similarity without the Winkler prefix bonus.
     */
    public double jaroSimilarity(CharSequence s1, CharSequence s2) {
        if (s1 == null && s2 == null) {
            return 1.0;
        }
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        String str1 = caseSensitive ? s1.toString() : s1.toString().toLowerCase(Locale.ROOT);
        String str2 = caseSensitive ? s2.toString() : s2.toString().toLowerCase(Locale.ROOT);

        if (str1.equals(str2)) {
            return 1.0;
        }

        int len1 = str1.length();
        int len2 = str2.length();
        if (len1 == 0 || len2 == 0) {
            return 0.0;
        }

        return calculateJaro(str1, str2, len1, len2);
    }

    /**
     * Calculates the Jaro-Winkler distance (1.0 - similarity).
     */
    public double distance(CharSequence s1, CharSequence s2) {
        return 1.0 - similarity(s1, s2);
    }

    /**
     * Checks if two sequences match based on a minimum similarity threshold.
     */
    public boolean isMatch(CharSequence s1, CharSequence s2, double threshold) {
        return similarity(s1, s2) >= threshold;
    }

    private double calculateJaro(String s1, String s2, int len1, int len2) {
        int matchWindow = Math.max(0, Math.max(len1, len2) / 2 - 1);

        boolean[] s1Matches = new boolean[len1];
        boolean[] s2Matches = new boolean[len2];
        int matches = 0;

        for (int i = 0; i < len1; i++) {
            char c1 = s1.charAt(i);
            int start = Math.max(0, i - matchWindow);
            int end = Math.min(len2 - 1, i + matchWindow);

            for (int j = start; j <= end; j++) {
                if (!s2Matches[j] && c1 == s2.charAt(j)) {
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

        int transpositions = 0;
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (s1Matches[i]) {
                while (!s2Matches[k]) {
                    k++;
                }
                if (s1.charAt(i) != s2.charAt(k)) {
                    transpositions++;
                }
                k++;
            }
        }

        double halfTranspositions = transpositions / 2.0;

        return ((matches / (double) len1)
                + (matches / (double) len2)
                + ((matches - halfTranspositions) / (double) matches)) / 3.0;
    }

    public static final class Builder {
        private double scalingFactor = DEFAULT_SCALING_FACTOR;
        private int maxPrefixLength = DEFAULT_MAX_PREFIX_LENGTH;
        private double boostThreshold = DEFAULT_BOOST_THRESHOLD;
        private boolean caseSensitive = DEFAULT_CASE_SENSITIVE;

        public Builder scalingFactor(double factor) {
            this.scalingFactor = factor;
            return this;
        }

        public Builder maxPrefixLength(int length) {
            this.maxPrefixLength = length;
            return this;
        }

        public Builder boostThreshold(double threshold) {
            this.boostThreshold = threshold;
            return this;
        }

        public Builder caseSensitive(boolean sensitive) {
            this.caseSensitive = sensitive;
            return this;
        }

        public JaroWinklerMetric build() {
            return new JaroWinklerMetric(scalingFactor, maxPrefixLength, boostThreshold, caseSensitive);
        }
    }
}
