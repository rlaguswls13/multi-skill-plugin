package com.msp.truncate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TextTruncator Unit Tests")
class TextTruncatorTest {

    private static final Charset EUC_KR = Charset.forName("EUC-KR");

    @Nested
    @DisplayName("Character Length Truncation")
    class LengthTruncationTests {

        @Test
        @DisplayName("Text shorter or equal to maxLength returns unchanged")
        void testShorterOrEqual() {
            assertEquals("hello", TextTruncator.truncateByLength("hello", 10));
            assertEquals("hello", TextTruncator.truncateByLength("hello", 5));
        }

        @Test
        @DisplayName("Text longer than maxLength appends ellipsis")
        void testLonger() {
            String result = TextTruncator.truncateByLength("Hello, World!", 8);
            assertEquals("Hello...", result);
            assertEquals(8, result.length());
        }

        @Test
        @DisplayName("Custom ellipsis")
        void testCustomEllipsis() {
            String result = TextTruncator.truncateByLength("Hello, World!", 7, "…");
            assertEquals("Hello,…", result);
            assertEquals(7, result.length());
        }

        @Test
        @DisplayName("Surrogate pair emoji is not split")
        void testSurrogatePairSafety() {
            // "abc" (3) + "🚀" (2 chars, surrogate pair) + "def" (3)
            String text = "abc\uD83D\uDE80def"; // "abc🚀def", length is 8
            // If maxLength is 6 with "..." (budget 3) -> "abc..."
            // If maxLength is 7 with "..." (budget 4): cutting at index 4 would be high surrogate!
            String result = TextTruncator.truncateByLength(text, 7, "...");
            assertEquals("abc...", result); // Should stop before orphaned high surrogate!
        }

        @Test
        @DisplayName("Preserving whole words")
        void testPreserveWords() {
            String text = "The quick brown fox jumps over the lazy dog";
            String result = TextTruncator.truncateByLengthPreservingWords(text, 15, "...");
            assertEquals("The quick...", result);
            assertTrue(result.length() <= 15);
        }

        @Test
        @DisplayName("Boundary and error cases")
        void testErrors() {
            assertNull(TextTruncator.truncateByLength(null, 5));
            assertThrows(IllegalArgumentException.class, () -> TextTruncator.truncateByLength("abc", -1));
            assertThrows(IllegalArgumentException.class, () -> TextTruncator.truncateByLength("abc", 2, "..."));
        }
    }

    @Nested
    @DisplayName("Byte-Safe Truncation (UTF-8 & EUC-KR)")
    class ByteTruncationTests {

        @Test
        @DisplayName("UTF-8 Korean text truncation never splits 3-byte characters")
        void testUtf8KoreanSafety() {
            // "가나다라마바사": each Korean char is 3 bytes in UTF-8
            String text = "가나다라마바사";
            // "..." is 3 bytes in UTF-8.
            // maxBytes = 10 -> budget 7 bytes -> 2 Korean chars (6 bytes) + "..." (3 bytes) = 9 bytes!
            String truncated = TextTruncator.truncateByBytes(text, 10, StandardCharsets.UTF_8, "...");

            assertEquals("가나...", truncated);
            byte[] bytes = truncated.getBytes(StandardCharsets.UTF_8);
            assertTrue(bytes.length <= 10);
            assertFalse(truncated.contains("\uFFFD"), "Must not contain broken replacement characters");
        }

        @Test
        @DisplayName("EUC-KR Korean text truncation never splits 2-byte characters")
        void testEucKrKoreanSafety() {
            // In EUC-KR, "대한민국" is 8 bytes (2 bytes per Hangul char).
            // "..." is 3 bytes.
            // maxBytes = 8 -> budget 5 bytes -> 2 Hangul chars (4 bytes) + "..." (3 bytes) = 7 bytes.
            String text = "대한민국만세";
            String truncated = TextTruncator.truncateByBytes(text, 8, EUC_KR, "...");

            assertEquals("대한...", truncated);
            byte[] bytes = truncated.getBytes(EUC_KR);
            assertTrue(bytes.length <= 8);
            assertFalse(truncated.contains("?"), "Must not contain unmapped/broken question mark characters");
        }

        @Test
        @DisplayName("UTF-8 4-byte Emoji truncation is atomic and clean")
        void testEmojiUtf8Safety() {
            String text = "축하합니다 🎉🎉🎉 대박나세요";
            // maxBytes = 20
            String truncated = TextTruncator.truncateByBytes(text, 20, StandardCharsets.UTF_8, "...");

            byte[] bytes = truncated.getBytes(StandardCharsets.UTF_8);
            assertTrue(bytes.length <= 20);
            assertFalse(truncated.contains("\uFFFD"));
        }

        @Test
        @DisplayName("Byte truncation preserving words")
        void testBytePreserveWords() {
            String text = "안녕 친구야 모두 반가워";
            String truncated = TextTruncator.truncateByBytesPreservingWords(text, 16, StandardCharsets.UTF_8, "...");

            byte[] bytes = truncated.getBytes(StandardCharsets.UTF_8);
            assertTrue(bytes.length <= 16);
            assertEquals("안녕...", truncated);
        }
    }

    @Nested
    @DisplayName("Word Count Truncation")
    class WordTruncationTests {

        @Test
        @DisplayName("Truncates by number of space-separated words")
        void testWordCount() {
            String text = "The quick brown fox jumps over the lazy dog";
            assertEquals("The quick brown...", TextTruncator.truncateWords(text, 3));
            assertEquals("The quick brown", TextTruncator.truncateWords(text, 3, ""));
            assertEquals("The quick brown fox jumps over the lazy dog", TextTruncator.truncateWords(text, 15));
        }

        @Test
        @DisplayName("Korean sentence word truncation")
        void testKoreanWords() {
            String text = "오늘 날씨가 정말 좋습니다 나들이 가요";
            assertEquals("오늘 날씨가 정말...", TextTruncator.truncateWords(text, 3));
        }

        @Test
        @DisplayName("Edge cases for word truncation")
        void testWordEdgeCases() {
            assertNull(TextTruncator.truncateWords(null, 3));
            assertEquals("", TextTruncator.truncateWords("hello world", 0, ""));
            assertThrows(IllegalArgumentException.class, () -> TextTruncator.truncateWords("hello", -1));
        }
    }
}
