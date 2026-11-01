package com.msp.autocomplete;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class AutocompleteTrieTest {

    @Test
    @DisplayName("Prefix suggestion returns top-K results ordered descending by score")
    void testPrefixSuggestionTopK() {
        AutocompleteTrie trie = new AutocompleteTrie();
        trie.insert("apple", 10.0);
        trie.insert("app", 5.0);
        trie.insert("application", 25.0);
        trie.insert("apply", 15.0);
        trie.insert("aptitude", 30.0);

        List<AutocompleteItem> results = trie.suggest("app", 3);
        assertEquals(3, results.size());
        assertEquals("application", results.get(0).text());
        assertEquals(25.0, results.get(0).score());
        assertEquals("apply", results.get(1).text());
        assertEquals(15.0, results.get(1).score());
        assertEquals("apple", results.get(2).text());
        assertEquals(10.0, results.get(2).score());

        // 'aptitude' does not start with 'app'
        assertTrue(results.stream().noneMatch(r -> r.text().equals("aptitude")));
    }

    @Test
    @DisplayName("Score increment updates existing score or initializes new word")
    void testIncrementScore() {
        AutocompleteTrie trie = new AutocompleteTrie();

        trie.incrementScore("search", 2.5);
        assertEquals(2.5, trie.getScore("search").orElseThrow());

        trie.incrementScore("search", 3.5);
        assertEquals(6.0, trie.getScore("search").orElseThrow());

        assertEquals(1, trie.size());
    }

    @Test
    @DisplayName("getScore returns exact word score and empty for non-existent or prefix words")
    void testGetScore() {
        AutocompleteTrie trie = new AutocompleteTrie();
        trie.insert("database", 42.0);

        assertEquals(OptionalDouble.of(42.0), trie.getScore("database"));
        assertEquals(OptionalDouble.empty(), trie.getScore("data"));
        assertEquals(OptionalDouble.empty(), trie.getScore("nonexistent"));
    }

    @Test
    @DisplayName("Deletion removes words and cleans up trie size")
    void testRemoveWord() {
        AutocompleteTrie trie = new AutocompleteTrie();
        trie.insert("cloud", 10.0);
        trie.insert("cloudy", 20.0);
        assertEquals(2, trie.size());

        assertTrue(trie.remove("cloud"));
        assertEquals(1, trie.size());
        assertEquals(OptionalDouble.empty(), trie.getScore("cloud"));

        // 'cloudy' should still exist
        assertEquals(OptionalDouble.of(20.0), trie.getScore("cloudy"));

        assertFalse(trie.remove("cloud")); // Already removed
        assertFalse(trie.remove("unknown"));
    }

    @Test
    @DisplayName("Multi-language Korean Hangul syllables prefix autocomplete")
    void testKoreanAutocomplete() {
        AutocompleteTrie trie = new AutocompleteTrie();
        trie.insert("한국", 100.0);
        trie.insert("한국어", 150.0);
        trie.insert("한류", 80.0);
        trie.insert("한복", 70.0);
        trie.insert("해외", 50.0);

        List<AutocompleteItem> suggestions = trie.suggest("한", 3);
        assertEquals(3, suggestions.size());
        assertEquals("한국어", suggestions.get(0).text());
        assertEquals(150.0, suggestions.get(0).score());
        assertEquals("한국", suggestions.get(1).text());
        assertEquals(100.0, suggestions.get(1).score());
        assertEquals("한류", suggestions.get(2).text());
        assertEquals(80.0, suggestions.get(2).score());

        List<AutocompleteItem> hangukSuggestions = trie.suggest("한국", 5);
        assertEquals(2, hangukSuggestions.size());
        assertEquals("한국어", hangukSuggestions.get(0).text());
        assertEquals("한국", hangukSuggestions.get(1).text());
    }

    @Test
    @DisplayName("Empty or null prefix returns top words overall in the trie")
    void testEmptyPrefixReturnsTopOverall() {
        AutocompleteTrie trie = new AutocompleteTrie();
        trie.insert("java", 50.0);
        trie.insert("python", 40.0);
        trie.insert("kotlin", 60.0);
        trie.insert("rust", 70.0);

        List<AutocompleteItem> top2 = trie.suggest("", 2);
        assertEquals(2, top2.size());
        assertEquals("rust", top2.get(0).text());
        assertEquals("kotlin", top2.get(1).text());

        List<AutocompleteItem> topNull = trie.suggest(null, 1);
        assertEquals(1, topNull.size());
        assertEquals("rust", topNull.get(0).text());
    }

    @Test
    @DisplayName("Concurrent reads and writes operate correctly without race conditions")
    void testConcurrentOperations() throws Exception {
        AutocompleteTrie trie = new AutocompleteTrie();
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    latch.await();
                    for (int j = 0; j < 100; j++) {
                        String word = "item-" + (j % 20);
                        trie.incrementScore(word, 1.0);
                        trie.suggest("item", 5);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(20, trie.size());
        List<AutocompleteItem> top5 = trie.suggest("item", 5);
        assertEquals(5, top5.size());
        for (AutocompleteItem item : top5) {
            assertEquals(threads * 5.0, item.score(), 0.001); // 100/20 = 5 increments per thread
        }
    }

    @Test
    @DisplayName("Clear resets trie size and suggestions")
    void testClear() {
        AutocompleteTrie trie = new AutocompleteTrie();
        trie.insert("alpha", 1.0);
        trie.insert("beta", 2.0);
        assertEquals(2, trie.size());

        trie.clear();
        assertEquals(0, trie.size());
        assertTrue(trie.suggest("a", 10).isEmpty());
        assertEquals(OptionalDouble.empty(), trie.getScore("alpha"));
    }
}
