package com.msp.jsonpath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MiniJson Parser Unit Tests")
class MiniJsonTest {

    @Test
    @DisplayName("Parses basic JSON object with diverse types")
    void parseBasicObject() {
        String json = """
                {
                  "name": "Antigravity",
                  "version": 1,
                  "active": true,
                  "ratio": 3.14,
                  "tags": ["java", "agent", 2026],
                  "extra": null
                }
                """;

        Object parsed = MiniJson.parse(json);
        assertInstanceOf(Map.class, parsed);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;

        assertEquals("Antigravity", map.get("name"));
        assertEquals(1, map.get("version"));
        assertEquals(Boolean.TRUE, map.get("active"));
        assertEquals(3.14, map.get("ratio"));
        assertNull(map.get("extra"));

        assertInstanceOf(List.class, map.get("tags"));
        @SuppressWarnings("unchecked")
        List<Object> tags = (List<Object>) map.get("tags");
        assertEquals(3, tags.size());
        assertEquals("java", tags.get(0));
        assertEquals(2026, tags.get(2));
    }

    @Test
    @DisplayName("Parses string escape sequences")
    void parseEscapedStrings() {
        String json = "{\"escaped\": \"Hello \\\"World\\\" \\n \\t \\\\ \\/ \\u0041\"}";
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) MiniJson.parse(json);
        assertEquals("Hello \"World\" \n \t \\ / A", map.get("escaped"));
    }

    @Test
    @DisplayName("Parses standalone JSON array")
    void parseArray() {
        String json = "[10, 20, 30]";
        Object parsed = MiniJson.parse(json);
        assertInstanceOf(List.class, parsed);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) parsed;
        assertEquals(List.of(10, 20, 30), list);
    }

    @Test
    @DisplayName("Handles empty inputs and invalid JSON")
    void invalidJson() {
        assertNull(MiniJson.parse(null));
        assertNull(MiniJson.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{ unquoted: 1 }"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("[1, 2, "));
    }
}
