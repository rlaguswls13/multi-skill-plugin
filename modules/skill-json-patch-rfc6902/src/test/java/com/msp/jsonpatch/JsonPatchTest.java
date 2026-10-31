package com.msp.jsonpatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonPatchTest {

    private Map<String, Object> createSampleDoc() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("title", "Hello World");
        doc.put("author", "Alice");

        List<Object> tags = new ArrayList<>(List.of("tech", "java", "code"));
        doc.put("tags", tags);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("views", 100);
        meta.put("published", true);
        doc.put("meta", meta);

        return doc;
    }

    @Test
    @DisplayName("ADD operation: map property, list insert, list append")
    void testAddOperations() {
        Map<String, Object> doc = createSampleDoc();

        List<JsonPatchOperation> ops = List.of(
            JsonPatchOperation.add("/category", "Programming"),
            JsonPatchOperation.add("/tags/1", "spring"), // Insert at index 1: tech, spring, java, code
            JsonPatchOperation.add("/tags/-", "rfc6902")  // Append to list
        );

        Object result = JsonPatch.apply(doc, ops);
        assertTrue(result instanceof Map<?, ?>);

        @SuppressWarnings("unchecked")
        Map<String, Object> resMap = (Map<String, Object>) result;
        assertEquals("Programming", resMap.get("category"));

        @SuppressWarnings("unchecked")
        List<Object> tags = (List<Object>) resMap.get("tags");
        assertEquals(5, tags.size());
        assertEquals("tech", tags.get(0));
        assertEquals("spring", tags.get(1));
        assertEquals("java", tags.get(2));
        assertEquals("code", tags.get(3));
        assertEquals("rfc6902", tags.get(4));
    }

    @Test
    @DisplayName("REMOVE operation: map property, list item")
    void testRemoveOperations() {
        Map<String, Object> doc = createSampleDoc();

        List<JsonPatchOperation> ops = List.of(
            JsonPatchOperation.remove("/author"),
            JsonPatchOperation.remove("/tags/0") // Remove "tech"
        );

        JsonPatch.apply(doc, ops);

        assertFalse(doc.containsKey("author"));
        @SuppressWarnings("unchecked")
        List<Object> tags = (List<Object>) doc.get("tags");
        assertEquals(2, tags.size());
        assertEquals("java", tags.get(0));
        assertEquals("code", tags.get(1));
    }

    @Test
    @DisplayName("REMOVE nonexistent key should throw exception")
    void testRemoveNonexistentThrows() {
        Map<String, Object> doc = createSampleDoc();
        List<JsonPatchOperation> ops = List.of(JsonPatchOperation.remove("/nonexistent"));

        assertThrows(JsonPatchException.class, () -> JsonPatch.apply(doc, ops));
    }

    @Test
    @DisplayName("REPLACE operation: map property, list element")
    void testReplaceOperations() {
        Map<String, Object> doc = createSampleDoc();

        List<JsonPatchOperation> ops = List.of(
            JsonPatchOperation.replace("/title", "Updated Title"),
            JsonPatchOperation.replace("/meta/views", 250),
            JsonPatchOperation.replace("/tags/1", "kotlin")
        );

        JsonPatch.apply(doc, ops);

        assertEquals("Updated Title", doc.get("title"));
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) doc.get("meta");
        assertEquals(250, meta.get("views"));

        @SuppressWarnings("unchecked")
        List<Object> tags = (List<Object>) doc.get("tags");
        assertEquals("kotlin", tags.get(1));
    }

    @Test
    @DisplayName("MOVE operation: move property and move within list")
    void testMoveOperation() {
        Map<String, Object> doc = createSampleDoc();

        List<JsonPatchOperation> ops = List.of(
            JsonPatchOperation.move("/author", "/meta/writer")
        );

        JsonPatch.apply(doc, ops);

        assertFalse(doc.containsKey("author"));
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) doc.get("meta");
        assertEquals("Alice", meta.get("writer"));
    }

    @Test
    @DisplayName("MOVE operation fails if target is child of source")
    void testMoveIntoDescendantFails() {
        Map<String, Object> doc = createSampleDoc();
        List<JsonPatchOperation> ops = List.of(
            JsonPatchOperation.move("/meta", "/meta/nested/illegal")
        );

        assertThrows(JsonPatchException.class, () -> JsonPatch.apply(doc, ops));
    }

    @Test
    @DisplayName("COPY operation: clones deep value")
    void testCopyOperation() {
        Map<String, Object> doc = createSampleDoc();

        List<JsonPatchOperation> ops = List.of(
            JsonPatchOperation.copy("/meta", "/metaCopy")
        );

        JsonPatch.apply(doc, ops);

        assertTrue(doc.containsKey("metaCopy"));
        @SuppressWarnings("unchecked")
        Map<String, Object> copy = (Map<String, Object>) doc.get("metaCopy");
        assertEquals(100, copy.get("views"));

        // Modifying the copy must not alter original meta
        copy.put("views", 999);
        @SuppressWarnings("unchecked")
        Map<String, Object> orig = (Map<String, Object>) doc.get("meta");
        assertEquals(100, orig.get("views"));
    }

    @Test
    @DisplayName("TEST operation: success and failure")
    void testTestOperation() {
        Map<String, Object> doc = createSampleDoc();

        List<JsonPatchOperation> validOps = List.of(
            JsonPatchOperation.test("/title", "Hello World"),
            JsonPatchOperation.test("/meta/views", 100),
            JsonPatchOperation.test("/tags/0", "tech")
        );
        assertDoesNotThrow(() -> JsonPatch.apply(doc, validOps));

        List<JsonPatchOperation> invalidOps = List.of(
            JsonPatchOperation.test("/title", "Wrong Value")
        );
        assertThrows(JsonPatchTestFailedException.class, () -> JsonPatch.apply(doc, invalidOps));
    }

    @Test
    @DisplayName("RFC 6901 Pointer escaping: ~0 (~) and ~1 (/)")
    void testPointerEscaping() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("a/b", "slash-val");
        doc.put("m~n", "tilde-val");

        // Escaped paths: "/a~1b", "/m~0n"
        assertEquals("slash-val", JsonPointer.of("/a~1b").evaluate(doc));
        assertEquals("tilde-val", JsonPointer.of("/m~0n").evaluate(doc));

        // Test replace on escaped paths
        List<JsonPatchOperation> ops = List.of(
            JsonPatchOperation.replace("/a~1b", "updated-slash"),
            JsonPatchOperation.replace("/m~0n", "updated-tilde")
        );
        JsonPatch.apply(doc, ops);

        assertEquals("updated-slash", doc.get("a/b"));
        assertEquals("updated-tilde", doc.get("m~n"));
    }

    @Test
    @DisplayName("Root replace via add with empty path")
    void testRootReplace() {
        Map<String, Object> doc = createSampleDoc();
        Map<String, Object> newRoot = Map.of("status", "reset");

        Object result = JsonPatch.apply(doc, List.of(JsonPatchOperation.add("", newRoot)));
        assertEquals(newRoot, result);
    }
}
