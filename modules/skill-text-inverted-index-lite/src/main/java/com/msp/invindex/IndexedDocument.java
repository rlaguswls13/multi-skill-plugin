package com.msp.invindex;

import java.util.Collections;
import java.util.Map;

/**
 * Internal representation of an indexed document with term statistics.
 */
public record IndexedDocument<T>(
        String docId,
        T data,
        Map<String, String> rawFields,
        int totalLength,
        Map<String, Integer> termFrequencies,
        Map<String, Double> weightedTermFrequencies
) {
    public IndexedDocument {
        rawFields = Collections.unmodifiableMap(rawFields);
        termFrequencies = Collections.unmodifiableMap(termFrequencies);
        weightedTermFrequencies = Collections.unmodifiableMap(weightedTermFrequencies);
    }
}
