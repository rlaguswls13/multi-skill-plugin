package com.msp.simhash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimHashTest {

    @Test
    @DisplayName("Identical documents should yield identical SimHash (Hamming distance 0)")
    void testIdenticalDocuments() {
        String doc1 = "The quick brown fox jumps over the lazy dog in the sunny morning.";
        String doc2 = "The quick brown fox jumps over the lazy dog in the sunny morning.";

        long hash1 = SimHash.compute(doc1);
        long hash2 = SimHash.compute(doc2);

        assertEquals(hash1, hash2);
        assertEquals(0, SimHash.hammingDistance(hash1, hash2));
        assertEquals(1.0, SimHash.similarity(hash1, hash2));
        assertTrue(SimHash.isNearDuplicate(doc1, doc2, 0));
    }

    @Test
    @DisplayName("Near-duplicate documents with minor changes should have small Hamming distance")
    void testNearDuplicateDocuments() {
        String original = "Java 21 introduces virtual threads, pattern matching, record patterns, and sequenced collections for high throughput.";
        // Changed one word: 'throughput' -> 'performance', added 'modern'
        String modified = "Java 21 introduces virtual threads, modern pattern matching, record patterns, and sequenced collections for high performance.";

        long hash1 = SimHash.compute(original);
        long hash2 = SimHash.compute(modified);

        int distance = SimHash.hammingDistance(hash1, hash2);
        assertTrue(distance <= 8, "Expected small Hamming distance for near duplicate, got " + distance);
        assertTrue(SimHash.similarity(hash1, hash2) >= 0.85, "Expected high bit similarity >= 85%");

        // Single word variation in a paragraph should have even smaller distance (<= 6)
        String p1 = "The distributed storage cluster provides high availability, fault tolerance, automatic data replication, consensus coordination, and fast analytical queries across global regions.";
        String p2 = "The distributed storage cluster provides high availability, fault tolerance, automatic data replication, consensus coordination, and quick analytical queries across global regions.";
        int pDistance = SimHash.hammingDistance(SimHash.compute(p1), SimHash.compute(p2));
        assertTrue(pDistance <= 6, "Single word replacement should result in very small distance, got: " + pDistance);
    }

    @Test
    @DisplayName("Completely different documents should have high Hamming distance")
    void testCompletelyDifferentDocuments() {
        String doc1 = "Quantum computing uses qubits and quantum entanglement for exponential parallel calculations.";
        String doc2 = "Baking sourdough bread requires organic whole wheat flour, natural wild yeast starter, water and sea salt.";

        long hash1 = SimHash.compute(doc1);
        long hash2 = SimHash.compute(doc2);

        int distance = SimHash.hammingDistance(hash1, hash2);
        assertTrue(distance >= 15, "Expected high distance for completely distinct topics, got: " + distance);
        assertFalse(SimHash.isNearDuplicate(doc1, doc2, 3));
    }

    @Test
    @DisplayName("Empty or whitespace documents should produce 0L")
    void testEmptyDocument() {
        assertEquals(0L, SimHash.compute(""));
        assertEquals(0L, SimHash.compute("   "));
        assertEquals(0L, SimHash.compute(null));
    }

    @Test
    @DisplayName("Custom tokenizers (char n-grams, word n-grams)")
    void testTokenizers() {
        String text = "machine learning models";
        List<String> char3Grams = Tokenizer.charNGrams(3).tokenize(text);
        assertTrue(char3Grams.contains("mac"));
        assertTrue(char3Grams.contains("ach"));

        List<String> biGrams = Tokenizer.wordNGrams(2).tokenize(text);
        assertEquals(List.of("machine_learning", "learning_models"), biGrams);

        long hash = SimHash.compute(text, Tokenizer.wordNGrams(2));
        assertNotEquals(0L, hash);
    }

    @Test
    @DisplayName("SimHashTable should index and retrieve near duplicates in sub-linear time")
    void testSimHashTable() {
        SimHashTable<String> index = new SimHashTable<>(4);

        String docA = "Distributed consensus algorithm Paxos and Raft provide fault-tolerant replication across multiple server nodes.";
        String docA_variant = "Distributed consensus algorithm Paxos and Raft provide fault-tolerant replication across various server nodes.";
        String docB = "Cooking Italian pasta requires boiling salted water, semolina spaghetti, and fresh garlic olive oil sauce.";

        long hashA = index.put(docA, "DocA");
        index.put(docB, "DocB");

        assertEquals(2, index.size());

        // Search near duplicates for docA_variant with maxDistance 3
        List<SimHashTable.SearchResult<String>> results = index.findNearDuplicates(docA_variant, 4);

        assertFalse(results.isEmpty());
        assertEquals("DocA", results.get(0).value());
        assertTrue(results.get(0).distance() <= 4);

        // Remove DocA
        boolean removed = index.remove(hashA, "DocA");
        assertTrue(removed);
        assertEquals(1, index.size());

        List<SimHashTable.SearchResult<String>> afterRemove = index.findNearDuplicates(docA_variant, 4);
        assertTrue(afterRemove.isEmpty());
    }
}
