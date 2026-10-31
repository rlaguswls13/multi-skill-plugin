package com.msp.myersdiff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyersDiffTest {

    @Test
    @DisplayName("Identical texts should produce only KEEP operations and empty unified diff")
    void testIdenticalTexts() {
        List<String> text = List.of("line 1", "line 2", "line 3");

        List<DiffItem> diff = MyersDiff.computeDiff(text, text);
        assertEquals(3, diff.size());
        for (int i = 0; i < diff.size(); i++) {
            DiffItem item = diff.get(i);
            assertEquals(DiffOperationType.KEEP, item.type());
            assertEquals(i + 1, item.oldLineNum());
            assertEquals(i + 1, item.newLineNum());
            assertEquals(text.get(i), item.text());
        }

        String unified = MyersDiff.toUnifiedDiff("a.txt", "b.txt", text, text, 3);
        assertEquals("", unified, "Identical texts must produce empty unified diff");
    }

    @Test
    @DisplayName("Completely different texts should delete all original and insert all revised")
    void testCompletelyDifferentTexts() {
        List<String> original = List.of("A", "B");
        List<String> revised = List.of("X", "Y");

        List<DiffItem> diff = MyersDiff.computeDiff(original, revised);
        assertEquals(4, diff.size());

        // First 2 deleted, next 2 inserted
        assertEquals(DiffOperationType.DELETE, diff.get(0).type());
        assertEquals("A", diff.get(0).text());
        assertEquals(1, diff.get(0).oldLineNum());
        assertEquals(-1, diff.get(0).newLineNum());

        assertEquals(DiffOperationType.DELETE, diff.get(1).type());
        assertEquals("B", diff.get(1).text());

        assertEquals(DiffOperationType.INSERT, diff.get(2).type());
        assertEquals("X", diff.get(2).text());
        assertEquals(-1, diff.get(2).oldLineNum());
        assertEquals(1, diff.get(2).newLineNum());

        assertEquals(DiffOperationType.INSERT, diff.get(3).type());
        assertEquals("Y", diff.get(3).text());
    }

    @Test
    @DisplayName("Insertions: adding lines to middle and end")
    void testInsertions() {
        List<String> original = List.of("alpha", "gamma");
        List<String> revised = List.of("alpha", "beta", "gamma", "delta");

        List<DiffItem> diff = MyersDiff.computeDiff(original, revised);
        assertEquals(4, diff.size());

        assertEquals(DiffOperationType.KEEP, diff.get(0).type()); // alpha
        assertEquals("alpha", diff.get(0).text());

        assertEquals(DiffOperationType.INSERT, diff.get(1).type()); // beta
        assertEquals("beta", diff.get(1).text());
        assertEquals(2, diff.get(1).newLineNum());

        assertEquals(DiffOperationType.KEEP, diff.get(2).type()); // gamma
        assertEquals("gamma", diff.get(2).text());

        assertEquals(DiffOperationType.INSERT, diff.get(3).type()); // delta
        assertEquals("delta", diff.get(3).text());
        assertEquals(4, diff.get(3).newLineNum());
    }

    @Test
    @DisplayName("Deletions: removing lines")
    void testDeletions() {
        List<String> original = List.of("first", "to-remove", "last");
        List<String> revised = List.of("first", "last");

        List<DiffItem> diff = MyersDiff.computeDiff(original, revised);
        assertEquals(3, diff.size());

        assertEquals(DiffOperationType.KEEP, diff.get(0).type());
        assertEquals(DiffOperationType.DELETE, diff.get(1).type());
        assertEquals("to-remove", diff.get(1).text());
        assertEquals(2, diff.get(1).oldLineNum());
        assertEquals(DiffOperationType.KEEP, diff.get(2).type());
    }

    @Test
    @DisplayName("Unified diff output format with hunks (@@ -1,3 +1,4 @@)")
    void testUnifiedDiffFormat() {
        List<String> original = List.of("line1", "line2", "line3");
        List<String> revised = List.of("line1", "line2", "line3_modified", "line4");

        String uDiff = MyersDiff.toUnifiedDiff("fileA.txt", "fileB.txt", original, revised, 3);

        assertTrue(uDiff.contains("--- fileA.txt"));
        assertTrue(uDiff.contains("+++ fileB.txt"));
        assertTrue(uDiff.contains("@@ -1,3 +1,4 @@"));
        assertTrue(uDiff.contains(" line1"));
        assertTrue(uDiff.contains(" line2"));
        assertTrue(uDiff.contains("-line3"));
        assertTrue(uDiff.contains("+line3_modified"));
        assertTrue(uDiff.contains("+line4"));
    }

    @Test
    @DisplayName("Multiple distinct hunks when changes are separated by large unchanged blocks")
    void testMultipleHunks() {
        List<String> original = List.of(
                "head1", "head2",
                "mid1", "mid2", "mid3", "mid4", "mid5", "mid6", "mid7", "mid8", "mid9", "mid10",
                "tail1", "tail2"
        );
        List<String> revised = List.of(
                "head1_changed", "head2",
                "mid1", "mid2", "mid3", "mid4", "mid5", "mid6", "mid7", "mid8", "mid9", "mid10",
                "tail1", "tail2_changed"
        );

        // contextLines = 1 -> will produce two separate hunks because middle has 10 unchanged lines
        String uDiff = MyersDiff.toUnifiedDiff("orig.txt", "rev.txt", original, revised, 1);

        int hunkCount = 0;
        int idx = 0;
        while ((idx = uDiff.indexOf("@@ -", idx)) != -1) {
            hunkCount++;
            idx += 4;
        }
        assertEquals(2, hunkCount, "Should produce exactly 2 separate hunks");
    }

    @Test
    @DisplayName("Edge cases: empty inputs")
    void testEmptyInputs() {
        // Both empty
        List<DiffItem> bothEmpty = MyersDiff.computeDiff(List.of(), List.of());
        assertTrue(bothEmpty.isEmpty());
        assertEquals("", MyersDiff.toUnifiedDiff("a", "b", List.of(), List.of(), 3));

        // Original empty (all inserts)
        List<DiffItem> origEmpty = MyersDiff.computeDiff(List.of(), List.of("new1", "new2"));
        assertEquals(2, origEmpty.size());
        assertTrue(origEmpty.get(0).isInsert());

        // Revised empty (all deletes)
        List<DiffItem> revEmpty = MyersDiff.computeDiff(List.of("old1"), List.of());
        assertEquals(1, revEmpty.size());
        assertTrue(revEmpty.get(0).isDelete());
    }
}
