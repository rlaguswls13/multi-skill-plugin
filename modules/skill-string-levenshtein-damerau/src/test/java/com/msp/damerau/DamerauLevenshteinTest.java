package com.msp.damerau;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Damerau-Levenshtein Edit Distance & Similarity Tests")
class DamerauLevenshteinTest {

    @Test
    @DisplayName("Adjacent character transposition costs exactly 1")
    void testAdjacentTransposition() {
        // Simple 2-character swap
        assertEquals(1, DamerauLevenshtein.distance("ab", "ba"));
        assertEquals(1, DamerauLevenshtein.distance("CA", "AC"));

        // Word middle adjacent swap ("ei" <-> "ie")
        assertEquals(1, DamerauLevenshtein.distance("receive", "recieve"));
        assertEquals(1, DamerauLevenshtein.distance("casual", "causal"));

        // Word prefix swap
        assertEquals(1, DamerauLevenshtein.distance("tehs", "thes"));
    }

    @Test
    @DisplayName("Standard edit operations: insertion, deletion, substitution")
    void testStandardEditOperations() {
        // Substitution: 1
        assertEquals(1, DamerauLevenshtein.distance("cat", "hat"));

        // Insertion: 1
        assertEquals(1, DamerauLevenshtein.distance("cat", "cats"));

        // Deletion: 1
        assertEquals(1, DamerauLevenshtein.distance("cats", "cat"));

        // Multiple operations: "kitten" -> "sitting" = 3
        assertEquals(3, DamerauLevenshtein.distance("kitten", "sitting"));

        // "saturday" -> "sunday" = 3
        assertEquals(3, DamerauLevenshtein.distance("saturday", "sunday"));
    }

    @Test
    @DisplayName("Identical and empty string boundary tests")
    void testBoundaryConditions() {
        // Identical
        assertEquals(0, DamerauLevenshtein.distance("", ""));
        assertEquals(0, DamerauLevenshtein.distance("hello", "hello"));
        assertEquals(1.0, DamerauLevenshtein.similarity("", ""), 1e-6);
        assertEquals(1.0, DamerauLevenshtein.similarity("hello", "hello"), 1e-6);

        // One empty
        assertEquals(5, DamerauLevenshtein.distance("hello", ""));
        assertEquals(5, DamerauLevenshtein.distance("", "hello"));
        assertEquals(0.0, DamerauLevenshtein.similarity("hello", ""), 1e-6);
        assertEquals(0.0, DamerauLevenshtein.similarity("", "hello"), 1e-6);
    }

    @Test
    @DisplayName("Similarity ratio calculations")
    void testSimilarityCalculation() {
        // "ab" and "ba" has distance 1, maxLen 2 -> similarity = 0.5
        assertEquals(0.5, DamerauLevenshtein.similarity("ab", "ba"), 1e-6);

        // "hello" and "hallo" has distance 1, maxLen 5 -> similarity = 0.8
        assertEquals(0.8, DamerauLevenshtein.similarity("hello", "hallo"), 1e-6);

        // Completely different
        assertTrue(DamerauLevenshtein.similarity("abc", "xyz") <= 0.01);
    }

    @Test
    @DisplayName("isWithinDistance with length difference and early pruning")
    void testIsWithinDistance() {
        assertTrue(DamerauLevenshtein.isWithinDistance("ab", "ba", 1));
        assertFalse(DamerauLevenshtein.isWithinDistance("ab", "ba", 0));

        // Negative max distance is always false
        assertFalse(DamerauLevenshtein.isWithinDistance("hello", "hello", -1));

        // Exact match with distance 0
        assertTrue(DamerauLevenshtein.isWithinDistance("test", "test", 0));
        assertFalse(DamerauLevenshtein.isWithinDistance("test", "fest", 0));

        // Length difference > maxDistance pruned immediately
        assertFalse(DamerauLevenshtein.isWithinDistance("a", "aaaaaa", 2));
        assertTrue(DamerauLevenshtein.isWithinDistance("a", "aaaa", 3));

        // Far words pruned early
        assertFalse(DamerauLevenshtein.isWithinDistance("apple", "banana", 1));
        assertTrue(DamerauLevenshtein.isWithinDistance("kitten", "sitting", 3));
        assertFalse(DamerauLevenshtein.isWithinDistance("kitten", "sitting", 2));
    }

    @Test
    @DisplayName("Null input validation")
    void testNullValidation() {
        assertThrows(IllegalArgumentException.class, () -> DamerauLevenshtein.distance(null, "test"));
        assertThrows(IllegalArgumentException.class, () -> DamerauLevenshtein.distance("test", null));
        assertThrows(IllegalArgumentException.class, () -> DamerauLevenshtein.similarity(null, "test"));
        assertThrows(IllegalArgumentException.class, () -> DamerauLevenshtein.isWithinDistance(null, "test", 2));
    }
}
