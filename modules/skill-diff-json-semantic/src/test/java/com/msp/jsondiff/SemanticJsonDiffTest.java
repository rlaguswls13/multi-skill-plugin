package com.msp.jsondiff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SemanticJsonDiffTest {

    @Test
    @DisplayName("Detects field addition, removal, and modification")
    void testBasicFieldChanges() {
        String before = """
                {
                  "name": "Alice",
                  "age": 30,
                  "status": "PENDING"
                }
                """;
        String after = """
                {
                  "name": "Alice",
                  "age": 31,
                  "email": "alice@example.com"
                }
                """;

        List<JsonDiffEntry> diffs = SemanticJsonDiff.diff(before, after);
        assertEquals(3, diffs.size());

        // Age modified
        JsonDiffEntry ageDiff = diffs.stream().filter(d -> d.path().equals("age")).findFirst().orElseThrow();
        assertEquals(DiffType.MODIFIED, ageDiff.type());
        assertEquals(30L, ((Number) ageDiff.oldValue()).longValue());
        assertEquals(31L, ((Number) ageDiff.newValue()).longValue());

        // Status removed
        JsonDiffEntry statusDiff = diffs.stream().filter(d -> d.path().equals("status")).findFirst().orElseThrow();
        assertEquals(DiffType.REMOVED, statusDiff.type());
        assertEquals("PENDING", statusDiff.oldValue());
        assertNull(statusDiff.newValue());

        // Email added
        JsonDiffEntry emailDiff = diffs.stream().filter(d -> d.path().equals("email")).findFirst().orElseThrow();
        assertEquals(DiffType.ADDED, emailDiff.type());
        assertNull(emailDiff.oldValue());
        assertEquals("alice@example.com", emailDiff.newValue());

        assertTrue(SemanticJsonDiff.hasChanges(before, after));
    }

    @Test
    @DisplayName("Detects nested object and array element changes")
    void testNestedAndArrayChanges() {
        String before = """
                {
                  "user": {
                    "profile": {
                      "city": "Seoul",
                      "zip": "12345"
                    },
                    "tags": ["java", "spring"]
                  }
                }
                """;
        String after = """
                {
                  "user": {
                    "profile": {
                      "city": "Busan",
                      "zip": "12345"
                    },
                    "tags": ["java", "kotlin", "backend"]
                  }
                }
                """;

        List<JsonDiffEntry> diffs = SemanticJsonDiff.diff(before, after);

        // Nested city modified
        JsonDiffEntry cityDiff = diffs.stream().filter(d -> d.path().equals("user.profile.city")).findFirst().orElseThrow();
        assertEquals(DiffType.MODIFIED, cityDiff.type());
        assertEquals("Seoul", cityDiff.oldValue());
        assertEquals("Busan", cityDiff.newValue());

        // Array element 1 modified ("spring" -> "kotlin")
        JsonDiffEntry tag1Diff = diffs.stream().filter(d -> d.path().equals("user.tags[1]")).findFirst().orElseThrow();
        assertEquals(DiffType.MODIFIED, tag1Diff.type());
        assertEquals("spring", tag1Diff.oldValue());
        assertEquals("kotlin", tag1Diff.newValue());

        // Array element 2 added ("backend")
        JsonDiffEntry tag2Diff = diffs.stream().filter(d -> d.path().equals("user.tags[2]")).findFirst().orElseThrow();
        assertEquals(DiffType.ADDED, tag2Diff.type());
        assertEquals("backend", tag2Diff.newValue());
    }

    @Test
    @DisplayName("Identical JSON produces no diff entries and hasChanges false")
    void testIdenticalJson() {
        String json1 = "{\"id\": 1, \"active\": true, \"scores\": [10, 20]}";
        String json2 = "{\"scores\": [10, 20], \"active\": true, \"id\": 1.0}";

        List<JsonDiffEntry> diffs = SemanticJsonDiff.diff(json1, json2);
        assertTrue(diffs.isEmpty());
        assertFalse(SemanticJsonDiff.hasChanges(json1, json2));
        assertEquals("(No semantic changes)", SemanticJsonDiff.toSummaryString(diffs));
    }

    @Test
    @DisplayName("Diff directly on Map objects")
    void testMapDiff() {
        Map<String, Object> before = Map.of("theme", "dark", "fontSize", 14);
        Map<String, Object> after = Map.of("theme", "light", "fontSize", 14);

        List<JsonDiffEntry> diffs = SemanticJsonDiff.diff(before, after);
        assertEquals(1, diffs.size());
        assertEquals("theme", diffs.getFirst().path());
        assertEquals(DiffType.MODIFIED, diffs.getFirst().type());
        assertEquals("dark", diffs.getFirst().oldValue());
        assertEquals("light", diffs.getFirst().newValue());
    }

    @Test
    @DisplayName("Human readable summary string formatting")
    void testSummaryString() {
        List<JsonDiffEntry> diffs = List.of(
                new JsonDiffEntry("user.email", DiffType.ADDED, null, "test@test.com"),
                new JsonDiffEntry("user.password", DiffType.REMOVED, "secret", null),
                new JsonDiffEntry("user.role", DiffType.MODIFIED, "USER", "ADMIN")
        );

        String summary = SemanticJsonDiff.toSummaryString(diffs);
        assertTrue(summary.contains("[+] ADDED    user.email => \"test@test.com\""));
        assertTrue(summary.contains("[-] REMOVED  user.password <= \"secret\""));
        assertTrue(summary.contains("[~] MODIFIED user.role: \"USER\" => \"ADMIN\""));
    }

    @Test
    @DisplayName("Gracefully handle null or empty JSON strings")
    void testNullAndEmptyInputs() {
        assertTrue(SemanticJsonDiff.diff((String) null, (String) null).isEmpty());
        assertTrue(SemanticJsonDiff.diff((Map<String, Object>) null, (Map<String, Object>) null).isEmpty());
        assertFalse(SemanticJsonDiff.hasChanges(null, ""));

        List<JsonDiffEntry> diffs = SemanticJsonDiff.diff((String) null, "{\"k\":\"v\"}");
        assertFalse(diffs.isEmpty());
        assertTrue(SemanticJsonDiff.hasChanges(null, "{\"k\":\"v\"}"));
    }
}
