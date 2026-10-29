package com.msp.similarity;

import java.util.*;

/**
 * Auto-suggestion engine for correcting typos and finding closest candidate matches.
 */
public final class TypoSuggester {

    public static final double DEFAULT_MIN_SIMILARITY = 0.65;
    public static final int DEFAULT_MAX_RESULTS = 5;

    public enum Algorithm {
        JARO_WINKLER,
        LEVENSHTEIN,
        COSINE
    }

    /**
     * Immutable suggestion result holding candidate text, similarity score, and edit distance.
     */
    public record Suggestion(String candidate, double similarity, int editDistance) implements Comparable<Suggestion> {
        @Override
        public int compareTo(Suggestion other) {
            // Highest similarity first
            int cmp = Double.compare(other.similarity, this.similarity);
            if (cmp != 0) {
                return cmp;
            }
            // Smallest edit distance first
            cmp = Integer.compare(this.editDistance, other.editDistance);
            if (cmp != 0) {
                return cmp;
            }
            return this.candidate.compareTo(other.candidate);
        }
    }

    private final Algorithm algorithm;
    private final double minSimilarity;

    public TypoSuggester() {
        this(Algorithm.JARO_WINKLER, DEFAULT_MIN_SIMILARITY);
    }

    public TypoSuggester(Algorithm algorithm, double minSimilarity) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
        this.minSimilarity = minSimilarity;
    }

    /**
     * Finds the single best matching candidate if its similarity satisfies the threshold.
     */
    public Optional<String> suggestBest(String input, Collection<String> candidates) {
        List<Suggestion> results = suggest(input, candidates, 1);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst().candidate());
    }

    /**
     * Recommends top matching suggestions up to {@code maxResults}.
     */
    public List<Suggestion> suggest(String input, Collection<String> candidates, int maxResults) {
        return suggest(input, candidates, this.minSimilarity, maxResults);
    }

    /**
     * Recommends top matching suggestions meeting the given threshold.
     */
    public List<Suggestion> suggest(String input, Collection<String> candidates, double threshold, int maxResults) {
        Objects.requireNonNull(input, "input must not be null");
        if (candidates == null || candidates.isEmpty() || maxResults <= 0) {
            return List.of();
        }

        List<Suggestion> scored = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate == null) continue;

            double score = computeSimilarity(input, candidate, this.algorithm);
            if (score >= threshold) {
                int dist = LevenshteinDistance.distance(input, candidate);
                scored.add(new Suggestion(candidate, score, dist));
            }
        }

        scored.sort(null);

        if (scored.size() > maxResults) {
            return Collections.unmodifiableList(new ArrayList<>(scored.subList(0, maxResults)));
        }
        return Collections.unmodifiableList(scored);
    }

    private static double computeSimilarity(String s1, String s2, Algorithm algo) {
        return switch (algo) {
            case JARO_WINKLER -> JaroWinklerDistance.similarity(s1, s2);
            case LEVENSHTEIN -> LevenshteinDistance.similarity(s1, s2);
            case COSINE -> CosineSimilarity.similarity(s1, s2);
        };
    }

    /**
     * Static convenience method to find the closest candidate with default settings.
     */
    public static Optional<String> findClosest(String input, Collection<String> candidates) {
        return new TypoSuggester().suggestBest(input, candidates);
    }
}
