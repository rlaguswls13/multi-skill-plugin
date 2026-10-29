package com.msp.hangul;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Hangul Choseong Searcher Unit Tests")
class HangulSearcherTest {

    @Nested
    @DisplayName("HangulDecomposer Tests")
    class DecomposerTests {

        @Test
        @DisplayName("Decompose syllable without jongseong ('가')")
        void testNoJongseong() {
            HangulSyllable s = HangulDecomposer.decompose('가');
            assertTrue(s.isHangul());
            assertEquals('가', s.original());
            assertEquals('ㄱ', s.choseong());
            assertEquals('ㅏ', s.jungseong());
            assertEquals('\0', s.jongseong());
            assertFalse(s.hasJongseong());
        }

        @Test
        @DisplayName("Decompose syllable with single jongseong ('홍')")
        void testSingleJongseong() {
            HangulSyllable s = HangulDecomposer.decompose('홍');
            assertTrue(s.isHangul());
            assertEquals('홍', s.original());
            assertEquals('ㅎ', s.choseong());
            assertEquals('ㅗ', s.jungseong());
            assertEquals('ㅇ', s.jongseong());
            assertTrue(s.hasJongseong());
        }

        @Test
        @DisplayName("Decompose syllable with complex/double jongseong ('닭')")
        void testDoubleJongseong() {
            HangulSyllable s = HangulDecomposer.decompose('닭');
            assertTrue(s.isHangul());
            assertEquals('닭', s.original());
            assertEquals('ㄷ', s.choseong());
            assertEquals('ㅏ', s.jungseong());
            assertEquals('ㄺ', s.jongseong());
            assertTrue(s.hasJongseong());
        }

        @Test
        @DisplayName("Non-Hangul characters decompose safely as passthrough")
        void testNonHangul() {
            HangulSyllable s = HangulDecomposer.decompose('A');
            assertFalse(s.isHangul());
            assertEquals('A', s.original());
            assertEquals('A', s.choseong());
            assertEquals('\0', s.jungseong());
            assertEquals('\0', s.jongseong());
        }

        @Test
        @DisplayName("extractChoseong extracts initial consonants and preserves other characters")
        void testExtractChoseong() {
            String text = "홍길동 123 Java!";
            String choseong = HangulDecomposer.extractChoseong(text);
            assertEquals("ㅎㄱㄷ 123 Java!", choseong);

            assertEquals("", HangulDecomposer.extractChoseong(""));
            assertNull(HangulDecomposer.extractChoseong(null));
        }

        @Test
        @DisplayName("isChoseong validation")
        void testIsChoseong() {
            assertTrue(HangulDecomposer.isChoseong('ㄱ'));
            assertTrue(HangulDecomposer.isChoseong('ㄲ'));
            assertTrue(HangulDecomposer.isChoseong('ㅎ'));
            assertFalse(HangulDecomposer.isChoseong('ㅏ')); // vowel
            assertFalse(HangulDecomposer.isChoseong('ㄳ')); // compound consonant (jongseong only)
            assertFalse(HangulDecomposer.isChoseong('A'));
            assertFalse(HangulDecomposer.isChoseong('1'));
        }
    }

    @Nested
    @DisplayName("ChoseongSearcher Matching Tests")
    class SearcherMatchTests {

        @Test
        @DisplayName("Exact initial consonant query matches full text")
        void testFullChoseongMatch() {
            assertTrue(ChoseongSearcher.matches("홍길동", "ㅎㄱㄷ"));
            assertTrue(ChoseongSearcher.matches("동해물과 백두산이", "ㄷㅎㅁㄱ ㅂㄷㅅㅇ"));
        }

        @Test
        @DisplayName("Partial initial consonant substring matches")
        void testPartialChoseongMatch() {
            assertTrue(ChoseongSearcher.matches("홍길동전", "ㄱㄷ"));
            assertTrue(ChoseongSearcher.matches("서울특별시청", "ㅂㅅㅊ"));
        }

        @Test
        @DisplayName("Mixed query with both complete Hangul and Choseong")
        void testMixedQuery() {
            assertTrue(ChoseongSearcher.matches("홍길동전", "홍ㄱㄷ"));
            assertTrue(ChoseongSearcher.matches("삼성전자 갤럭시", "삼성ㅈㅈ"));
        }

        @Test
        @DisplayName("Mixed Hangul and English/numbers with case-insensitivity")
        void testMixedAlphanumeric() {
            assertTrue(ChoseongSearcher.matches("Java개발자 모임", "javaㄱㅂ"));
            assertTrue(ChoseongSearcher.matches("iPhone 15 프로맥스", "15 ㅍㄹ"));
            assertTrue(ChoseongSearcher.matches("Spring Boot 3.0", "boot 3"));
        }

        @Test
        @DisplayName("In-flight syllable autocomplete prefix matching ('홍기' matches '홍길동')")
        void testInFlightPrefix() {
            assertTrue(ChoseongSearcher.matches("홍길동", "홍기"));
            assertTrue(ChoseongSearcher.matches("배곧신도시", "배고"));
        }

        @Test
        @DisplayName("Hanja passthrough matching")
        void testHanjaPassthrough() {
            assertTrue(ChoseongSearcher.matches("大韓民國 대한민국", "大韓"));
        }

        @Test
        @DisplayName("Negative cases return false")
        void testNoMatch() {
            assertFalse(ChoseongSearcher.matches("홍길동", "ㅈㄱㄷ"));
            assertFalse(ChoseongSearcher.matches("홍길동", "홍길동전"));
            assertFalse(ChoseongSearcher.matches("", "ㅎ"));
            assertFalse(ChoseongSearcher.matches("홍길동", ""));
            assertFalse(ChoseongSearcher.matches(null, "ㅎ"));
        }
    }

    @Nested
    @DisplayName("Search & Highlighting Tests")
    class SearchAndHighlightTests {

        @Test
        @DisplayName("Search returns accurate character index ranges")
        void testSearchRanges() {
            List<MatchRange> ranges = ChoseongSearcher.search("홍길동전과 허균, 그리고 홍길동", "ㅎㄱㄷ");
            assertEquals(2, ranges.size());

            assertEquals(0, ranges.get(0).start());
            assertEquals(3, ranges.get(0).end());
            assertEquals("홍길동", ranges.get(0).matchedText());

            assertEquals(14, ranges.get(1).start());
            assertEquals(17, ranges.get(1).end());
            assertEquals("홍길동", ranges.get(1).matchedText());
        }

        @Test
        @DisplayName("Highlight wraps matched text with tags cleanly")
        void testHighlight() {
            String text = "홍길동전의 저자는 허균이며 홍길동은 실존인물 모티브다.";
            String highlighted = ChoseongSearcher.highlight(text, "ㅎㄱㄷ", "<mark>", "</mark>");

            assertEquals("<mark>홍길동</mark>전의 저자는 허균이며 <mark>홍길동</mark>은 실존인물 모티브다.", highlighted);
        }

        @Test
        @DisplayName("Highlight preserves text when no match exists")
        void testHighlightNoMatch() {
            String text = "이순신 장군";
            assertEquals(text, ChoseongSearcher.highlight(text, "ㅎㄱㄷ", "<b>", "</b>"));
        }
    }
}
