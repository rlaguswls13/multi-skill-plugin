package com.msp.diffview;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataDiffVisualizerTest {

    @Test
    @DisplayName("Compare flat and nested map structures and generate diff metrics")
    void testDiffComparison() {
        Map<String, Object> before = Map.of(
                "name", "Alice",
                "age", 25,
                "role", "Developer",
                "settings", Map.of(
                        "theme", "light",
                        "notifications", true
                )
        );

        Map<String, Object> after = Map.of(
                "name", "Alice",
                "age", 26,             // MODIFIED
                "email", "alice@test.com", // ADDED
                // role REMOVED
                "settings", Map.of(
                        "theme", "dark",       // MODIFIED
                        "notifications", true  // UNCHANGED
                )
        );

        DiffReport report = DataDiffVisualizer.diff(before, after);

        assertTrue(report.hasChanges());
        assertEquals(1, report.getAdded().size());      // email
        assertEquals(1, report.getRemoved().size());    // role
        assertEquals(2, report.getModified().size());   // age, settings.theme
        assertEquals(2, report.getUnchanged().size());  // name, settings.notifications

        assertEquals("+1 -1 ~2 (unchanged: 2)", report.toSummaryString());

        String mdTable = report.toMarkdownTable();
        assertNotNull(mdTable);
        assertTrue(mdTable.contains("| Field Path | Diff Type | Before | After |"));
        assertTrue(mdTable.contains("`age`"));
        assertTrue(mdTable.contains("`email`"));
        assertTrue(mdTable.contains("`role`"));
        assertTrue(mdTable.contains("`settings.theme`"));
    }
}
