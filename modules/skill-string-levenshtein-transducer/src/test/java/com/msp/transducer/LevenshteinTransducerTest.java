package com.msp.transducer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LevenshteinTransducerTest {

    @Test
    @DisplayName("Exact match with distance 0")
    void testExactMatch() {
        DictionaryTrie trie = new DictionaryTrie();
        trie.insert("banana");
        trie.insert("apple");
        trie.insert("orange");

        List<FuzzyMatch> matches = LevenshteinTransducer.search(trie, "apple", 0);
        assertEquals(1, matches.size());
        assertEquals("apple", matches.get(0).word());
        assertEquals(0, matches.get(0).distance());

        List<FuzzyMatch> none = LevenshteinTransducer.search(trie, "appl", 0);
        assertTrue(none.isEmpty());
    }

    @Test
    @DisplayName("Single edit distance: substitution, insertion, deletion")
    void testDistanceOne() {
        DictionaryTrie trie = new DictionaryTrie();
        trie.insertAll(List.of("cat", "bat", "rat", "cats", "cut", "dog"));

        // Query "cat" with distance 1:
        // "cat" (0), "bat" (1), "rat" (1), "cut" (1), "cats" (1)
        List<FuzzyMatch> matches = LevenshteinTransducer.search(trie, "cat", 1);
        assertEquals(5, matches.size());

        Set<String> matchedWords = matches.stream().map(FuzzyMatch::word).collect(Collectors.toSet());
        assertTrue(matchedWords.containsAll(List.of("cat", "bat", "rat", "cut", "cats")));
        assertFalse(matchedWords.contains("dog"));
    }

    @Test
    @DisplayName("Distance 2 matching with spelling corrections")
    void testDistanceTwo() {
        DictionaryTrie trie = new DictionaryTrie();
        trie.insertAll(List.of(
                "algorithm",
                "logarithm",
                "alligator",
                "although",
                "altogether"
        ));

        // Query "algoritm" (missing 'h' -> dist 1)
        List<FuzzyMatch> matches = LevenshteinTransducer.search(trie, "algoritm", 2);
        assertFalse(matches.isEmpty());
        assertEquals("algorithm", matches.get(0).word());
        assertEquals(1, matches.get(0).distance());

        // "alligator" has distance > 2 from "algoritm"
        assertFalse(matches.stream().anyMatch(m -> m.word().equals("alligator")));
    }

    @Test
    @DisplayName("findClosest returns best match")
    void testFindClosest() {
        DictionaryTrie trie = new DictionaryTrie();
        trie.insertAll(List.of("development", "developer", "develop", "delivery"));

        Optional<FuzzyMatch> closest = LevenshteinTransducer.findClosest(trie, "devloper", 2);
        assertTrue(closest.isPresent());
        assertEquals("developer", closest.get().word());
        assertEquals(1, closest.get().distance());
    }

    @Test
    @DisplayName("Limit max results")
    void testMaxResults() {
        DictionaryTrie trie = new DictionaryTrie();
        trie.insertAll(List.of("test", "tent", "text", "team", "tell", "term"));

        List<FuzzyMatch> top2 = LevenshteinTransducer.search(trie, "test", 1, 2);
        assertEquals(2, top2.size());
        assertEquals(0, top2.get(0).distance()); // "test"
    }

    @Test
    @DisplayName("Automaton intersection matches naive Levenshtein exactly")
    void testParityWithNaiveLevenshtein() {
        List<String> dictionary = List.of(
                "apple", "application", "apply", "applet", "aptitude", "attitude",
                "banana", "bandana", "banner", "bandage",
                "cat", "cater", "caterpillar", "catalyst",
                "dog", "dodge", "dogmatic",
                "elephant", "elegant", "element", "elevator"
        );

        DictionaryTrie trie = new DictionaryTrie();
        trie.insertAll(dictionary);

        String[] testQueries = {"appl", "banan", "cate", "elega", "xyz", "application"};
        int[] maxDistances = {0, 1, 2, 3};

        for (String query : testQueries) {
            for (int k : maxDistances) {
                List<FuzzyMatch> transducerMatches = LevenshteinTransducer.search(trie, query, k);

                List<FuzzyMatch> naiveMatches = dictionary.stream()
                        .map(word -> new FuzzyMatch(word, LevenshteinTransducer.computeDistance(query, word)))
                        .filter(m -> m.distance() <= k)
                        .sorted()
                        .toList();

                assertEquals(naiveMatches.size(), transducerMatches.size(),
                        "Mismatch in count for query=" + query + ", k=" + k);

                for (int i = 0; i < naiveMatches.size(); i++) {
                    assertEquals(naiveMatches.get(i), transducerMatches.get(i),
                            "Mismatch at index " + i + " for query=" + query + ", k=" + k);
                }
            }
        }
    }

    @Test
    @DisplayName("Edge cases: empty query, empty trie, empty word")
    void testEdgeCases() {
        DictionaryTrie emptyTrie = new DictionaryTrie();
        assertTrue(LevenshteinTransducer.search(emptyTrie, "test", 2).isEmpty());

        DictionaryTrie trie = new DictionaryTrie();
        trie.insert("");
        trie.insert("a");

        List<FuzzyMatch> matches = LevenshteinTransducer.search(trie, "", 1);
        assertEquals(2, matches.size());
        assertEquals("", matches.get(0).word());
        assertEquals(0, matches.get(0).distance());
        assertEquals("a", matches.get(1).word());
        assertEquals(1, matches.get(1).distance());

        assertThrows(IllegalArgumentException.class, () ->
                LevenshteinTransducer.search(trie, "test", -1));
    }
}
