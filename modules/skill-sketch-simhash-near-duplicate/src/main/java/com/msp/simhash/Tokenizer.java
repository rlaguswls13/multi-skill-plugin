package com.msp.simhash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Strategy interface for tokenizing documents into features for SimHash computation.
 */
@FunctionalInterface
public interface Tokenizer {

    Pattern WORD_BOUNDARY_PATTERN = Pattern.compile("[\\p{Punct}\\s]+");

    /**
     * Extracts token features from the input text.
     *
     * @param text Raw document string
     * @return List of extracted token features
     */
    List<String> tokenize(String text);

    /**
     * Default word-level tokenizer: splits by whitespace and punctuation, converts to lower case.
     */
    Tokenizer WORDS = text -> {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        String[] parts = WORD_BOUNDARY_PATTERN.split(text.toLowerCase(Locale.ROOT));
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isEmpty()) {
                tokens.add(part);
            }
        }
        return tokens;
    };

    /**
     * Character n-gram tokenizer (e.g. 3-grams, 4-grams).
     *
     * @param n Size of character n-grams (must be >= 1)
     * @return Tokenizer generating character n-grams
     */
    static Tokenizer charNGrams(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be greater than 0");
        }
        return text -> {
            if (text == null || text.length() < n) {
                return Collections.emptyList();
            }
            String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            if (normalized.length() < n) {
                return Collections.emptyList();
            }
            List<String> grams = new ArrayList<>(normalized.length() - n + 1);
            for (int i = 0; i <= normalized.length() - n; i++) {
                grams.add(normalized.substring(i, i + n));
            }
            return grams;
        };
    }

    /**
     * Word n-gram tokenizer (e.g. bi-grams, tri-grams).
     *
     * @param n Size of word n-grams (must be >= 1)
     * @return Tokenizer generating word n-grams
     */
    static Tokenizer wordNGrams(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be greater than 0");
        }
        return text -> {
            List<String> words = WORDS.tokenize(text);
            if (words.size() < n) {
                return words;
            }
            List<String> ngrams = new ArrayList<>(words.size() - n + 1);
            for (int i = 0; i <= words.size() - n; i++) {
                ngrams.add(String.join("_", words.subList(i, i + n)));
            }
            return ngrams;
        };
    }
}
