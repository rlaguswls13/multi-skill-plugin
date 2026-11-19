package com.msp.minhash;

import java.util.*;

/**
 * Utility for generating character k-shingles and word n-grams from raw text.
 */
public final class ShingleGenerator {

    private ShingleGenerator() {
    }

    /**
     * Extracts character k-shingles (substrings of length k).
     */
    public static Set<String> characterShingles(String text, int k) {
        if (text == null || text.isEmpty() || k <= 0) {
            return Collections.emptySet();
        }
        if (text.length() <= k) {
            return Collections.singleton(text);
        }
        Set<String> shingles = new LinkedHashSet<>();
        for (int i = 0; i <= text.length() - k; i++) {
            shingles.add(text.substring(i, i + k));
        }
        return shingles;
    }

    /**
     * Extracts word n-grams (sequences of n consecutive whitespace-delimited words).
     */
    public static Set<String> wordNGrams(String text, int n) {
        if (text == null || text.isBlank() || n <= 0) {
            return Collections.emptySet();
        }
        String[] words = text.trim().split("\\s+");
        if (words.length <= n) {
            return Collections.singleton(String.join(" ", words));
        }
        Set<String> nGrams = new LinkedHashSet<>();
        for (int i = 0; i <= words.length - n; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < n; j++) {
                if (j > 0) sb.append(' ');
                sb.append(words[i + j]);
            }
            nGrams.add(sb.toString());
        }
        return nGrams;
    }
}
