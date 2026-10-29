package com.msp.diff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DiffDetector Unit Tests")
class DiffDetectorTest {

    record Address(String city, String zip) {}
    record User(String name, int age, Address address, List<String> tags) {}

    @Test
    @DisplayName("Detects ADDED, REMOVED, and MODIFIED fields in nested maps")
    void nestedMapDiff() {
        Map<String, Object> oldMap = new LinkedHashMap<>();
        oldMap.put("name", "Alice");
        oldMap.put("role", "USER");
        oldMap.put("address", new LinkedHashMap<>(Map.of("city", "Seoul", "zip", "10001")));
        oldMap.put("deprecated", "remove_me");

        Map<String, Object> newMap = new LinkedHashMap<>();
        newMap.put("name", "Alice");
        newMap.put("role", "ADMIN"); // MODIFIED
        newMap.put("address", new LinkedHashMap<>(Map.of("city", "Busan", "zip", "10001"))); // address.city MODIFIED
        newMap.put("department", "Engineering"); // ADDED
        // 'deprecated' is REMOVED

        DiffResult diff = DiffDetector.compare(oldMap, newMap);
        assertTrue(diff.hasChanges());
        assertEquals(4, diff.changeCount());

        List<FieldChange> modified = diff.getChangesByType(ChangeType.MODIFIED);
        assertEquals(2, modified.size());
        assertTrue(modified.stream().anyMatch(c -> "role".equals(c.path()) && "USER".equals(c.oldValue()) && "ADMIN".equals(c.newValue())));
        assertTrue(modified.stream().anyMatch(c -> "address.city".equals(c.path()) && "Seoul".equals(c.oldValue()) && "Busan".equals(c.newValue())));

        List<FieldChange> added = diff.getChangesByType(ChangeType.ADDED);
        assertEquals(1, added.size());
        assertEquals("department", added.get(0).path());
        assertEquals("Engineering", added.get(0).newValue());

        List<FieldChange> removed = diff.getChangesByType(ChangeType.REMOVED);
        assertEquals(1, removed.size());
        assertEquals("deprecated", removed.get(0).path());
        assertEquals("remove_me", removed.get(0).oldValue());
    }

    @Test
    @DisplayName("Detects list element differences and size changes")
    void listDiff() {
        List<String> oldList = List.of("Java", "Kotlin", "Scala");
        List<String> newList = List.of("Java", "Go", "Scala", "Rust");

        DiffResult diff = DiffDetector.compare(oldList, newList);
        assertEquals(2, diff.changeCount());

        // index 1: Kotlin -> Go (MODIFIED)
        FieldChange mod = diff.changes().get(0);
        assertEquals(ChangeType.MODIFIED, mod.changeType());
        assertEquals("[1]", mod.path());
        assertEquals("Kotlin", mod.oldValue());
        assertEquals("Go", mod.newValue());

        // index 3: Rust (ADDED)
        FieldChange add = diff.changes().get(1);
        assertEquals(ChangeType.ADDED, add.changeType());
        assertEquals("[3]", add.path());
        assertEquals("Rust", add.newValue());
    }

    @Test
    @DisplayName("Detects differences in Java Records")
    void recordDiff() {
        User u1 = new User("Bob", 30, new Address("Tokyo", "111"), List.of("dev"));
        User u2 = new User("Bob", 31, new Address("Osaka", "111"), List.of("dev", "lead"));

        DiffResult diff = DiffDetector.compare(u1, u2);
        assertEquals(3, diff.changeCount());

        assertTrue(diff.changes().stream().anyMatch(c -> "age".equals(c.path()) && Integer.valueOf(30).equals(c.oldValue())));
        assertTrue(diff.changes().stream().anyMatch(c -> "address.city".equals(c.path()) && "Tokyo".equals(c.oldValue())));
        assertTrue(diff.changes().stream().anyMatch(c -> "tags[1]".equals(c.path()) && ChangeType.ADDED == c.changeType()));
    }

    @Test
    @DisplayName("Identical objects produce empty DiffResult")
    void identicalObjects() {
        Map<String, Object> map = Map.of("a", 1, "b", List.of(1, 2));
        DiffResult diff = DiffDetector.compare(map, map);
        assertFalse(diff.hasChanges());
        assertEquals(0, diff.changeCount());
    }
}
