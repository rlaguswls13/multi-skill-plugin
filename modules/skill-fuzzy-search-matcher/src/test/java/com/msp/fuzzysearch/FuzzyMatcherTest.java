package com.msp.fuzzysearch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FuzzyMatcherTest {

    @Test
    @DisplayName("Levenshtein distance calculation test")
    void testLevenshtein() {
        assertEquals(0, FuzzyMatcher.levenshteinDistance("apple", "apple"));
        assertEquals(1, FuzzyMatcher.levenshteinDistance("apple", "apply"));
        assertEquals(3, FuzzyMatcher.levenshteinDistance("kitten", "sitting"));
    }

    @Test
    @DisplayName("Similarity ratio test")
    void testSimilarity() {
        assertEquals(1.0, FuzzyMatcher.similarity("hello", "hello"));
        assertTrue(FuzzyMatcher.similarity("hello", "hella") >= 0.8);
        assertTrue(FuzzyMatcher.similarity("abc", "xyz") <= 0.1);
    }

    @Test
    @DisplayName("Fuzzy search and ranking test")
    void testFuzzySearch() {
        List<String> products = List.of(
                "Apple iPhone 15 Pro",
                "Apple iPad Air",
                "Samsung Galaxy S24",
                "Sony PlayStation 5",
                "Google Pixel 8"
        );

        // Typos: "iphne" -> "Apple iPhone 15 Pro"
        List<FuzzyMatchResult<String>> results = FuzzyMatcher.search("iPhone", products, p -> p, 0.4, 5);
        assertFalse(results.isEmpty());
        assertEquals("Apple iPhone 15 Pro", results.get(0).item());

        // Korean test
        List<String> koreanCompanies = List.of("삼성전자", "현대자동차", "카카오", "네이버", "삼선전자");
        List<FuzzyMatchResult<String>> krResults = FuzzyMatcher.search("삼성전자", koreanCompanies, s -> s, 0.6, 5);
        assertEquals("삼성전자", krResults.get(0).item());
        assertEquals("삼선전자", krResults.get(1).item()); // 오타 유사어
    }
}
