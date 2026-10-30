package com.msp.fuzzysearch;

public record FuzzyMatchResult<T>(
        T item,
        String matchedText,
        double score,
        int distance
) implements Comparable<FuzzyMatchResult<T>> {

    @Override
    public int compareTo(FuzzyMatchResult<T> o) {
        // Higher score first, then lower distance
        int cmp = Double.compare(o.score, this.score);
        if (cmp != 0) return cmp;
        return Integer.compare(this.distance, o.distance);
    }
}
