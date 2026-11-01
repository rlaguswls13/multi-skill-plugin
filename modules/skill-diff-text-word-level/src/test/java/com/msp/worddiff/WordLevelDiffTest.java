package com.msp.worddiff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WordLevelDiff Tests")
class WordLevelDiffTest {

    @Test
    @DisplayName("Identical text produces single EQUAL item and unchanged markup")
    void testIdenticalText() {
        String text = "The quick brown fox jumps over the lazy dog.";
        List<WordDiffItem> diffs = WordLevelDiff.diffWords(text, text);

        assertEquals(1, diffs.size());
        assertEquals(WordDiffOp.EQUAL, diffs.getFirst().op());
        assertEquals(text, diffs.getFirst().text());

        String markup = WordLevelDiff.toInlineMarkup(text, text);
        assertEquals(text, markup);
    }

    @Test
    @DisplayName("Single word replacement matches canonical inline markup")
    void testSingleWordReplacement() {
        String original = "The quick brown fox";
        String revised = "The slow brown fox";

        List<WordDiffItem> diffs = WordLevelDiff.diffWords(original, revised);

        // Expect: EQUAL("The "), DELETE("quick"), INSERT("slow"), EQUAL(" brown fox")
        assertEquals(4, diffs.size());
        assertEquals(WordDiffOp.EQUAL, diffs.get(0).op());
        assertEquals("The ", diffs.get(0).text());

        assertEquals(WordDiffOp.DELETE, diffs.get(1).op());
        assertEquals("quick", diffs.get(1).text());

        assertEquals(WordDiffOp.INSERT, diffs.get(2).op());
        assertEquals("slow", diffs.get(2).text());

        assertEquals(WordDiffOp.EQUAL, diffs.get(3).op());
        assertEquals(" brown fox", diffs.get(3).text());

        String markup = WordLevelDiff.toInlineMarkup(original, revised);
        assertEquals("The [-quick-]{+slow+} brown fox", markup);
    }

    @Test
    @DisplayName("Insertion in the middle")
    void testInsertionInMiddle() {
        String original = "The brown fox";
        String revised = "The quick brown fox";

        String markup = WordLevelDiff.toInlineMarkup(original, revised);
        assertEquals("The {+quick +}brown fox", markup);
    }

    @Test
    @DisplayName("Deletion at the end")
    void testDeletionAtEnd() {
        String original = "The brown fox jumps";
        String revised = "The brown fox";

        String markup = WordLevelDiff.toInlineMarkup(original, revised);
        assertEquals("The brown fox[- jumps-]", markup);
    }

    @Test
    @DisplayName("Punctuation preservation during word replacement")
    void testPunctuationPreservation() {
        String original = "Hello, world!";
        String revised = "Hello, there!";

        List<WordDiffItem> diffs = WordLevelDiff.diffWords(original, revised);
        String markup = WordLevelDiff.toInlineMarkup(original, revised);

        assertEquals("Hello, [-world-]{+there+}!", markup);
    }

    @Test
    @DisplayName("Korean multilingual word and phrase diff")
    void testKoreanMultilingualDiff() {
        String original = "안녕하세요 멋진 세상입니다.";
        String revised = "안녕하세요 아름다운 세상입니다.";

        String markup = WordLevelDiff.toInlineMarkup(original, revised);
        assertEquals("안녕하세요 [-멋진-]{+아름다운+} 세상입니다.", markup);

        // Token count verification
        List<String> tokens = WordLevelDiff.tokenize("안녕하세요 멋진 세상!");
        assertTrue(tokens.contains("안녕하세요"));
        assertTrue(tokens.contains("멋진"));
        assertTrue(tokens.contains("세상"));
        assertTrue(tokens.contains("!"));
    }

    @Test
    @DisplayName("Multi-line text differences")
    void testMultiLineDiff() {
        String original = "Header\nFirst Line\nFooter";
        String revised = "Header\nUpdated Line\nFooter";

        String markup = WordLevelDiff.toInlineMarkup(original, revised);
        assertEquals("Header\n[-First-]{+Updated+} Line\nFooter", markup);
    }

    @Test
    @DisplayName("Custom HTML markup generation (del/ins)")
    void testCustomMarkup() {
        String original = "status: active";
        String revised = "status: suspended";

        String custom = WordLevelDiff.toCustomMarkup(
                original, revised,
                "<del>", "</del>",
                "<ins>", "</ins>"
        );
        assertEquals("status: <del>active</del><ins>suspended</ins>", custom);
    }

    @Test
    @DisplayName("Complete replacement and edge cases")
    void testCompleteReplacementAndEmpty() {
        // Complete replacement
        String markupRep = WordLevelDiff.toInlineMarkup("foo", "bar");
        assertEquals("[-foo-]{+bar+}", markupRep);

        // Both empty
        assertTrue(WordLevelDiff.diffWords("", "").isEmpty());
        assertEquals("", WordLevelDiff.toInlineMarkup("", ""));

        // Original empty
        assertEquals("{+hello+}", WordLevelDiff.toInlineMarkup("", "hello"));

        // Revised empty
        assertEquals("[-world-]", WordLevelDiff.toInlineMarkup("world", ""));

        // Null checks
        assertThrows(NullPointerException.class, () -> WordLevelDiff.diffWords(null, "test"));
        assertThrows(NullPointerException.class, () -> WordLevelDiff.diffWords("test", null));
    }
}
