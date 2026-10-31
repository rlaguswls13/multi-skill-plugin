package com.msp.fuzzysearch;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * N-Gram based Jaccard & Sørensen-Dice fuzzy string similarity matcher
 * and in-memory typo-tolerant search indexer.
 *
 * @param <T> payload type
 */
public final class FuzzySearchIndex<T> {

    private final int n;
    private final boolean useDice;
    private final List<IndexedDoc<T>> documents = new ArrayList<>();
    private final Map<String, List<IndexedDoc<T>>> invertedIndex = new HashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    private record IndexedDoc<T>(String text, T payload, Map<String, Integer> ngrams) {}

    public FuzzySearchIndex(int n, boolean useDice) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be at least 1, got: " + n);
        }
        this.n = n;
        this.useDice = useDice;
    }

    public FuzzySearchIndex(int n) {
        this(n, false);
    }

    public FuzzySearchIndex() {
        this(2, false);
    }

    /**
     * Indexes a document text with an associated payload.
     */
    public void index(String text, T payload) {
        Objects.requireNonNull(text, "text must not be null");
        Map<String, Integer> ngrams = extractNgrams(text, n);
        IndexedDoc<T> doc = new IndexedDoc<>(text, payload, ngrams);

        rwLock.writeLock().lock();
        try {
            documents.add(doc);
            for (String gram : ngrams.keySet()) {
                invertedIndex.computeIfAbsent(gram, k -> new ArrayList<>()).add(doc);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Performs a fuzzy search query against the index.
     *
     * @param query the query string
     * @param minSimilarity minimum similarity threshold [0.0, 1.0]
     * @param limit maximum number of results to return (must be > 0)
     * @return ranked list of fuzzy match results in descending score order
     */
    public List<FuzzyMatchResult<T>> search(String query, double minSimilarity, int limit) {
        Objects.requireNonNull(query, "query must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0, got: " + limit);
        }

        rwLock.readLock().lock();
        try {
            if (documents.isEmpty()) {
                return List.of();
            }

            Map<String, Integer> queryNgrams = extractNgrams(query, n);
            Set<IndexedDoc<T>> candidates = new HashSet<>();

            if (queryNgrams.isEmpty()) {
                // If query produces no ngrams, evaluate against all docs
                candidates.addAll(documents);
            } else {
                for (String gram : queryNgrams.keySet()) {
                    List<IndexedDoc<T>> docs = invertedIndex.get(gram);
                    if (docs != null) {
                        candidates.addAll(docs);
                    }
                }
            }

            List<FuzzyMatchResult<T>> matches = new ArrayList<>();
            for (IndexedDoc<T> doc : candidates) {
                double score = useDice
                        ? dice(query, doc.text(), n)
                        : jaccard(query, doc.text(), n);

                if (score >= minSimilarity) {
                    matches.add(new FuzzyMatchResult<>(doc.payload(), score, doc.text()));
                }
            }

            Collections.sort(matches);

            if (matches.size() > limit) {
                return matches.subList(0, limit);
            }
            return matches;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Computes the Jaccard similarity index between s1 and s2 using n-grams.
     * J(A, B) = |A ∩ B| / |A ∪ B|
     */
    public static double jaccard(String s1, String s2, int n) {
        if (s1 == null || s2 == null) {
            throw new IllegalArgumentException("Strings must not be null");
        }
        if (s1.equals(s2)) {
            return 1.0;
        }
        if (s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }

        Map<String, Integer> m1 = extractNgrams(s1, n);
        Map<String, Integer> m2 = extractNgrams(s2, n);

        int intersection = computeIntersectionCount(m1, m2);
        int total1 = countTotalNgrams(m1);
        int total2 = countTotalNgrams(m2);
        int union = total1 + total2 - intersection;

        if (union == 0) {
            return 1.0;
        }
        return (double) intersection / union;
    }

    /**
     * Computes the Sørensen-Dice coefficient between s1 and s2 using n-grams.
     * D(A, B) = (2 * |A ∩ B|) / (|A| + |B|)
     */
    public static double dice(String s1, String s2, int n) {
        if (s1 == null || s2 == null) {
            throw new IllegalArgumentException("Strings must not be null");
        }
        if (s1.equals(s2)) {
            return 1.0;
        }
        if (s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }

        Map<String, Integer> m1 = extractNgrams(s1, n);
        Map<String, Integer> m2 = extractNgrams(s2, n);

        int intersection = computeIntersectionCount(m1, m2);
        int total1 = countTotalNgrams(m1);
        int total2 = countTotalNgrams(m2);
        int sum = total1 + total2;

        if (sum == 0) {
            return 1.0;
        }
        return (2.0 * intersection) / sum;
    }

    public int size() {
        rwLock.readLock().lock();
        try {
            return documents.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public int getN() {
        return n;
    }

    public boolean isUseDice() {
        return useDice;
    }

    private static Map<String, Integer> extractNgrams(String s, int n) {
        Map<String, Integer> map = new HashMap<>();
        if (s == null || s.isEmpty()) {
            return map;
        }

        int len = s.length();
        if (len < n) {
            map.put(s, 1);
            return map;
        }

        for (int i = 0; i <= len - n; i++) {
            String gram = s.substring(i, i + n);
            map.merge(gram, 1, Integer::sum);
        }
        return map;
    }

    private static int computeIntersectionCount(Map<String, Integer> m1, Map<String, Integer> m2) {
        int count = 0;
        for (Map.Entry<String, Integer> entry : m1.entrySet()) {
            Integer count2 = m2.get(entry.getKey());
            if (count2 != null) {
                count += Math.min(entry.getValue(), count2);
            }
        }
        return count;
    }

    private static int countTotalNgrams(Map<String, Integer> map) {
        int total = 0;
        for (int c : map.values()) {
            total += c;
        }
        return total;
    }
}
