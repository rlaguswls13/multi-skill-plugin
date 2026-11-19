package com.msp.transducer;

import java.util.Objects;

/**
 * Result representing a matching dictionary word and its Levenshtein edit distance.
 */
public record FuzzyMatch(String word, int distance) implements Comparable<FuzzyMatch> {

    public FuzzyMatch {
        Objects.requireNonNull(word, "word cannot be null");
        if (distance < 0) {
            throw new IllegalArgumentException("distance cannot be negative");
        }
    }

    @Override
    public int compareTo(FuzzyMatch other) {
        int cmp = Integer.compare(this.distance, other.distance);
        if (cmp != 0) {
            return cmp;
        }
        return this.word.compareTo(other.word);
    }
}
