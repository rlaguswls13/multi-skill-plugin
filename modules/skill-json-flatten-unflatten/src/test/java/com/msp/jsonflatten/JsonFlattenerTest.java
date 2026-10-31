package com.msp.jsonflatten;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonFlattenerTest {

    @Test
    @DisplayName("Should flatten and unflatten nested map with array matching prompt specification")
    void testPromptExample() {
        Map<String, Object> nested = Map.of(
                "user", Map.of("address", List.of("Seoul", "Gangnam"))
        );

        Map<String, Object> flat = JsonFlattener.flatten(nested);

        assertEquals("Seoul", flat.get("user.address[0]"));
        assertEquals("Gangnam", flat.get("user.address[1]"));
        assertEquals(2, flat.size());

        Map<String, Object> unflattened = JsonFlattener.unflatten(flat);
        assertEquals(nested, unflattened);
    }

    @Test
    @DisplayName("Should handle deeply nested objects")
    void testDeeplyNestedObjects() {
        Map<String, Object> nested = Map.of(
                "a", Map.of(
                        "b", Map.of(
                                "c", Map.of("d", 42)
                        )
                )
        );

        Map<String, Object> flat = JsonFlattener.flatten(nested);
        assertEquals(42, flat.get("a.b.c.d"));
        assertEquals(1, flat.size());

        Map<String, Object> reconstructed = JsonFlattener.unflatten(flat);
        assertEquals(nested, reconstructed);
    }

    @Test
    @DisplayName("Should handle list of maps")
    void testListOfMaps() {
        Map<String, Object> nested = Map.of(
                "users", List.of(
                        Map.of("id", 1, "name", "Alice"),
                        Map.of("id", 2, "name", "Bob")
                )
        );

        Map<String, Object> flat = JsonFlattener.flatten(nested);
        assertEquals(1, flat.get("users[0].id"));
        assertEquals("Alice", flat.get("users[0].name"));
        assertEquals(2, flat.get("users[1].id"));
        assertEquals("Bob", flat.get("users[1].name"));

        Map<String, Object> reconstructed = JsonFlattener.unflatten(flat);
        assertEquals(nested, reconstructed);
    }

    @Test
    @DisplayName("Should handle 2D multi-dimensional arrays")
    void testMultidimensionalArrays() {
        Map<String, Object> nested = Map.of(
                "matrix", List.of(
                        List.of(1, 2),
                        List.of(3, 4)
                )
        );

        Map<String, Object> flat = JsonFlattener.flatten(nested);
        assertEquals(1, flat.get("matrix[0][0]"));
        assertEquals(2, flat.get("matrix[0][1]"));
        assertEquals(3, flat.get("matrix[1][0]"));
        assertEquals(4, flat.get("matrix[1][1]"));

        Map<String, Object> reconstructed = JsonFlattener.unflatten(flat);
        assertEquals(nested, reconstructed);
    }

    @Test
    @DisplayName("Should support custom separators")
    void testCustomSeparator() {
        JsonFlattener flattener = new JsonFlattener("/");
        Map<String, Object> nested = Map.of(
                "server", Map.of("config", Map.of("port", 8080))
        );

        Map<String, Object> flat = flattener.flattenMap(nested);
        assertEquals(8080, flat.get("server/config/port"));

        Map<String, Object> reconstructed = flattener.unflatMap(flat);
        assertEquals(nested, reconstructed);
    }

    @Test
    @DisplayName("Should preserve null values in leaf positions")
    void testNullValues() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("status", null);
        nested.put("meta", new LinkedHashMap<>(Map.of("code", 200)));

        Map<String, Object> flat = JsonFlattener.flatten(nested);
        assertNull(flat.get("status"));
        assertTrue(flat.containsKey("status"));
        assertEquals(200, flat.get("meta.code"));

        Map<String, Object> reconstructed = JsonFlattener.unflatten(flat);
        assertTrue(reconstructed.containsKey("status"));
        assertNull(reconstructed.get("status"));
    }

    @Test
    @DisplayName("Should handle empty and null inputs safely")
    void testEmptyAndNullInputs() {
        assertTrue(JsonFlattener.flatten(null).isEmpty());
        assertTrue(JsonFlattener.flatten(Map.of()).isEmpty());
        assertTrue(JsonFlattener.unflatten(null).isEmpty());
        assertTrue(JsonFlattener.unflatten(Map.of()).isEmpty());
    }
}
