package com.msp.fuzzysearch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FuzzySearchIndexTest {

    @Test
    @DisplayName("Exact match yields score of 1.0")
    void testExactMatchScore() {
        assertEquals(1.0, FuzzySearchIndex.jaccard("kubernetes", "kubernetes", 2), 0.0001);
        assertEquals(1.0, FuzzySearchIndex.dice("kubernetes", "kubernetes", 2), 0.0001);
        assertEquals(1.0, FuzzySearchIndex.jaccard("", "", 2), 0.0001);
        assertEquals(1.0, FuzzySearchIndex.dice("", "", 2), 0.0001);
    }

    @Test
    @DisplayName("Typo tolerance: kubernetes vs kubernets")
    void testTypoTolerance() {
        double jaccardScore = FuzzySearchIndex.jaccard("kubernetes", "kubernets", 2);
        double diceScore = FuzzySearchIndex.dice("kubernetes", "kubernets", 2);

        // "kubernetes" (9 bigrams), "kubernets" (8 bigrams), 7 shared -> Jaccard 7/10=0.7, Dice 14/17=0.8235
        assertEquals(0.70, jaccardScore, 0.01);
        assertEquals(0.8235, diceScore, 0.01);
        assertTrue(jaccardScore > 0.65);
        assertTrue(diceScore > 0.80);
    }

    @Test
    @DisplayName("Multi-language / Korean Unicode n-gram similarity")
    void testKoreanUnicodeNgrams() {
        double jaccard = FuzzySearchIndex.jaccard("스프링 부트 프레임워크", "스프링 부트 프레임웤", 2);
        double dice = FuzzySearchIndex.dice("스프링 부트 프레임워크", "스프링 부트 프레임웤", 2);

        assertTrue(jaccard >= 0.70, "Korean fuzzy jaccard score: " + jaccard);
        assertTrue(dice >= 0.80, "Korean fuzzy dice score: " + dice);

        double greetingJaccard = FuzzySearchIndex.jaccard("안녕하세요", "안녕세효", 2);
        assertTrue(greetingJaccard > 0.15);
    }

    @Test
    @DisplayName("In-memory index search with top-K ranking in descending score order")
    void testTopKRankingDescending() {
        FuzzySearchIndex<Integer> index = new FuzzySearchIndex<>(2, true); // use Dice
        index.index("kubernetes-cluster", 101);
        index.index("kubernets-clstr", 102);
        index.index("docker-container", 103);
        index.index("helm-charts", 104);

        assertEquals(4, index.size());

        List<FuzzyMatchResult<Integer>> results = index.search("kubernetes", 0.4, 3);
        assertFalse(results.isEmpty());
        assertTrue(results.size() <= 3);

        // Verify descending order
        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(results.get(i).score() >= results.get(i + 1).score(),
                    "Results must be sorted descending by score");
        }

        assertEquals(101, results.get(0).item());
        assertEquals("kubernetes-cluster", results.get(0).matchedText());
    }

    @Test
    @DisplayName("Empty index or no match returns empty list")
    void testNoMatchReturnsEmptyList() {
        FuzzySearchIndex<String> index = new FuzzySearchIndex<>();
        assertTrue(index.search("anything", 0.5, 10).isEmpty());

        index.index("apple", "A");
        index.index("banana", "B");

        List<FuzzyMatchResult<String>> matches = index.search("zzzzzzzzz", 0.5, 10);
        assertTrue(matches.isEmpty());
    }

    @Test
    @DisplayName("Invalid parameters validation")
    void testInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new FuzzySearchIndex<>(0, false));
        assertThrows(IllegalArgumentException.class, () -> new FuzzySearchIndex<>(-2, false));

        FuzzySearchIndex<String> index = new FuzzySearchIndex<>();
        assertThrows(NullPointerException.class, () -> index.index(null, "x"));
        assertThrows(NullPointerException.class, () -> index.search(null, 0.5, 5));
        assertThrows(IllegalArgumentException.class, () -> index.search("test", 0.5, 0));
        assertThrows(IllegalArgumentException.class, () -> index.search("test", 0.5, -1));
    }
}
