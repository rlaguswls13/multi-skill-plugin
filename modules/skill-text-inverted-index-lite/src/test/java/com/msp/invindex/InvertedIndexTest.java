package com.msp.invindex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InvertedIndexTest {

    private InvertedIndex<String> index;

    @BeforeEach
    void setUp() {
        index = new InvertedIndex<>();
        index.setFieldBoost("title", 2.0);
        index.setFieldBoost("body", 1.0);
    }

    @Test
    @DisplayName("Indexes documents and retrieves by single term")
    void testBasicIndexingAndSearch() {
        index.addDocument("doc1", "Java Concurrency Guide", Map.of(
                "title", "Java Concurrency in Practice",
                "body", "Threads, virtual threads, and synchronization in Java."
        ));

        index.addDocument("doc2", "Python Async Guide", Map.of(
                "title", "Python Asyncio Programming",
                "body", "Event loop, coroutines, and async programming."
        ));

        List<SearchResult<String>> results = index.search("java", 5);
        assertEquals(1, results.size());
        assertEquals("doc1", results.get(0).docId());
        assertTrue(results.get(0).score() > 0.0);
        assertTrue(results.get(0).matchedTerms().containsKey("java"));
    }

    @Test
    @DisplayName("Evaluates Boolean AND, OR, and NOT queries with parentheses")
    void testBooleanQueryEvaluation() {
        index.addDocument("d1", "Data 1", Map.of(
                "body", "Modern java architecture with high resilience and high performance"
        ));
        index.addDocument("d2", "Data 2", Map.of(
                "body", "Java concurrency with virtual threads and async nonblocking IO"
        ));
        index.addDocument("d3", "Data 3", Map.of(
                "body", "Legacy java application marked deprecated due to old concurrency models"
        ));
        index.addDocument("d4", "Data 4", Map.of(
                "body", "Rust systems programming with memory safety"
        ));

        // Query: java AND (resilience OR concurrency) AND NOT deprecated
        // d1 matches java & resilience
        // d2 matches java & concurrency
        // d3 matches java & concurrency but has deprecated -> excluded!
        // d4 does not match java
        List<SearchResult<String>> results = index.search("java AND (resilience OR concurrency) AND NOT deprecated", 10);

        assertEquals(2, results.size());
        List<String> docIds = results.stream().map(SearchResult::docId).toList();
        assertTrue(docIds.contains("d1"));
        assertTrue(docIds.contains("d2"));
        assertFalse(docIds.contains("d3"), "d3 must be excluded because it contains 'deprecated'");
        assertFalse(docIds.contains("d4"));
    }

    @Test
    @DisplayName("Field boosting gives higher BM25 score to title matches over body matches")
    void testFieldBoostRanking() {
        // docA has "microservices" in title (boost 2.0)
        index.addDocument("docA", "Title Match", Map.of(
                "title", "Microservices Design Patterns",
                "body", "Software architecture fundamentals."
        ));

        // docB has "microservices" only in body (boost 1.0)
        index.addDocument("docB", "Body Match", Map.of(
                "title", "Software Architecture Fundamentals",
                "body", "Modern cloud microservices design patterns."
        ));

        List<SearchResult<String>> results = index.search("microservices", 5);
        assertEquals(2, results.size());
        assertEquals("docA", results.get(0).docId(), "Title match with 2.0 boost should rank first");
        assertTrue(results.get(0).score() > results.get(1).score());
    }

    @Test
    @DisplayName("Document removal cleans up terms and index entries")
    void testDocumentRemoval() {
        index.addDocument("doc1", "Doc 1", Map.of("body", "kubernetes cluster orchestration"));
        assertEquals(1, index.documentCount());

        List<SearchResult<String>> before = index.search("kubernetes", 5);
        assertEquals(1, before.size());

        boolean removed = index.removeDocument("doc1");
        assertTrue(removed);
        assertEquals(0, index.documentCount());

        List<SearchResult<String>> after = index.search("kubernetes", 5);
        assertTrue(after.isEmpty());
    }

    @Test
    @DisplayName("Implicit AND is supported for multi-term queries")
    void testImplicitAnd() {
        index.addDocument("doc1", "Doc 1", Map.of("body", "distributed database consensus"));
        index.addDocument("doc2", "Doc 2", Map.of("body", "distributed cache memory"));

        List<SearchResult<String>> results = index.search("distributed database", 5);
        assertEquals(1, results.size());
        assertEquals("doc1", results.get(0).docId());
    }

    @Test
    @DisplayName("Handles empty, blank, or unmatched queries gracefully")
    void testEdgeCases() {
        index.addDocument("doc1", "Doc 1", Map.of("body", "simple test"));

        assertTrue(index.search("", 5).isEmpty());
        assertTrue(index.search("   ", 5).isEmpty());
        assertTrue(index.search("nonexistent", 5).isEmpty());
    }
}
