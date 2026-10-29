package com.msp.similarity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * N-gram frequency vector Cosine Similarity metric.
 * Evaluates similarity based on character n-gram distribution.
 */
public final class CosineSimilarity {

    public static final int DEFAULT_NGRAM = 2;

    private CosineSimilarity() {
    }

    /**
     * Calculates cosine similarity using character bigrams (n=2).
     */
    public static double similarity(CharSequence s1, CharSequence s2) {
        return similarity(s1, s2, DEFAULT_NGRAM);
    }

    /**
     * Calculates cosine similarity using character n-grams.
     *
     * @param s1    first sequence
     * @param s2    second sequence
     * @param nGram size of n-gram (e.g. 2 for bigram, 3 for trigram)
     * @return cosine similarity in range [0.0, 1.0]
     */
    public static double similarity(CharSequence s1, CharSequence s2, int nGram) {
        Objects.requireNonNull(s1, "s1 must not be null");
        Objects.requireNonNull(s2, "s2 must not be null");

        if (nGram < 1) {
            throw new IllegalArgumentException("nGram must be at least 1");
        }

        if (s1.equals(s2)) {
            return 1.0;
        }

        if (s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }

        Map<String, Integer> profile1 = extractNgramProfile(s1, nGram);
        Map<String, Integer> profile2 = extractNgramProfile(s2, nGram);

        double dotProduct = 0.0;
        for (Map.Entry<String, Integer> entry : profile1.entrySet()) {
            Integer count2 = profile2.get(entry.getKey());
            if (count2 != null) {
                dotProduct += (double) entry.getValue() * count2;
            }
        }

        double norm1 = calculateNorm(profile1);
        double norm2 = calculateNorm(profile2);

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        double score = dotProduct / (norm1 * norm2);
        return Math.min(1.0, Math.max(0.0, score));
    }

    private static Map<String, Integer> extractNgramProfile(CharSequence s, int nGram) {
        Map<String, Integer> profile = new HashMap<>();
        int len = s.length();

        if (len < nGram) {
            // Fallback for short strings: treat entire sequence as single token
            profile.put(s.toString(), 1);
            return profile;
        }

        for (int i = 0; i <= len - nGram; i++) {
            String gram = s.subSequence(i, i + nGram).toString();
            profile.merge(gram, 1, Integer::sum);
        }
        return profile;
    }

    private static double calculateNorm(Map<String, Integer> profile) {
        double sumSq = 0.0;
        for (int count : profile.values()) {
            sumSq += (double) count * count;
        }
        return Math.sqrt(sumSq);
    }
}
