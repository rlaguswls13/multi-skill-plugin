package com.msp.bm25;

import java.util.Objects;

/**
 * Represents a document along with its calculated BM25 relevance score.
 * Implements {@link Comparable} to provide natural ordering in descending order of score.
 *
 * @param <T>      the type of document identifier
 * @param document the document
 * @param score    the computed BM25 relevance score
 */
public record ScoredDocument<T>(Document<T> document, double score) implements Comparable<ScoredDocument<T>> {

    public ScoredDocument {
        Objects.requireNonNull(document, "Document must not be null");
    }

    @Override
    public int compareTo(ScoredDocument<T> o) {
        // Natural ordering is descending by score
        return Double.compare(o.score, this.score);
    }
}
