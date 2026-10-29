package com.msp.similarity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Unified entry-point facade for string similarity and typo correction.
 */
public final class StringSimilarity {

    private StringSimilarity() {
    }

    /**
     * Calculates the edit distance (Levenshtein) between two strings.
     */
    public static int levenshteinDistance(CharSequence s1, CharSequence s2) {
        return LevenshteinDistance.distance(s1, s2);
    }

    /**
     * Calculates normalized Levenshtein similarity in range [0.0, 1.0].
     */
    public static double levenshtein(CharSequence s1, CharSequence s2) {
        return LevenshteinDistance.similarity(s1, s2);
    }

    /**
     * Calculates Jaro-Winkler similarity in range [0.0, 1.0].
     */
    public static double jaroWinkler(CharSequence s1, CharSequence s2) {
        return JaroWinklerDistance.similarity(s1, s2);
    }

    /**
     * Calculates Cosine similarity using bigrams in range [0.0, 1.0].
     */
    public static double cosine(CharSequence s1, CharSequence s2) {
        return CosineSimilarity.similarity(s1, s2);
    }

    /**
     * Calculates Cosine similarity using custom n-grams in range [0.0, 1.0].
     */
    public static double cosine(CharSequence s1, CharSequence s2, int nGram) {
        return CosineSimilarity.similarity(s1, s2, nGram);
    }

    /**
     * Suggests the best matching candidate for a potential typo.
     */
    public static Optional<String> suggestBest(String input, Collection<String> candidates) {
        return TypoSuggester.findClosest(input, candidates);
    }

    /**
     * Suggests top matching candidates sorted by relevance.
     */
    public static List<TypoSuggester.Suggestion> suggest(String input, Collection<String> candidates, int maxResults) {
        return new TypoSuggester().suggest(input, candidates, maxResults);
    }
}
