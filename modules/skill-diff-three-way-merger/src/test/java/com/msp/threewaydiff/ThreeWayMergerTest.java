package com.msp.threewaydiff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ThreeWayMerger Tests")
class ThreeWayMergerTest {

    @Test
    @DisplayName("Clean merge when no branches have changes")
    void testNoChanges() {
        List<String> base = List.of("line1", "line2", "line3");
        MergeResult result = ThreeWayMerger.merge(base, base, base);

        assertFalse(result.hasConflicts());
        assertEquals(base, result.mergedLines());
        assertEquals("line1\nline2\nline3", result.formattedOutput());
    }

    @Test
    @DisplayName("Clean merge when only ours modified a line")
    void testChangeOnlyInOurs() {
        List<String> base = List.of("apple", "banana", "cherry");
        List<String> ours = List.of("apple", "BLUEBERRY", "cherry");
        List<String> theirs = List.of("apple", "banana", "cherry");

        MergeResult result = ThreeWayMerger.merge(base, ours, theirs);

        assertFalse(result.hasConflicts());
        assertEquals(List.of("apple", "BLUEBERRY", "cherry"), result.mergedLines());
    }

    @Test
    @DisplayName("Clean merge when only theirs modified a line")
    void testChangeOnlyInTheirs() {
        List<String> base = List.of("apple", "banana", "cherry");
        List<String> ours = List.of("apple", "banana", "cherry");
        List<String> theirs = List.of("apple", "banana", "CRANBERRY");

        MergeResult result = ThreeWayMerger.merge(base, ours, theirs);

        assertFalse(result.hasConflicts());
        assertEquals(List.of("apple", "banana", "CRANBERRY"), result.mergedLines());
    }

    @Test
    @DisplayName("Clean merge when both modified different lines")
    void testDisjointChangesInBoth() {
        List<String> base = List.of("row1", "row2", "row3", "row4");
        List<String> ours = List.of("ROW_ONE", "row2", "row3", "row4");
        List<String> theirs = List.of("row1", "row2", "row3", "ROW_FOUR");

        MergeResult result = ThreeWayMerger.merge(base, ours, theirs);

        assertFalse(result.hasConflicts());
        assertEquals(List.of("ROW_ONE", "row2", "row3", "ROW_FOUR"), result.mergedLines());
    }

    @Test
    @DisplayName("Clean merge when both branches made identical changes")
    void testIdenticalChangesInBoth() {
        List<String> base = List.of("header", "body", "footer");
        List<String> ours = List.of("header", "UPDATED_BODY", "footer");
        List<String> theirs = List.of("header", "UPDATED_BODY", "footer");

        MergeResult result = ThreeWayMerger.merge(base, ours, theirs);

        assertFalse(result.hasConflicts());
        assertEquals(List.of("header", "UPDATED_BODY", "footer"), result.mergedLines());
    }

    @Test
    @DisplayName("Conflict detected when both branches modified the same line differently")
    void testConflictingModifications() {
        List<String> base = List.of("alpha", "beta", "gamma");
        List<String> ours = List.of("alpha", "BETA_BY_OURS", "gamma");
        List<String> theirs = List.of("alpha", "BETA_BY_THEIRS", "gamma");

        MergeResult result = ThreeWayMerger.merge(base, ours, theirs);

        assertTrue(result.hasConflicts());
        assertTrue(result.formattedOutput().contains("<<<<<<< OURS"));
        assertTrue(result.formattedOutput().contains("BETA_BY_OURS"));
        assertTrue(result.formattedOutput().contains("======="));
        assertTrue(result.formattedOutput().contains("BETA_BY_THEIRS"));
        assertTrue(result.formattedOutput().contains(">>>>>>> THEIRS"));

        // Verify alpha and gamma remain untouched around the conflict
        assertEquals("alpha", result.mergedLines().getFirst());
        assertEquals("gamma", result.mergedLines().getLast());
    }

    @Test
    @DisplayName("Deletions: one branch deletes, the other leaves untouched")
    void testDeletionByOneBranch() {
        List<String> base = List.of("line1", "to_delete", "line3");
        List<String> ours = List.of("line1", "line3");
        List<String> theirs = List.of("line1", "to_delete", "line3");

        MergeResult result = ThreeWayMerger.merge(base, ours, theirs);

        assertFalse(result.hasConflicts());
        assertEquals(List.of("line1", "line3"), result.mergedLines());
    }

    @Test
    @DisplayName("Conflict when one branch deletes and other branch modifies the same line")
    void testDeleteModifyConflict() {
        List<String> base = List.of("header", "target", "footer");
        List<String> ours = List.of("header", "footer"); // deleted target
        List<String> theirs = List.of("header", "target_modified", "footer");

        MergeResult result = ThreeWayMerger.merge(base, ours, theirs);

        assertTrue(result.hasConflicts());
        assertTrue(result.formattedOutput().contains("<<<<<<< OURS"));
        assertTrue(result.formattedOutput().contains("======="));
        assertTrue(result.formattedOutput().contains("target_modified"));
        assertTrue(result.formattedOutput().contains(">>>>>>> THEIRS"));
    }

    @Test
    @DisplayName("String input overload test with multiline text")
    void testStringOverload() {
        String base = "first\nsecond\nthird";
        String ours = "first\nsecond_ours\nthird";
        String theirs = "first\nsecond\nthird_theirs";

        MergeResult result = ThreeWayMerger.merge(base, ours, theirs);
        assertFalse(result.hasConflicts());
        assertEquals("first\nsecond_ours\nthird_theirs", result.formattedOutput());
    }

    @Test
    @DisplayName("MergeChunk metadata and factory methods")
    void testMergeChunkProperties() {
        MergeChunk cleanChunk = MergeChunk.clean(
                List.of("a"), List.of("a"), List.of("a"), List.of("a")
        );
        assertFalse(cleanChunk.isConflict());
        assertEquals(List.of("a"), cleanChunk.resolvedLines());

        MergeChunk conflictChunk = MergeChunk.conflict(
                List.of("base"), List.of("ours"), List.of("theirs")
        );
        assertTrue(conflictChunk.isConflict());
        assertEquals(5, conflictChunk.resolvedLines().size());
        assertEquals("<<<<<<< OURS", conflictChunk.resolvedLines().get(0));
        assertEquals("ours", conflictChunk.resolvedLines().get(1));
        assertEquals("=======", conflictChunk.resolvedLines().get(2));
        assertEquals("theirs", conflictChunk.resolvedLines().get(3));
        assertEquals(">>>>>>> THEIRS", conflictChunk.resolvedLines().get(4));
    }
}
