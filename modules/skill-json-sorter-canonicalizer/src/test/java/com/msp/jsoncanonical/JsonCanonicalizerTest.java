package com.msp.jsoncanonical;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonCanonicalizerTest {

    @Test
    @DisplayName("Sorts keys lexicographically by UTF-16 code units")
    void testKeySorting() {
        String input = """
                {
                    "zebra": "stripes",
                    "alpha": 1,
                    "beta": true,
                    "apple": "fruit"
                }
                """;
        String expected = "{\"alpha\":1,\"apple\":\"fruit\",\"beta\":true,\"zebra\":\"stripes\"}";
        assertEquals(expected, JsonCanonicalizer.canonicalize(input));
    }

    @Test
    @DisplayName("Deeply nested object key sorting and array preservation")
    void testNestedObjectsAndArrays() {
        String input = """
                {
                    "user": {
                        "name": "Alice",
                        "age": 30,
                        "address": {
                            "zip": "12345",
                            "city": "Seoul"
                        }
                    },
                    "tags": [ "c", "b", "a" ]
                }
                """;
        String expected = "{\"tags\":[\"c\",\"b\",\"a\"],\"user\":{\"address\":{\"city\":\"Seoul\",\"zip\":\"12345\"},\"age\":30,\"name\":\"Alice\"}}";
        assertEquals(expected, JsonCanonicalizer.canonicalize(input));
    }

    @Test
    @DisplayName("RFC 8785 string escaping preserves raw UTF-8 and escapes control characters")
    void testStringEscaping() {
        String input = "{\"message\": \"Hello\\tWorld!\\n한글 사랑 😀 / slash\"}";
        String canonical = JsonCanonicalizer.canonicalize(input);
        assertEquals("{\"message\":\"Hello\\tWorld!\\n한글 사랑 😀 / slash\"}", canonical);
    }

    @Test
    @DisplayName("Number normalization handles integers and decimals properly")
    void testNumberNormalization() {
        String input = "{\"zero\": 0, \"int\": 42, \"float\": 3.14159}";
        String canonical = JsonCanonicalizer.canonicalize(input);
        assertEquals("{\"float\":3.14159,\"int\":42,\"zero\":0}", canonical);
    }

    @Test
    @DisplayName("Map canonicalization produces identical output")
    void testMapCanonicalize() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("z", 99);
        inner.put("a", 1);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("nested", inner);
        root.put("items", List.of("x", "y"));

        String result = JsonCanonicalizer.canonicalize(root);
        assertEquals("{\"items\":[\"x\",\"y\"],\"nested\":{\"a\":1,\"z\":99}}", result);
    }

    @Test
    @DisplayName("SHA-256 hash matches for different whitespace and key orders of same data")
    void testSha256Determinism() {
        String json1 = "{\"b\": 2, \"a\": 1}";
        String json2 = """
                {
                   "a":   1,
                   "b":   2
                }
                """;

        String hash1 = JsonCanonicalizer.sha256Hex(json1);
        String hash2 = JsonCanonicalizer.sha256Hex(json2);

        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("Throws IllegalArgumentException on invalid JSON")
    void testInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> JsonCanonicalizer.canonicalize((String) null));
        assertThrows(IllegalArgumentException.class, () -> JsonCanonicalizer.canonicalize(""));
        assertThrows(IllegalArgumentException.class, () -> JsonCanonicalizer.canonicalize("   "));
        assertThrows(IllegalArgumentException.class, () -> JsonCanonicalizer.canonicalize("{ invalid }"));
        assertThrows(IllegalArgumentException.class, () -> JsonCanonicalizer.canonicalize("{\"key\": }"));
        assertThrows(IllegalArgumentException.class, () -> JsonCanonicalizer.canonicalize("[1, 2, "));
    }
}
