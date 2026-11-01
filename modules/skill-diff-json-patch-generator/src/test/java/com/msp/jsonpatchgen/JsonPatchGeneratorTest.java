package com.msp.jsonpatchgen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonPatchGeneratorTest {

    @Test
    @DisplayName("Identical JSONs yield an empty patch list and empty JSON array")
    void testIdenticalJsonsYieldEmptyPatch() {
        String json1 = "{\"name\":\"Alice\",\"age\":30,\"tags\":[\"dev\",\"jvm\"]}";
        String json2 = "{\"name\":\"Alice\",\"age\":30,\"tags\":[\"dev\",\"jvm\"]}";

        List<JsonPatchOperation> patch = JsonPatchGenerator.generatePatch(json1, json2);
        assertTrue(patch.isEmpty());

        String patchJson = JsonPatchGenerator.generatePatchJson(json1, json2);
        assertEquals("[]", patchJson);
    }

    @Test
    @DisplayName("Object field addition produces ADD operation")
    void testObjectFieldAddition() {
        String source = "{\"name\":\"Alice\"}";
        String target = "{\"name\":\"Alice\",\"email\":\"alice@example.com\"}";

        List<JsonPatchOperation> patch = JsonPatchGenerator.generatePatch(source, target);
        assertEquals(1, patch.size());

        JsonPatchOperation op = patch.get(0);
        assertEquals(JsonPatchOp.ADD, op.op());
        assertEquals("/email", op.path());
        assertEquals("alice@example.com", op.value());

        String patchJson = JsonPatchGenerator.generatePatchJson(source, target);
        assertEquals("[{\"op\":\"add\",\"path\":\"/email\",\"value\":\"alice@example.com\"}]", patchJson);
    }

    @Test
    @DisplayName("Object field removal produces REMOVE operation with null value")
    void testObjectFieldRemoval() {
        String source = "{\"name\":\"Alice\",\"temporary\":true}";
        String target = "{\"name\":\"Alice\"}";

        List<JsonPatchOperation> patch = JsonPatchGenerator.generatePatch(source, target);
        assertEquals(1, patch.size());

        JsonPatchOperation op = patch.get(0);
        assertEquals(JsonPatchOp.REMOVE, op.op());
        assertEquals("/temporary", op.path());
        assertNull(op.value());

        String patchJson = JsonPatchGenerator.generatePatchJson(source, target);
        assertEquals("[{\"op\":\"remove\",\"path\":\"/temporary\"}]", patchJson);
    }

    @Test
    @DisplayName("Field value replacement produces REPLACE operation")
    void testFieldValueReplacement() {
        String source = "{\"status\":\"PENDING\",\"count\":10}";
        String target = "{\"status\":\"COMPLETED\",\"count\":15}";

        List<JsonPatchOperation> patch = JsonPatchGenerator.generatePatch(source, target);
        assertEquals(2, patch.size());

        assertTrue(patch.stream().anyMatch(op -> op.op() == JsonPatchOp.REPLACE
                && op.path().equals("/status")
                && "COMPLETED".equals(op.value())));

        assertTrue(patch.stream().anyMatch(op -> op.op() == JsonPatchOp.REPLACE
                && op.path().equals("/count")
                && Long.valueOf(15).equals(op.value())));
    }

    @Test
    @DisplayName("Nested object path (/a/b/c) modifications are correctly addressed")
    void testNestedObjectPath() {
        String source = "{\"config\":{\"database\":{\"port\":3306,\"host\":\"localhost\"}}}";
        String target = "{\"config\":{\"database\":{\"port\":5432,\"host\":\"localhost\"}}}";

        List<JsonPatchOperation> patch = JsonPatchGenerator.generatePatch(source, target);
        assertEquals(1, patch.size());

        JsonPatchOperation op = patch.get(0);
        assertEquals(JsonPatchOp.REPLACE, op.op());
        assertEquals("/config/database/port", op.path());
        assertEquals(5432L, op.value());

        String patchJson = JsonPatchGenerator.generatePatchJson(source, target);
        assertEquals("[{\"op\":\"replace\",\"path\":\"/config/database/port\",\"value\":5432}]", patchJson);
    }

    @Test
    @DisplayName("Array element modifications, additions, and removals")
    void testArrayOperations() {
        // Replacement in array
        String src1 = "{\"items\":[10, 20, 30]}";
        String tgt1 = "{\"items\":[10, 99, 30]}";
        List<JsonPatchOperation> patch1 = JsonPatchGenerator.generatePatch(src1, tgt1);
        assertEquals(1, patch1.size());
        assertEquals(JsonPatchOp.REPLACE, patch1.get(0).op());
        assertEquals("/items/1", patch1.get(0).path());
        assertEquals(99L, patch1.get(0).value());

        // Addition to array
        String src2 = "{\"items\":[10, 20]}";
        String tgt2 = "{\"items\":[10, 20, 30]}";
        List<JsonPatchOperation> patch2 = JsonPatchGenerator.generatePatch(src2, tgt2);
        assertEquals(1, patch2.size());
        assertEquals(JsonPatchOp.ADD, patch2.get(0).op());
        assertEquals("/items/2", patch2.get(0).path());
        assertEquals(30L, patch2.get(0).value());

        // Removal from array
        String src3 = "{\"items\":[10, 20, 30]}";
        String tgt3 = "{\"items\":[10]}";
        List<JsonPatchOperation> patch3 = JsonPatchGenerator.generatePatch(src3, tgt3);
        assertEquals(2, patch3.size());
        assertEquals(JsonPatchOp.REMOVE, patch3.get(0).op());
        assertEquals("/items/2", patch3.get(0).path());
        assertEquals(JsonPatchOp.REMOVE, patch3.get(1).op());
        assertEquals("/items/1", patch3.get(1).path());
    }

    @Test
    @DisplayName("RFC 6901 JSON Pointer escaping (~ and /)")
    void testJsonPointerEscaping() {
        String source = "{\"a/b\":1}";
        String target = "{\"a/b\":2}";

        List<JsonPatchOperation> patch = JsonPatchGenerator.generatePatch(source, target);
        assertEquals(1, patch.size());
        assertEquals("/a~1b", patch.get(0).path());
    }

    @Test
    @DisplayName("Malformed JSON input throws JsonParseException")
    void testMalformedJsonThrows() {
        assertThrows(JsonParseException.class, () ->
                JsonPatchGenerator.generatePatch("{\"invalid\":", "{}"));

        assertThrows(JsonParseException.class, () ->
                JsonPatchGenerator.generatePatch("{}", "[1, 2,"));

        assertThrows(JsonParseException.class, () ->
                JsonPatchGenerator.generatePatch(null, "{}"));

        assertThrows(JsonParseException.class, () ->
                JsonPatchGenerator.generatePatch("{\"a\": 1} trailing", "{}"));
    }
}
