package com.msp.fuzzysearch;

import java.util.Objects;

/**
 * Result of a fuzzy similarity match.
 *
 * @param item the associated payload item
 * @param score the calculated similarity score in [0.0, 1.0]
 * @param matchedText the indexed text that produced the match
 * @param <T> payload type
 */
public record FuzzyMatchResult<T>(T item, double score, String matchedText) implements Comparable<FuzzyMatchResult<T>> {

    public FuzzyMatchResult {
        Objects.requireNonNull(matchedText, "matchedText must not be null");
    }

    public T getItem() {
        return item;
    }

    public double getScore() {
        return score;
    }

    public String getMatchedText() {
        return matchedText;
    }

    @Override
    public int compareTo(FuzzyMatchResult<T> other) {
        // Natural ordering: descending by score, ascending by matchedText
        int scoreCmp = Double.compare(other.score, this.score);
        if (scoreCmp != 0) {
            return scoreCmp;
        }
        return this.matchedText.compareTo(other.matchedText);
    }
}
