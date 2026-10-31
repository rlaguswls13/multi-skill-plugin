package com.msp.bm25;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bm25ScorerTest {

    @Test
    @DisplayName("Default constructor should use standard parameters k1=1.2, b=0.75")
    void testDefaultConstructor() {
        Bm25Scorer scorer = new Bm25Scorer();
        assertEquals(1.2, scorer.k1(), 1e-6);
        assertEquals(0.75, scorer.b(), 1e-6);
    }

    @Test
    @DisplayName("Invalid parameters should throw IllegalArgumentException")
    void testParameterValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Bm25Scorer(-0.1, 0.75));
        assertThrows(IllegalArgumentException.class, () -> new Bm25Scorer(1.2, -0.01));
        assertThrows(IllegalArgumentException.class, () -> new Bm25Scorer(1.2, 1.01));
    }

    @Test
    @DisplayName("computeIdf should calculate correct non-negative scores")
    void testComputeIdf() {
        assertEquals(0.0, Bm25Scorer.computeIdf(0, 5));
        assertEquals(0.0, Bm25Scorer.computeIdf(10, 0));

        // Rare term (1 out of 100) vs frequent term (90 out of 100)
        double rareIdf = Bm25Scorer.computeIdf(100, 1);
        double frequentIdf = Bm25Scorer.computeIdf(100, 90);

        assertTrue(rareIdf > frequentIdf, "Rare terms must have strictly higher IDF than frequent terms");
        assertTrue(frequentIdf > 0.0, "Frequent term IDF must remain strictly positive");
    }

    @Test
    @DisplayName("Higher TF should yield higher score (Relevance Ranking)")
    void testHigherTfYieldsHigherScore() {
        Bm25Scorer scorer = new Bm25Scorer(1.2, 0.75);

        Document<String> docLowTf = Document.of("doc1", "java", "code", "programming", "software");
        Document<String> docHighTf = Document.of("doc2", "java", "java", "java", "software");

        List<Document<String>> corpus = List.of(docLowTf, docHighTf);
        List<ScoredDocument<String>> ranked = scorer.rank(List.of("java"), corpus);

        assertEquals(2, ranked.size());
        assertEquals("doc2", ranked.get(0).document().id(), "Document with 3 'java' occurrences must rank higher than document with 1");
        assertEquals("doc1", ranked.get(1).document().id());
        assertTrue(ranked.get(0).score() > ranked.get(1).score());
    }

    @Test
    @DisplayName("TF saturation: score gain diminishes with increasing TF (governed by k1)")
    void testTfSaturationWithK1() {
        Bm25Scorer scorer = new Bm25Scorer(1.2, 0.0); // disable length norm (b=0) to isolate TF

        Map<String, Integer> df = Map.of("apple", 1);
        int totalDocs = 10;
        double avgDl = 10.0;

        // TF = 1 vs TF = 2 (delta 1)
        double score1 = scorer.score(List.of("apple"), List.of("apple"), avgDl, df, totalDocs);
        double score2 = scorer.score(List.of("apple"), List.of("apple", "apple"), avgDl, df, totalDocs);
        double gain1To2 = score2 - score1;

        // TF = 10 vs TF = 11 (delta 1)
        List<String> tenTokens = java.util.Collections.nCopies(10, "apple");
        List<String> elevenTokens = java.util.Collections.nCopies(11, "apple");
        double score10 = scorer.score(List.of("apple"), tenTokens, avgDl, df, totalDocs);
        double score11 = scorer.score(List.of("apple"), elevenTokens, avgDl, df, totalDocs);
        double gain10To11 = score11 - score10;

        assertTrue(gain1To2 > gain10To11, "Marginal score gain from TF=1->2 must be much larger than TF=10->11 due to saturation");
    }

    @Test
    @DisplayName("Document length normalization (b): shorter document with same TF gets higher score")
    void testDocumentLengthNormalizationWithB() {
        Bm25Scorer scorer = new Bm25Scorer(1.2, 0.75); // b=0.75 actively penalizes long documents

        // Both docs have exactly one occurrence of "keyword"
        // docShort length: 2 tokens
        Document<String> docShort = Document.of("shortDoc", "keyword", "extra");
        // docLong length: 10 tokens
        Document<String> docLong = Document.of("longDoc",
                "keyword", "noise1", "noise2", "noise3", "noise4", "noise5", "noise6", "noise7", "noise8");

        List<Document<String>> corpus = List.of(docShort, docLong);
        List<ScoredDocument<String>> ranked = scorer.rank(List.of("keyword"), corpus);

        assertEquals("shortDoc", ranked.get(0).document().id(), "Shorter document with same TF must have higher score due to length normalization");
        assertTrue(ranked.get(0).score() > ranked.get(1).score());
    }

    @Test
    @DisplayName("Zero match handling should produce zero score")
    void testZeroMatchHandling() {
        Bm25Scorer scorer = new Bm25Scorer();

        Document<String> doc1 = Document.of("doc1", "spring", "boot", "framework");
        Document<String> doc2 = Document.of("doc2", "quarkus", "micronaut");

        List<Document<String>> corpus = List.of(doc1, doc2);
        List<ScoredDocument<String>> ranked = scorer.rank(List.of("python"), corpus);

        assertEquals(2, ranked.size());
        assertEquals(0.0, ranked.get(0).score(), 1e-6);
        assertEquals(0.0, ranked.get(1).score(), 1e-6);
    }

    @Test
    @DisplayName("Ranking should sort corpus in strictly descending order")
    void testSortingDescendingOrder() {
        Bm25Scorer scorer = new Bm25Scorer();

        Document<Integer> d1 = Document.of(1, "database", "sql", "query", "index");
        Document<Integer> d2 = Document.of(2, "database", "database", "query", "database");
        Document<Integer> d3 = Document.of(3, "network", "socket", "http");
        Document<Integer> d4 = Document.of(4, "database", "storage");

        List<ScoredDocument<Integer>> ranked = scorer.rank(List.of("database", "query"), List.of(d1, d2, d3, d4));

        assertEquals(4, ranked.size());
        for (int i = 0; i < ranked.size() - 1; i++) {
            assertTrue(ranked.get(i).score() >= ranked.get(i + 1).score(),
                    "Results must be in descending order: " + ranked.get(i).score() + " >= " + ranked.get(i + 1).score());
        }
        assertEquals(2, ranked.get(0).document().id(), "Doc 2 has highest frequency of both terms");
        assertEquals(3, ranked.get(3).document().id(), "Doc 3 has zero matches and should rank last");
        assertEquals(0.0, ranked.get(3).score(), 1e-6);
    }

    @Test
    @DisplayName("Edge cases: empty query, empty corpus, case insensitivity")
    void testEdgeCases() {
        Bm25Scorer scorer = new Bm25Scorer();

        // Empty corpus
        List<ScoredDocument<String>> emptyResult = scorer.rank(List.of("test"), List.of());
        assertTrue(emptyResult.isEmpty());

        // Empty query
        Document<String> d = Document.of("doc", "sample", "text");
        List<ScoredDocument<String>> rankedEmptyQ = scorer.rank(List.of(), List.of(d));
        assertEquals(1, rankedEmptyQ.size());
        assertEquals(0.0, rankedEmptyQ.get(0).score());

        // Case insensitivity
        Document<String> dMixed = Document.of("docMixed", "ElasticSearch", "LUCENE");
        List<ScoredDocument<String>> rankedCase = scorer.rank(List.of("elasticsearch"), List.of(dMixed));
        assertTrue(rankedCase.get(0).score() > 0.0);
    }
}
