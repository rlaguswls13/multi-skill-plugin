package com.msp.yaml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YamlParser & YamlWriter Tests")
class YamlParserTest {

    @Test
    @DisplayName("Should parse simple key-value pairs")
    void testSimpleKeyValue() {
        String yaml = """
            title: Quick Start
            version: 1
            active: true
            rate: 4.5
            missing: null
            """;

        Map<String, Object> map = YamlParser.parse(yaml);
        assertEquals("Quick Start", map.get("title"));
        assertEquals(1, map.get("version"));
        assertEquals(true, map.get("active"));
        assertEquals(4.5, map.get("rate"));
        assertNull(map.get("missing"));
    }

    @Test
    @DisplayName("Should parse nested maps with indentation")
    void testNestedMaps() {
        String yaml = """
            server:
              host: 127.0.0.1
              port: 8080
              ssl:
                enabled: true
                cert: server.crt
            """;

        Map<String, Object> map = YamlParser.parse(yaml);
        assertTrue(map.containsKey("server"));

        @SuppressWarnings("unchecked")
        Map<String, Object> server = (Map<String, Object>) map.get("server");
        assertEquals("127.0.0.1", server.get("host"));
        assertEquals(8080, server.get("port"));

        @SuppressWarnings("unchecked")
        Map<String, Object> ssl = (Map<String, Object>) server.get("ssl");
        assertEquals(true, ssl.get("enabled"));
        assertEquals("server.crt", ssl.get("cert"));
    }

    @Test
    @DisplayName("Should parse lists of scalars")
    void testListScalars() {
        String yaml = """
            fruits:
              - apple
              - banana
              - orange
            numbers:
              - 10
              - 20
              - 30
            """;

        Map<String, Object> map = YamlParser.parse(yaml);
        assertEquals(List.of("apple", "banana", "orange"), map.get("fruits"));
        assertEquals(List.of(10, 20, 30), map.get("numbers"));
    }

    @Test
    @DisplayName("Should parse list of maps")
    void testListOfMaps() {
        String yaml = """
            users:
              - id: 1
                name: Alice
                admin: true
              - id: 2
                name: Bob
                admin: false
            """;

        Map<String, Object> map = YamlParser.parse(yaml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = (List<Map<String, Object>>) map.get("users");
        assertEquals(2, users.size());

        assertEquals(1, users.get(0).get("id"));
        assertEquals("Alice", users.get(0).get("name"));
        assertEquals(true, users.get(0).get("admin"));

        assertEquals(2, users.get(1).get("id"));
        assertEquals("Bob", users.get(1).get("name"));
        assertEquals(false, users.get(1).get("admin"));
    }

    @Test
    @DisplayName("Should handle comments properly")
    void testComments() {
        String yaml = """
            # Header comment
            app: my-service # inline comment
            # Middle comment
            port: 9000
            # Footer comment
            """;

        Map<String, Object> map = YamlParser.parse(yaml);
        assertEquals(2, map.size());
        assertEquals("my-service", map.get("app"));
        assertEquals(9000, map.get("port"));
    }

    @Test
    @DisplayName("Should parse literal block scalar (|)")
    void testLiteralBlockScalar() {
        String yaml = """
            description: |
              Line one
              Line two
              Line three
            author: MSP
            """;

        Map<String, Object> map = YamlParser.parse(yaml);
        assertEquals("Line one\nLine two\nLine three", map.get("description"));
        assertEquals("MSP", map.get("author"));
    }

    @Test
    @DisplayName("Should parse folded block scalar (>)")
    void testFoldedBlockScalar() {
        String yaml = """
            summary: >
              This is a long
              single paragraph
              folded into one line.
            """;

        Map<String, Object> map = YamlParser.parse(yaml);
        assertEquals("This is a long single paragraph folded into one line.", map.get("summary"));
    }

    @Test
    @DisplayName("Should parse flow sequences and mappings")
    void testFlowSyntax() {
        String yaml = """
            tags: [java, plugin, yaml]
            point: {x: 10, y: 20}
            """;

        Map<String, Object> map = YamlParser.parse(yaml);
        assertEquals(List.of("java", "plugin", "yaml"), map.get("tags"));

        @SuppressWarnings("unchecked")
        Map<String, Object> point = (Map<String, Object>) map.get("point");
        assertEquals(10, point.get("x"));
        assertEquals(20, point.get("y"));
    }

    @Test
    @DisplayName("Should round-trip dump and parse")
    void testRoundTrip() {
        Map<String, Object> original = Map.of(
            "name", "Demo App",
            "count", 42,
            "active", true,
            "items", List.of("alpha", "beta", "gamma"),
            "config", Map.of("timeout", 5000, "debug", false)
        );

        String yaml = YamlWriter.dump(original);
        assertNotNull(yaml);

        Map<String, Object> parsed = YamlParser.parse(yaml);
        assertEquals("Demo App", parsed.get("name"));
        assertEquals(42, parsed.get("count"));
        assertEquals(true, parsed.get("active"));
        assertEquals(List.of("alpha", "beta", "gamma"), parsed.get("items"));

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) parsed.get("config");
        assertEquals(5000, config.get("timeout"));
        assertEquals(false, config.get("debug"));
    }

    @Test
    @DisplayName("Should throw YamlException on invalid structure")
    void testInvalidYaml() {
        assertThrows(YamlException.class, () -> YamlParser.parse("invalid_no_colon_line"));
    }
}
