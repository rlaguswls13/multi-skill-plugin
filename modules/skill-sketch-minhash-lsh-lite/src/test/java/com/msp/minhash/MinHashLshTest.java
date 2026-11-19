package com.msp.minhash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MinHash and LSH Similarity Tests")
class MinHashLshTest {

    @Test
    @DisplayName("Exact Jaccard calculates correct mathematical ratio")
    void testExactJaccard() {
        Set<String> setA = Set.of("apple", "banana", "cherry");
        Set<String> setB = Set.of("banana", "cherry", "date");

        // Intersection = 2, Union = 4 -> 2/4 = 0.5
        assertEquals(0.5, MinHasher.exactJaccard(setA, setB), 0.001);
        assertEquals(1.0, MinHasher.exactJaccard(setA, setA), 0.001);
        assertEquals(0.0, MinHasher.exactJaccard(setA, Set.of("grape", "orange")), 0.001);
    }

    @Test
    @DisplayName("MinHash signature serialization round-trip works")
    void testSignatureSerialization() {
        MinHasher hasher = new MinHasher(64, 123L);
        Set<String> tokens = Set.of("the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog");
        MinHashSignature sig = hasher.computeSignature(tokens);

        byte[] bytes = sig.toBytes();
        assertEquals(64 * 8, bytes.length);

        MinHashSignature restored = MinHashSignature.fromBytes(bytes);
        assertEquals(sig, restored);
        assertEquals(1.0, sig.similarity(restored), 0.0001);
    }

    @Test
    @DisplayName("MinHash estimated similarity closely approximates exact Jaccard")
    void testMinHashSimilarityEstimate() {
        MinHasher hasher = new MinHasher(256, 999L);

        Set<String> setA = ShingleGenerator.characterShingles("The quick brown fox jumps over the lazy dog", 3);
        Set<String> setB = ShingleGenerator.characterShingles("The quick brown fox jumped over a lazy dog", 3);

        double exact = MinHasher.exactJaccard(setA, setB);
        double estimated = hasher.estimateSimilarity(setA, setB);

        // Error should be well within standard error bounds (~ 1/sqrt(256) = 0.0625)
        assertEquals(exact, estimated, 0.08);
        assertTrue(estimated > 0.60);
    }

    @Test
    @DisplayName("LSH Index finds near-duplicates in sub-linear candidate query")
    void testLshIndexingAndCandidateSearch() {
        LshIndex index = new LshIndex(new MinHasher(128, 42L), 32, 4);

        String docAi1 = "machine learning and deep neural network models for predictive analytics";
        String docAi2 = "deep neural network models and machine learning for predictive analytics";
        String docCook1 = "how to bake sourdough bread with artisan flour water and active yeast";
        String docCook2 = "artisan sourdough bread baking with flour yeast and long fermentation";

        index.index("doc-ai-1", ShingleGenerator.characterShingles(docAi1, 3));
        index.index("doc-ai-2", ShingleGenerator.characterShingles(docAi2, 3));
        index.index("doc-cook-1", ShingleGenerator.characterShingles(docCook1, 3));
        index.index("doc-cook-2", ShingleGenerator.characterShingles(docCook2, 3));

        assertEquals(4, index.size());

        // Query with text similar to AI docs (near-duplicate: 'analytics' -> 'analysis')
        Set<String> queryAi = ShingleGenerator.characterShingles(
                "machine learning and deep neural network models for predictive analysis", 3);

        Set<String> candidates = index.queryCandidates(queryAi);
        assertTrue(candidates.contains("doc-ai-1"));
        assertTrue(candidates.contains("doc-ai-2"));
        assertFalse(candidates.contains("doc-cook-1"));
        assertFalse(candidates.contains("doc-cook-2"));

        // Query with threshold 0.6
        Set<String> similar = index.querySimilar(queryAi, 0.60);
        assertTrue(similar.contains("doc-ai-1") || similar.contains("doc-ai-2"));
        assertFalse(similar.contains("doc-cook-1"));

        // Query with scores
        List<SimilarityResult> results = index.querySimilarWithScores(queryAi, 0.50);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).similarity() >= 0.50);
    }

    @Test
    @DisplayName("Removing documents dynamically cleans up buckets")
    void testRemoveDocument() {
        LshIndex index = new LshIndex();
        Set<String> tokens = Set.of("alpha", "beta", "gamma", "delta");
        index.index("doc-x", tokens);

        assertEquals(1, index.size());
        assertTrue(index.queryCandidates(tokens).contains("doc-x"));

        boolean removed = index.remove("doc-x");
        assertTrue(removed);
        assertEquals(0, index.size());
        assertFalse(index.queryCandidates(tokens).contains("doc-x"));
    }

    @Test
    @DisplayName("ShingleGenerator produces correct n-grams")
    void testShingleGenerator() {
        String text = "quick brown fox jumps";
        Set<String> charShingles = ShingleGenerator.characterShingles(text, 5);
        assertTrue(charShingles.contains("quick"));
        assertTrue(charShingles.contains("uick "));
        assertTrue(charShingles.contains("jumps"));

        Set<String> wordNGrams = ShingleGenerator.wordNGrams(text, 2);
        assertTrue(wordNGrams.contains("quick brown"));
        assertTrue(wordNGrams.contains("brown fox"));
        assertTrue(wordNGrams.contains("fox jumps"));
        assertEquals(3, wordNGrams.size());
    }
}
