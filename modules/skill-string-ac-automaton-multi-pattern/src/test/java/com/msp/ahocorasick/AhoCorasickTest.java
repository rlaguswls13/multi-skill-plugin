package com.msp.ahocorasick;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AhoCorasick Automaton Tests")
class AhoCorasickTest {

    @Nested
    @DisplayName("Exact Multi-Pattern Search (Classic Aho-Corasick)")
    class ClassicSearchTests {

        @Test
        @DisplayName("Finds all overlapping matches in classic 'ushers' test case")
        void classicUshersTestCase() {
            AhoCorasick ac = AhoCorasick.builder()
                    .addKeyword("he")
                    .addKeyword("she")
                    .addKeyword("his")
                    .addKeyword("hers")
                    .build();

            List<PatternMatch> matches = ac.findAll("ushers");
            // In "ushers":
            // index 1..4 is "she"
            // index 2..4 is "he"
            // index 2..6 is "hers"
            assertEquals(3, matches.size());

            assertTrue(matches.stream().anyMatch(m -> m.keyword().equals("she") && m.start() == 1 && m.end() == 4));
            assertTrue(matches.stream().anyMatch(m -> m.keyword().equals("he") && m.start() == 2 && m.end() == 4));
            assertTrue(matches.stream().anyMatch(m -> m.keyword().equals("hers") && m.start() == 2 && m.end() == 6));
        }

        @Test
        @DisplayName("Correctly filters non-overlapping matches with longest-match priority")
        void nonOverlappingMatches() {
            AhoCorasick ac = AhoCorasick.builder()
                    .addKeyword("he")
                    .addKeyword("hers")
                    .build();

            List<PatternMatch> nonOverlapping = ac.findNonOverlapping("hers");
            assertEquals(1, nonOverlapping.size());
            assertEquals("hers", nonOverlapping.get(0).keyword());
            assertEquals(0, nonOverlapping.get(0).start());
            assertEquals(4, nonOverlapping.get(0).end());
        }

        @Test
        @DisplayName("Detects presence with containsAny()")
        void containsAny() {
            AhoCorasick ac = AhoCorasick.builder()
                    .addKeywords(List.of("apple", "banana", "orange"))
                    .build();

            assertTrue(ac.containsAny("I like sweet banana pies"));
            assertFalse(ac.containsAny("I like strawberry cupcakes"));
            assertFalse(ac.containsAny(""));
            assertFalse(ac.containsAny(null));
        }

        @Test
        @DisplayName("Finds first match with findFirst()")
        void findFirst() {
            AhoCorasick ac = AhoCorasick.builder()
                    .addKeywords(List.of("cat", "dog", "bird"))
                    .build();

            Optional<PatternMatch> first = ac.findFirst("a black dog chased a cat");
            assertTrue(first.isPresent());
            assertEquals("dog", first.get().keyword());
            assertEquals(8, first.get().start());
            assertEquals(11, first.get().end());
        }
    }

    @Nested
    @DisplayName("Masking and Replacement Utilities")
    class UtilityTests {

        @Test
        @DisplayName("Masks matches with mask character correctly handling overlaps")
        void maskMatches() {
            AhoCorasick ac = AhoCorasick.builder()
                    .addKeyword("he")
                    .addKeyword("she")
                    .addKeyword("hers")
                    .build();

            String masked = ac.maskMatches("ushers", '*');
            // "ushers": indices 1 through 5 are part of she, he, hers
            // index 0 is 'u'
            assertEquals("u*****", masked);
        }

        @Test
        @DisplayName("Replaces non-overlapping matches with custom replacer function")
        void replaceMatches() {
            AhoCorasick ac = AhoCorasick.builder()
                    .addKeyword("foo")
                    .addKeyword("bar")
                    .build();

            String res = ac.replaceMatches("foo and bar are keywords", m -> "[" + m.keyword().toUpperCase() + "]");
            assertEquals("[FOO] and [BAR] are keywords", res);
        }
    }

    @Nested
    @DisplayName("Case-Insensitive and Payloads")
    class CaseAndPayloadTests {

        @Test
        @DisplayName("Matches case-insensitively when configured")
        void caseInsensitive() {
            AhoCorasick ac = AhoCorasick.builder()
                    .addKeyword("admin")
                    .addKeyword("root")
                    .caseInsensitive(true)
                    .build();

            List<PatternMatch> matches = ac.findAll("Hello ADMIN and RoOt user!");
            assertEquals(2, matches.size());
            assertEquals("admin", matches.get(0).keyword());
            assertEquals(6, matches.get(0).start());
            assertEquals(11, matches.get(0).end());

            assertEquals("root", matches.get(1).keyword());
            assertEquals(16, matches.get(1).start());
            assertEquals(20, matches.get(1).end());
        }

        @Test
        @DisplayName("Carries custom payload tags with matches")
        void payloads() {
            record Meta(int id, String category) {}

            AhoCorasick ac = AhoCorasick.builder()
                    .addKeyword("SELECT", new Meta(1, "SQL"))
                    .addKeyword("DELETE", new Meta(2, "SQL"))
                    .build();

            List<PatternMatch> matches = ac.findAll("SELECT * FROM users");
            assertEquals(1, matches.size());
            assertInstanceOf(Meta.class, matches.get(0).payload());
            Meta meta = (Meta) matches.get(0).payload();
            assertEquals(1, meta.id());
            assertEquals("SQL", meta.category());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesTests {

        @Test
        @DisplayName("Empty text and empty automaton handling")
        void emptyAutomaton() {
            AhoCorasick ac = AhoCorasick.builder().build();
            assertEquals(0, ac.patternCount());
            assertTrue(ac.findAll("hello").isEmpty());
            assertFalse(ac.containsAny("hello"));

            AhoCorasick withKw = AhoCorasick.builder().addKeyword("a").build();
            assertTrue(withKw.findAll("").isEmpty());
            assertTrue(withKw.findAll(null).isEmpty());
            assertNull(withKw.maskMatches(null, '*'));
            assertEquals("", withKw.maskMatches("", '*'));
        }

        @Test
        @DisplayName("Throws exception on invalid keyword")
        void invalidKeyword() {
            assertThrows(NullPointerException.class, () -> AhoCorasick.builder().addKeyword(null));
            assertThrows(IllegalArgumentException.class, () -> AhoCorasick.builder().addKeyword(""));
        }
    }
}
