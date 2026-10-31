package com.msp.jsonstream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonStreamSplitterTest {

    private InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Should split NDJSON lines and ignore blank lines")
    void testSplitNdjson() {
        String ndjson = """
                {"id": 1, "type": "USER"}
                
                {"id": 2, "type": "ORDER"}
                  
                {"id": 3, "type": "PAYMENT"}
                """;

        List<String> items = new ArrayList<>();
        JsonStreamSplitter.splitNdjson(toStream(ndjson), items::add);

        assertEquals(3, items.size());
        assertEquals("{\"id\": 1, \"type\": \"USER\"}", items.get(0));
        assertEquals("{\"id\": 2, \"type\": \"ORDER\"}", items.get(1));
        assertEquals("{\"id\": 3, \"type\": \"PAYMENT\"}", items.get(2));
    }

    @Test
    @DisplayName("Should split top-level array of JSON objects")
    void testSplitTopLevelArrayObjects() {
        String json = """
                [
                    {"id": 101, "name": "Alpha"},
                    {"id": 102, "name": "Beta"},
                    {"id": 103, "name": "Gamma"}
                ]
                """;

        List<String> elements = JsonStreamSplitter.splitTopLevelArrayToList(toStream(json));

        assertEquals(3, elements.size());
        assertEquals("{\"id\": 101, \"name\": \"Alpha\"}", elements.get(0));
        assertEquals("{\"id\": 102, \"name\": \"Beta\"}", elements.get(1));
        assertEquals("{\"id\": 103, \"name\": \"Gamma\"}", elements.get(2));
    }

    @Test
    @DisplayName("Should handle deeply nested objects and arrays properly")
    void testDeeplyNestedObjects() {
        String json = """
                [
                    {"level1": {"level2": {"level3": [1, 2, {"level4": "deep"}]}}},
                    {"another": [ [1, 2], [3, 4] ]}
                ]
                """;

        List<String> elements = JsonStreamSplitter.splitTopLevelArrayToList(toStream(json));

        assertEquals(2, elements.size());
        assertEquals("{\"level1\": {\"level2\": {\"level3\": [1, 2, {\"level4\": \"deep\"}]}}}", elements.get(0));
        assertEquals("{\"another\": [ [1, 2], [3, 4] ]}", elements.get(1));
    }

    @Test
    @DisplayName("Should handle escaped quotes and brackets inside string literals")
    void testEscapedQuotesAndSpecialCharsInsideStrings() {
        String json = """
                [
                    {"text": "He said: \\"Hello, world!\\""},
                    {"braces": "contains { and } and [ and ] braces"},
                    "quoted \\"scalar\\""
                ]
                """;

        List<String> elements = JsonStreamSplitter.splitTopLevelArrayToList(toStream(json));

        assertEquals(3, elements.size());
        assertEquals("{\"text\": \"He said: \\\"Hello, world!\\\"\"}", elements.get(0));
        assertEquals("{\"braces\": \"contains { and } and [ and ] braces\"}", elements.get(1));
        assertEquals("\"quoted \\\"scalar\\\"\"", elements.get(2));
    }

    @Test
    @DisplayName("Should handle mixed scalar and complex elements in array")
    void testMixedArrayElements() {
        String json = "[ 42, \"hello\", true, null, [1, 2], {\"a\": 1} ]";

        List<String> elements = JsonStreamSplitter.splitTopLevelArrayToList(toStream(json));

        assertEquals(6, elements.size());
        assertEquals("42", elements.get(0));
        assertEquals("\"hello\"", elements.get(1));
        assertEquals("true", elements.get(2));
        assertEquals("null", elements.get(3));
        assertEquals("[1, 2]", elements.get(4));
        assertEquals("{\"a\": 1}", elements.get(5));
    }

    @Test
    @DisplayName("Should handle empty stream and empty array")
    void testEmptyStreamAndEmptyArray() {
        List<String> emptyStreamList = JsonStreamSplitter.splitTopLevelArrayToList(toStream(""));
        assertTrue(emptyStreamList.isEmpty());

        List<String> emptyArrayList = JsonStreamSplitter.splitTopLevelArrayToList(toStream("   [   ]  "));
        assertTrue(emptyArrayList.isEmpty());

        List<String> emptyNdjsonList = JsonStreamSplitter.splitNdjsonToList(toStream("   \n\n  \r\n  "));
        assertTrue(emptyNdjsonList.isEmpty());
    }

    @Test
    @DisplayName("Should throw JsonStreamException for non-array start")
    void testMalformedArrayStart() {
        assertThrows(JsonStreamException.class, () ->
                JsonStreamSplitter.splitTopLevelArrayToList(toStream("{\"not\": \"an array\"}"))
        );
    }
}
