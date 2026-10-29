package com.msp.jsonformat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonFormatTest {

    @Test
    @DisplayName("Prettifies compact JSON with default 2-space indentation")
    void testPrettifyCompactJson() {
        String compact = "{\"name\":\"John Doe\",\"age\":30,\"skills\":[\"Java\",\"SQL\"],\"active\":true}";
        String pretty = JsonFormat.prettyPrint(compact);

        String expected = """
                {
                  "name": "John Doe",
                  "age": 30,
                  "skills": [
                    "Java",
                    "SQL"
                  ],
                  "active": true
                }""";

        assertEquals(expected, pretty);
    }

    @Test
    @DisplayName("Minifies formatted JSON removing all redundant whitespace")
    void testMinifyFormattedJson() {
        String pretty = """
                {
                  "id": 101,
                  "title": "Module Builder",
                  "tags": [
                    "Java 21",
                    "Plugin"
                  ]
                }
                """;
        String minified = JsonFormat.minify(pretty);
        assertEquals("{\"id\":101,\"title\":\"Module Builder\",\"tags\":[\"Java 21\",\"Plugin\"]}", minified);
    }

    @Test
    @DisplayName("Preserves colons, commas, braces, and escaped quotes inside string literals")
    void testStringLiteralsWithSpecialCharacters() {
        String json = "{\"message\":\"Status: OK, code: {200}\",\"escaped\":\"Quote: \\\"Hello\\\" and slash \\\\\"}";
        String pretty = JsonFormat.prettyPrint(json);

        assertTrue(pretty.contains("\"message\": \"Status: OK, code: {200}\""));
        assertTrue(pretty.contains("\"escaped\": \"Quote: \\\"Hello\\\" and slash \\\\\""));

        String minified = JsonFormat.minify(pretty);
        assertEquals(json, minified);
    }

    @Test
    @DisplayName("Handles empty objects and arrays cleanly on single lines")
    void testEmptyObjectsAndArrays() {
        String json = "{\"emptyObj\":{},\"emptyArr\":[],\"nested\":[{},[]]}";
        String pretty = JsonFormat.prettyPrint(json);

        assertTrue(pretty.contains("\"emptyObj\": {}"));
        assertTrue(pretty.contains("\"emptyArr\": []"));

        String minified = JsonFormat.minify(pretty);
        assertEquals(json, minified);
    }

    @Test
    @DisplayName("Custom indent spaces and tab indent")
    void testCustomIndentation() {
        String json = "{\"a\":[1,2]}";
        String pretty4 = JsonFormat.prettyPrint(json, 4);
        assertTrue(pretty4.contains("{\n    \"a\": [\n        1,\n        2\n    ]\n}"));

        String prettyTab = JsonFormat.prettyPrint(json, "\t");
        assertTrue(prettyTab.contains("{\n\t\"a\": [\n\t\t1,\n\t\t2\n\t]\n}"));
    }

    @Test
    @DisplayName("Round trip: minify(prettyPrint(json)) equals original minified JSON")
    void testRoundTrip() {
        String complex = "{\"users\":[{\"id\":1,\"name\":\"Alice\",\"roles\":[\"ADMIN\"]},{\"id\":2,\"name\":\"Bob\",\"roles\":[]}],\"total\":2}";
        String pretty = JsonFormat.prettyPrint(complex);
        String reminified = JsonFormat.minify(pretty);

        assertEquals(complex, reminified);
    }

    @Test
    @DisplayName("Throws JsonParseException for unclosed string quotes")
    void testUnclosedStringQuote() {
        assertThrows(JsonParseException.class, () -> JsonFormat.prettyPrint("{\"unclosed: \"value\"}"));
        assertThrows(JsonParseException.class, () -> JsonFormat.minify("{\"unclosed: 123}"));
    }

    @Test
    @DisplayName("Throws JsonParseException for unbalanced brackets or braces")
    void testUnbalancedBrackets() {
        assertThrows(JsonParseException.class, () -> JsonFormat.prettyPrint("{\"a\": [1, 2}"));
        assertThrows(JsonParseException.class, () -> JsonFormat.prettyPrint("{\"a\": 1}}"));
        assertThrows(JsonParseException.class, () -> JsonFormat.minify("{\"unclosed\": 1"));
    }

    @Test
    @DisplayName("Throws JsonParseException on null or blank input")
    void testNullOrBlank() {
        assertThrows(JsonParseException.class, () -> JsonFormat.prettyPrint(null));
        assertThrows(JsonParseException.class, () -> JsonFormat.prettyPrint("   "));
        assertThrows(JsonParseException.class, () -> JsonFormat.minify(null));
        assertThrows(JsonParseException.class, () -> JsonFormat.minify(""));
    }

    @Test
    @DisplayName("isValid returns true for valid JSON and false for invalid JSON")
    void testIsValid() {
        assertTrue(JsonFormat.isValid("{\"valid\": true}"));
        assertTrue(JsonFormat.isValid("[1, 2, 3]"));
        assertFalse(JsonFormat.isValid("{\"invalid\": "));
        assertFalse(JsonFormat.isValid(""));
        assertFalse(JsonFormat.isValid(null));
    }
}
