package com.msp.jarowinkler;

/**
 * Standard utility entry point for Jaro and Jaro-Winkler string similarity calculations.
 */
public final class JaroWinkler {

    private static final JaroWinklerMetric DEFAULT_METRIC = JaroWinklerMetric.standard();

    private JaroWinkler() {}

    /**
     * Calculates Jaro-Winkler similarity (0.0 to 1.0) using standard parameters (p=0.1, L<=4, threshold=0.7).
     */
    public static double similarity(CharSequence s1, CharSequence s2) {
        return DEFAULT_METRIC.similarity(s1, s2);
    }

    /**
     * Calculates standard Jaro similarity (0.0 to 1.0).
     */
    public static double jaroSimilarity(CharSequence s1, CharSequence s2) {
        return DEFAULT_METRIC.jaroSimilarity(s1, s2);
    }

    /**
     * Calculates Jaro-Winkler distance (1.0 - similarity).
     */
    public static double distance(CharSequence s1, CharSequence s2) {
        return DEFAULT_METRIC.distance(s1, s2);
    }

    /**
     * Tests if two strings meet or exceed the similarity threshold.
     */
    public static boolean isMatch(CharSequence s1, CharSequence s2, double threshold) {
        return DEFAULT_METRIC.isMatch(s1, s2, threshold);
    }

    /**
     * Returns standard default metric instance.
     */
    public static JaroWinklerMetric withDefaults() {
        return DEFAULT_METRIC;
    }

    /**
     * Creates a builder for custom metric tuning.
     */
    public static JaroWinklerMetric.Builder builder() {
        return JaroWinklerMetric.builder();
    }
}
