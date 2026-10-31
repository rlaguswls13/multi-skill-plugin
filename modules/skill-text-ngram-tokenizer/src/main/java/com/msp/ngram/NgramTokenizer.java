package com.msp.ngram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * High-performance N-gram tokenizer and text similarity calculator (Jaccard & Cosine).
 * Zero-dependency, pure Java 21 implementation.
 */
public final class NgramTokenizer {

    private NgramTokenizer() {
        // Utility class
    }

    /**
     * Splits text into character N-grams.
     *
     * @param text input text
     * @param n n-gram length (e.g. 1 for unigram, 2 for bigram, 3 for trigram)
     * @return list of character n-gram tokens, or empty list if input is too short or invalid
     */
    public static List<String> characterNgrams(String text, int n) {
        if (text == null || text.isEmpty() || n <= 0 || text.length() < n) {
            return Collections.emptyList();
        }

        int count = text.length() - n + 1;
        List<String> ngrams = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ngrams.add(text.substring(i, i + n));
        }
        return Collections.unmodifiableList(ngrams);
    }

    /**
     * Splits text into word N-grams based on whitespace delimiters.
     *
     * @param text input text
     * @param n word n-gram length
     * @return list of word n-gram tokens joined by space
     */
    public static List<String> wordNgrams(String text, int n) {
        if (text == null || text.isBlank() || n <= 0) {
            return Collections.emptyList();
        }

        String[] words = text.trim().split("\\s+");
        if (words.length < n) {
            return Collections.emptyList();
        }

        int count = words.length - n + 1;
        List<String> ngrams = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < n; j++) {
                if (j > 0) {
                    sb.append(" ");
                }
                sb.append(words[i + j]);
            }
            ngrams.add(sb.toString());
        }
        return Collections.unmodifiableList(ngrams);
    }

    /**
     * Computes Jaccard similarity based on character N-grams.
     * Score ranges between 0.0 (completely distinct) and 1.0 (identical).
     *
     * @param s1 first string
     * @param s2 second string
     * @param n n-gram size
     * @return Jaccard similarity in [0.0, 1.0]
     */
    public static double jaccardSimilarity(String s1, String s2, int n) {
        if (s1 == null && s2 == null) {
            return 1.0;
        }
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.equals(s2)) {
            return 1.0;
        }

        List<String> tokens1 = characterNgrams(s1, n);
        List<String> tokens2 = characterNgrams(s2, n);

        return computeJaccard(tokens1, tokens2);
    }

    /**
     * Computes Jaccard similarity based on word N-grams.
     */
    public static double wordJaccardSimilarity(String s1, String s2, int n) {
        if (s1 == null && s2 == null) {
            return 1.0;
        }
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.trim().equals(s2.trim())) {
            return 1.0;
        }

        List<String> tokens1 = wordNgrams(s1, n);
        List<String> tokens2 = wordNgrams(s2, n);

        return computeJaccard(tokens1, tokens2);
    }

    /**
     * Computes Cosine similarity based on character N-gram term frequency vectors.
     * Score ranges between 0.0 (orthogonal) and 1.0 (identical vector).
     *
     * @param s1 first string
     * @param s2 second string
     * @param n n-gram size
     * @return Cosine similarity in [0.0, 1.0]
     */
    public static double cosineSimilarity(String s1, String s2, int n) {
        if (s1 == null && s2 == null) {
            return 1.0;
        }
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.equals(s2)) {
            return 1.0;
        }

        List<String> tokens1 = characterNgrams(s1, n);
        List<String> tokens2 = characterNgrams(s2, n);

        return computeCosine(tokens1, tokens2);
    }

    /**
     * Computes Cosine similarity based on word N-gram term frequency vectors.
     */
    public static double wordCosineSimilarity(String s1, String s2, int n) {
        if (s1 == null && s2 == null) {
            return 1.0;
        }
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.trim().equals(s2.trim())) {
            return 1.0;
        }

        List<String> tokens1 = wordNgrams(s1, n);
        List<String> tokens2 = wordNgrams(s2, n);

        return computeCosine(tokens1, tokens2);
    }

    // --- Private calculation algorithms ---

    private static double computeJaccard(List<String> tokens1, List<String> tokens2) {
        if (tokens1.isEmpty() && tokens2.isEmpty()) {
            return 0.0;
        }
        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }

        Set<String> set1 = new HashSet<>(tokens1);
        Set<String> set2 = new HashSet<>(tokens2);

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) {
            return 0.0;
        }
        return (double) intersection.size() / (double) union.size();
    }

    private static double computeCosine(List<String> tokens1, List<String> tokens2) {
        if (tokens1.isEmpty() && tokens2.isEmpty()) {
            return 0.0;
        }
        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }

        Map<String, Integer> freq1 = toFrequencyMap(tokens1);
        Map<String, Integer> freq2 = toFrequencyMap(tokens2);

        Set<String> allTokens = new HashSet<>(freq1.keySet());
        allTokens.addAll(freq2.keySet());

        double dotProduct = 0.0;
        double norm1Sq = 0.0;
        double norm2Sq = 0.0;

        for (Map.Entry<String, Integer> e : freq1.entrySet()) {
            norm1Sq += (long) e.getValue() * e.getValue();
        }
        for (Map.Entry<String, Integer> e : freq2.entrySet()) {
            norm2Sq += (long) e.getValue() * e.getValue();
        }

        for (String token : allTokens) {
            int c1 = freq1.getOrDefault(token, 0);
            int c2 = freq2.getOrDefault(token, 0);
            dotProduct += (long) c1 * c2;
        }

        if (norm1Sq <= 0.0 || norm2Sq <= 0.0) {
            return 0.0;
        }

        double cosine = dotProduct / (Math.sqrt(norm1Sq) * Math.sqrt(norm2Sq));
        return Math.min(1.0, Math.max(0.0, cosine));
    }

    private static Map<String, Integer> toFrequencyMap(List<String> tokens) {
        Map<String, Integer> map = new HashMap<>();
        for (String token : tokens) {
            map.put(token, map.getOrDefault(token, 0) + 1);
        }
        return map;
    }
}
