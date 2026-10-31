package com.msp.bm25;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Okapi BM25 text relevance and document ranking engine.
 * <p>
 * Implements the standard Okapi BM25 scoring function with term frequency saturation (controlled by k1)
 * and document length normalization (controlled by b).
 * Strictly zero 3rd-party runtime dependencies.
 */
public final class Bm25Scorer {

    public static final double DEFAULT_K1 = 1.2;
    public static final double DEFAULT_B = 0.75;

    private final double k1;
    private final double b;

    /**
     * Constructs a Bm25Scorer with default parameters: k1 = 1.2, b = 0.75.
     */
    public Bm25Scorer() {
        this(DEFAULT_K1, DEFAULT_B);
    }

    /**
     * Constructs a Bm25Scorer with custom parameters.
     *
     * @param k1 term frequency saturation parameter (typically between 1.2 and 2.0; must be >= 0)
     * @param b  document length normalization parameter (between 0.0 and 1.0)
     */
    public Bm25Scorer(double k1, double b) {
        if (k1 < 0.0) {
            throw new IllegalArgumentException("k1 must be non-negative: " + k1);
        }
        if (b < 0.0 || b > 1.0) {
            throw new IllegalArgumentException("b must be in range [0.0, 1.0]: " + b);
        }
        this.k1 = k1;
        this.b = b;
    }

    public double k1() {
        return k1;
    }

    public double b() {
        return b;
    }

    /**
     * Computes the Inverse Document Frequency (IDF) using the standard Okapi BM25 / Lucene formulation:
     * {@code ln(1 + (N - n + 0.5) / (n + 0.5))}.
     * Guaranteed to be non-negative.
     *
     * @param totalDocs            total number of documents in the corpus (N)
     * @param docsContainingTerm   number of documents containing the term (n)
     * @return IDF score (0.0 if totalDocs <= 0 or docsContainingTerm <= 0)
     */
    public static double computeIdf(int totalDocs, int docsContainingTerm) {
        if (totalDocs <= 0 || docsContainingTerm <= 0) {
            return 0.0;
        }
        int df = Math.min(docsContainingTerm, totalDocs);
        return Math.log(1.0 + (double) (totalDocs - df + 0.5) / (df + 0.5));
    }

    /**
     * Calculates the BM25 score of a document with respect to a query.
     *
     * @param queryTokens    query terms
     * @param docTokens      document terms
     * @param avgDocLength   average document length across the corpus
     * @param docFrequencies map of term to document frequency in corpus
     * @param totalDocs      total document count in corpus
     * @return BM25 relevance score
     */
    public double score(List<String> queryTokens,
                         List<String> docTokens,
                         double avgDocLength,
                         Map<String, Integer> docFrequencies,
                         int totalDocs) {
        if (queryTokens == null || queryTokens.isEmpty() ||
            docTokens == null || docTokens.isEmpty() ||
            totalDocs <= 0 || docFrequencies == null || docFrequencies.isEmpty()) {
            return 0.0;
        }

        Map<String, Integer> docTf = new HashMap<>();
        for (String token : docTokens) {
            if (token != null && !token.isBlank()) {
                docTf.merge(normalize(token), 1, Integer::sum);
            }
        }

        int docLength = docTokens.size();
        double normAvgLen = avgDocLength > 0 ? avgDocLength : 1.0;
        double lenFactor = 1.0 - b + b * (docLength / normAvgLen);

        double totalScore = 0.0;
        for (String qToken : queryTokens) {
            if (qToken == null || qToken.isBlank()) {
                continue;
            }
            String term = normalize(qToken);
            int tf = docTf.getOrDefault(term, 0);
            if (tf <= 0) {
                continue;
            }

            int df = docFrequencies.getOrDefault(term, 0);
            if (df <= 0) {
                continue;
            }

            double idf = computeIdf(totalDocs, df);
            double numerator = tf * (k1 + 1.0);
            double denominator = tf + k1 * lenFactor;

            if (denominator > 0) {
                totalScore += idf * (numerator / denominator);
            }
        }
        return totalScore;
    }

    /**
     * Ranks a collection of documents against a query in descending order of BM25 score.
     *
     * @param <T>         document ID type
     * @param queryTokens query tokens
     * @param corpus      collection of documents
     * @return sorted list of ScoredDocument instances (highest score first)
     */
    public <T> List<ScoredDocument<T>> rank(List<String> queryTokens, List<Document<T>> corpus) {
        if (corpus == null || corpus.isEmpty()) {
            return List.of();
        }

        int totalDocs = corpus.size();
        long totalTokens = 0;
        Map<String, Integer> docFrequencies = new HashMap<>();

        for (Document<T> doc : corpus) {
            List<String> tokens = doc.tokens();
            totalTokens += tokens.size();
            Set<String> uniqueInDoc = new HashSet<>();
            for (String t : tokens) {
                if (t != null && !t.isBlank()) {
                    uniqueInDoc.add(normalize(t));
                }
            }
            for (String u : uniqueInDoc) {
                docFrequencies.merge(u, 1, Integer::sum);
            }
        }

        double avgDocLength = (double) totalTokens / totalDocs;

        List<ScoredDocument<T>> scoredList = new ArrayList<>(totalDocs);
        for (Document<T> doc : corpus) {
            double s = score(queryTokens, doc.tokens(), avgDocLength, docFrequencies, totalDocs);
            scoredList.add(new ScoredDocument<>(doc, s));
        }

        // Sort descending by score
        scoredList.sort(Comparator.comparingDouble(ScoredDocument<T>::score).reversed());
        return Collections.unmodifiableList(scoredList);
    }

    private static String normalize(String token) {
        return token.toLowerCase(Locale.ROOT).trim();
    }
}
