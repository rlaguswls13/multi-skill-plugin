package com.msp.invindex;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a scored search result matching a query.
 */
public record SearchResult<T>(
        String docId,
        T data,
        double score,
        Map<String, Integer> matchedTerms
) implements Comparable<SearchResult<T>> {

    public SearchResult {
        matchedTerms = matchedTerms != null ? Collections.unmodifiableMap(matchedTerms) : Collections.emptyMap();
    }

    @Override
    public int compareTo(SearchResult<T> o) {
        int cmp = Double.compare(o.score, this.score); // descending
        if (cmp != 0) return cmp;
        return this.docId.compareTo(o.docId);
    }
}
