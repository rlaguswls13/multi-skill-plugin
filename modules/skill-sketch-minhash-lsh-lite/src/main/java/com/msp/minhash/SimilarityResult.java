package com.msp.minhash;

import java.util.Objects;

/**
 * Result of an LSH similarity query containing document ID and estimated similarity score.
 */
public record SimilarityResult(String docId, double similarity) implements Comparable<SimilarityResult> {

    public SimilarityResult {
        Objects.requireNonNull(docId, "docId must not be null");
    }

    @Override
    public int compareTo(SimilarityResult o) {
        // Sort descending by similarity, tie-break by docId ascending
        int cmp = Double.compare(o.similarity, this.similarity);
        if (cmp != 0) {
            return cmp;
        }
        return this.docId.compareTo(o.docId);
    }
}
