package com.msp.textdiff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextDiffEngineTest {

    @Test
    @DisplayName("Identical texts produce all EQUAL line diffs")
    void testIdenticalLines() {
        String text = "Line 1\nLine 2\nLine 3";
        List<LineDiff> diffs = TextDiffEngine.diffLines(text, text);

        assertEquals(3, diffs.size());
        assertTrue(diffs.stream().allMatch(d -> d.type() == DiffType.EQUAL));
        assertEquals(1, diffs.get(0).oldLineNumber());
        assertEquals(1, diffs.get(0).newLineNumber());
        assertEquals(3, diffs.get(2).oldLineNumber());
        assertEquals(3, diffs.get(2).newLineNumber());
    }

    @Test
    @DisplayName("Line additions and deletions are accurately tracked with line numbers")
    void testLineModifications() {
        String oldText = "Alpha\nBeta\nGamma";
        String newText = "Alpha\nBeta Modified\nGamma\nDelta";

        List<LineDiff> diffs = TextDiffEngine.diffLines(oldText, newText);

        assertEquals(5, diffs.size());
        // Alpha: EQUAL (old:1, new:1)
        assertEquals(DiffType.EQUAL, diffs.get(0).type());
        assertEquals("Alpha", diffs.get(0).line());
        assertEquals(1, diffs.get(0).oldLineNumber());
        assertEquals(1, diffs.get(0).newLineNumber());

        // Beta: DELETE (old:2, new:-1)
        assertEquals(DiffType.DELETE, diffs.get(1).type());
        assertEquals("Beta", diffs.get(1).line());
        assertEquals(2, diffs.get(1).oldLineNumber());
        assertEquals(-1, diffs.get(1).newLineNumber());

        // Beta Modified: INSERT (old:-1, new:2)
        assertEquals(DiffType.INSERT, diffs.get(2).type());
        assertEquals("Beta Modified", diffs.get(2).line());
        assertEquals(-1, diffs.get(2).oldLineNumber());
        assertEquals(2, diffs.get(2).newLineNumber());

        // Gamma: EQUAL (old:3, new:3)
        assertEquals(DiffType.EQUAL, diffs.get(3).type());
        assertEquals("Gamma", diffs.get(3).line());

        // Delta: INSERT (old:-1, new:4)
        assertEquals(DiffType.INSERT, diffs.get(4).type());
        assertEquals("Delta", diffs.get(4).line());
    }

    @Test
    @DisplayName("Word-level diff precisely identifies inserted and deleted words and tokens")
    void testWordDiff() {
        String oldText = "The quick brown fox jumps over the lazy dog";
        String newText = "The fast brown fox leaps over the lazy cat";

        List<WordDiff> diffs = TextDiffEngine.diffWords(oldText, newText);

        // Reconstruct old text
        StringBuilder oldRebuilt = new StringBuilder();
        StringBuilder newRebuilt = new StringBuilder();

        for (WordDiff wd : diffs) {
            if (wd.type() == DiffType.EQUAL || wd.type() == DiffType.DELETE) {
                oldRebuilt.append(wd.text());
            }
            if (wd.type() == DiffType.EQUAL || wd.type() == DiffType.INSERT) {
                newRebuilt.append(wd.text());
            }
        }

        assertEquals(oldText, oldRebuilt.toString());
        assertEquals(newText, newRebuilt.toString());
    }

    @Test
    @DisplayName("AnsiDiffFormatter produces ANSI color sequences for console")
    void testAnsiFormatting() {
        String oldText = "Hello World";
        String newText = "Hello Java";

        List<WordDiff> wordDiffs = TextDiffEngine.diffWords(oldText, newText);
        String ansiWords = AnsiDiffFormatter.formatWords(wordDiffs);

        assertTrue(ansiWords.contains(AnsiDiffFormatter.RED + "[-World-]" + AnsiDiffFormatter.RESET));
        assertTrue(ansiWords.contains(AnsiDiffFormatter.GREEN + "[+Java+]" + AnsiDiffFormatter.RESET));

        List<LineDiff> lineDiffs = TextDiffEngine.diffLines("Old line", "New line");
        String ansiLines = AnsiDiffFormatter.formatLines(lineDiffs);

        assertTrue(ansiLines.contains(AnsiDiffFormatter.RED + "- Old line" + AnsiDiffFormatter.RESET));
        assertTrue(ansiLines.contains(AnsiDiffFormatter.GREEN + "+ New line" + AnsiDiffFormatter.RESET));
    }

    @Test
    @DisplayName("HtmlDiffFormatter escapes special characters and outputs semantic ins and del tags")
    void testHtmlFormatting() {
        String oldText = "<script>alert('bad');</script>";
        String newText = "<span>Safe</span>";

        List<WordDiff> wordDiffs = TextDiffEngine.diffWords(oldText, newText);
        String htmlWords = HtmlDiffFormatter.formatWords(wordDiffs);

        // Raw tags must be escaped
        assertFalse(htmlWords.contains("<script>"));
        assertTrue(htmlWords.contains("&lt;"));
        assertTrue(htmlWords.contains("&gt;"));
        assertTrue(htmlWords.contains("script"));
        assertTrue(htmlWords.contains("&#39;bad&#39;"));
        assertTrue(htmlWords.contains("<del class=\"diff-del\">"));
        assertTrue(htmlWords.contains("<ins class=\"diff-ins\">"));

        List<LineDiff> lineDiffs = TextDiffEngine.diffLines("A < B", "A > B");
        String htmlLines = HtmlDiffFormatter.formatLines(lineDiffs);

        assertTrue(htmlLines.contains("&lt;"));
        assertTrue(htmlLines.contains("&gt;"));
        assertTrue(htmlLines.contains("diff-view"));
        assertTrue(htmlLines.contains("diff-delete"));
        assertTrue(htmlLines.contains("diff-insert"));
    }

    @Test
    @DisplayName("Empty or null text inputs return empty diff lists without exceptions")
    void testEmptyAndNullDiffs() {
        assertTrue(TextDiffEngine.diffLines("", "").isEmpty());
        assertTrue(TextDiffEngine.diffWords("", "").isEmpty());
        assertTrue(TextDiffEngine.diffLines(null, null).isEmpty());
        assertTrue(TextDiffEngine.diffWords(null, null).isEmpty());
    }
}
