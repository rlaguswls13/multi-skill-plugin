package com.msp.similarity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StringSimilarity Unit Tests")
class StringSimilarityTest {

    @Nested
    @DisplayName("Levenshtein Distance & Similarity")
    class LevenshteinTests {

        @Test
        @DisplayName("Identical strings have distance 0 and similarity 1.0")
        void testExactMatch() {
            assertEquals(0, LevenshteinDistance.distance("kitten", "kitten"));
            assertEquals(1.0, LevenshteinDistance.similarity("kitten", "kitten"));
            assertEquals(0, LevenshteinDistance.distance("", ""));
            assertEquals(1.0, LevenshteinDistance.similarity("", ""));
        }

        @Test
        @DisplayName("Standard edit distance calculations")
        void testStandardDistance() {
            // kitten -> sitting (replace k->s, e->i, add g: 3 ops)
            assertEquals(3, LevenshteinDistance.distance("kitten", "sitting"));
            // apple -> appple (insert p: 1 op)
            assertEquals(1, LevenshteinDistance.distance("apple", "appple"));
            assertEquals(5.0 / 6.0, LevenshteinDistance.similarity("apple", "appple"), 0.001);
        }

        @Test
        @DisplayName("Completely different strings")
        void testCompletelyDifferent() {
            assertEquals(3, LevenshteinDistance.distance("abc", "xyz"));
            assertEquals(0.0, LevenshteinDistance.similarity("abc", "xyz"));
        }

        @Test
        @DisplayName("Empty string comparisons")
        void testEmptyComparisons() {
            assertEquals(5, LevenshteinDistance.distance("hello", ""));
            assertEquals(0.0, LevenshteinDistance.similarity("hello", ""));
            assertEquals(5, LevenshteinDistance.distance("", "hello"));
            assertEquals(0.0, LevenshteinDistance.similarity("", "hello"));
        }
    }

    @Nested
    @DisplayName("Jaro-Winkler Distance & Prefix Bonus")
    class JaroWinklerTests {

        @Test
        @DisplayName("Exact matches yield 1.0")
        void testExactMatch() {
            assertEquals(1.0, JaroWinklerDistance.similarity("MARTHA", "MARTHA"));
            assertEquals(1.0, JaroWinklerDistance.similarity("", ""));
        }

        @Test
        @DisplayName("Disjoint strings yield 0.0")
        void testDisjoint() {
            assertEquals(0.0, JaroWinklerDistance.similarity("abc", "xyz"));
        }

        @Test
        @DisplayName("Prefix bonus test: Winkler score is higher when common prefix exists")
        void testPrefixBonus() {
            // "DIXON" vs "DICKSONX" shares prefix 'D' and characters
            double jaro = JaroWinklerDistance.jaroDistance("DIXON", "DICKSONX");
            double winkler = JaroWinklerDistance.similarity("DIXON", "DICKSONX");
            assertTrue(winkler > jaro, "Winkler score should be boosted by common prefix 'D'");

            // MARTHA vs MARHTA has common prefix "MAR" (3 chars)
            double jaroMartha = JaroWinklerDistance.jaroDistance("MARTHA", "MARHTA");
            double winklerMartha = JaroWinklerDistance.similarity("MARTHA", "MARHTA");
            assertTrue(winklerMartha > jaroMartha);
            assertTrue(winklerMartha > 0.95);
        }

        @Test
        @DisplayName("Typo resilience")
        void testTypoResilience() {
            double score = JaroWinklerDistance.similarity("apple", "appple");
            assertTrue(score > 0.9, "Score for 'appple' should be very high (> 0.9)");
        }
    }

    @Nested
    @DisplayName("Cosine Similarity (N-gram)")
    class CosineTests {

        @Test
        @DisplayName("Identical strings have cosine 1.0")
        void testExactMatch() {
            assertEquals(1.0, CosineSimilarity.similarity("database", "database"));
            assertEquals(1.0, CosineSimilarity.similarity("", ""));
        }

        @Test
        @DisplayName("Completely different strings have cosine 0.0")
        void testDisjoint() {
            assertEquals(0.0, CosineSimilarity.similarity("abc", "xyz"));
        }

        @Test
        @DisplayName("Similar strings with shared n-grams")
        void testSharedNgrams() {
            double score = CosineSimilarity.similarity("night", "nacht");
            assertTrue(score > 0.0 && score < 1.0);

            // Shuffled words / phrases
            double scoreSentence = CosineSimilarity.similarity("hello world", "world hello");
            assertTrue(scoreSentence > 0.7);
        }

        @Test
        @DisplayName("Short strings fallback cleanly")
        void testShortStrings() {
            double score = CosineSimilarity.similarity("a", "a", 2);
            assertEquals(1.0, score);
            double scoreDiff = CosineSimilarity.similarity("a", "b", 2);
            assertEquals(0.0, scoreDiff);
        }
    }

    @Nested
    @DisplayName("TypoSuggester & Auto-Suggestion")
    class TypoSuggesterTests {

        private final List<String> dictionary = List.of(
                "apple", "banana", "orange", "pineapple", "grape", "watermelon", "strawberry"
        );

        @Test
        @DisplayName("Suggests 'apple' for typo 'appple'")
        void testTypoApple() {
            TypoSuggester suggester = new TypoSuggester();
            Optional<String> best = suggester.suggestBest("appple", dictionary);

            assertTrue(best.isPresent());
            assertEquals("apple", best.get());
        }

        @Test
        @DisplayName("Suggests top candidates ordered by similarity")
        void testTopCandidates() {
            TypoSuggester suggester = new TypoSuggester(TypoSuggester.Algorithm.JARO_WINKLER, 0.5);
            List<TypoSuggester.Suggestion> suggestions = suggester.suggest("app", dictionary, 3);

            assertFalse(suggestions.isEmpty());
            assertEquals("apple", suggestions.getFirst().candidate());
        }

        @Test
        @DisplayName("No suggestions returned if below threshold")
        void testNoMatches() {
            TypoSuggester suggester = new TypoSuggester(TypoSuggester.Algorithm.LEVENSHTEIN, 0.95);
            Optional<String> best = suggester.suggestBest("zzzzzzzzz", dictionary);

            assertTrue(best.isEmpty());
        }
    }

    @Nested
    @DisplayName("Facade Interface")
    class FacadeTests {

        @Test
        @DisplayName("Facade delegates correctly")
        void testFacade() {
            assertEquals(1, StringSimilarity.levenshteinDistance("cat", "hat"));
            assertTrue(StringSimilarity.levenshtein("cat", "hat") > 0.6);
            assertTrue(StringSimilarity.jaroWinkler("cat", "car") > 0.7);
            assertTrue(StringSimilarity.cosine("information", "informative") > 0.6);

            Optional<String> best = StringSimilarity.suggestBest("computr", List.of("computer", "commute", "company"));
            assertTrue(best.isPresent());
            assertEquals("computer", best.get());
        }

        @Test
        @DisplayName("Null safety in facade")
        void testNullSafety() {
            assertThrows(NullPointerException.class, () -> StringSimilarity.levenshtein(null, "test"));
            assertThrows(NullPointerException.class, () -> StringSimilarity.jaroWinkler("test", null));
            assertThrows(NullPointerException.class, () -> StringSimilarity.cosine(null, null));
        }
    }
}
