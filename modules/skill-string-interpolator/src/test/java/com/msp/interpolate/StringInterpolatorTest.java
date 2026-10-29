package com.msp.interpolate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StringInterpolatorTest {

    record Address(String city, String zipCode) {}
    record User(String name, Address address) {}

    @Test
    @DisplayName("Basic map key interpolation")
    void testBasicMapInterpolation() {
        Map<String, Object> ctx = Map.of(
                "greeting", "Hello",
                "name", "Alice",
                "year", 2026
        );

        String result = StringInterpolator.interpolate("${greeting}, ${name}! Welcome to ${year}.", ctx);
        assertEquals("Hello, Alice! Welcome to 2026.", result);
    }

    @Test
    @DisplayName("Nested dot-notation with Maps and Java 21 Records")
    void testNestedProperties() {
        User user = new User("Bob", new Address("Seoul", "04524"));
        Map<String, Object> ctx = Map.of("user", user);

        String result = StringInterpolator.interpolate("User: ${user.name}, City: ${user.address.city}, Zip: ${user.address.zipCode}", ctx);
        assertEquals("User: Bob, City: Seoul, Zip: 04524", result);
    }

    @Test
    @DisplayName("Nested dot-notation with nested Maps")
    void testNestedMaps() {
        Map<String, Object> server = Map.of("host", "127.0.0.1", "port", 8080);
        Map<String, Object> config = Map.of("server", server);

        String result = StringInterpolator.interpolate("http://${server.host}:${server.port}/api", config);
        assertEquals("http://127.0.0.1:8080/api", result);
    }

    @Test
    @DisplayName("Escaping prevents interpolation and preserves literal syntax")
    void testEscaping() {
        Map<String, Object> ctx = Map.of("name", "Alice");

        // Escaped \${name} should render as ${name}
        String singleEscape = StringInterpolator.interpolate("Use \\${name} to insert value: ${name}", ctx);
        assertEquals("Use ${name} to insert value: Alice", singleEscape);

        // Double escape \\${name} renders as \Alice
        String doubleEscape = StringInterpolator.interpolate("Backslash: \\\\${name}", ctx);
        assertEquals("Backslash: \\Alice", doubleEscape);
    }

    @Test
    @DisplayName("Missing variable strategy PRESERVE keeps placeholder intact")
    void testMissingPreserve() {
        Map<String, Object> ctx = Map.of("existing", "Found");
        String result = StringInterpolator.interpolate("A: ${existing}, B: ${missing}", ctx);
        assertEquals("A: Found, B: ${missing}", result);
    }

    @Test
    @DisplayName("Missing variable strategy EMPTY replaces placeholder with empty string")
    void testMissingEmpty() {
        Map<String, Object> ctx = Map.of("existing", "Found");
        String result = StringInterpolator.interpolate("A: ${existing}, B: ${missing}", ctx, InterpolationOptions.EMPTY_ON_MISSING);
        assertEquals("A: Found, B: ", result);
    }

    @Test
    @DisplayName("Missing variable strategy THROW throws MissingVariableException")
    void testMissingThrow() {
        Map<String, Object> ctx = Map.of("existing", "Found");
        assertThrows(MissingVariableException.class, () ->
                StringInterpolator.interpolate("A: ${existing}, B: ${missing}", ctx, InterpolationOptions.THROW_ON_MISSING)
        );
    }

    @Test
    @DisplayName("Positional indexed interpolation ({0}, {1}) with escaping")
    void testIndexedInterpolation() {
        String template = "Hello {0}, you have {1} points! Use \\{0\\} for placeholders.";
        String result = StringInterpolator.interpolateIndexed(template, "Alice", 100);
        assertEquals("Hello Alice, you have 100 points! Use {0} for placeholders.", result);
    }

    @Test
    @DisplayName("Positional indexed missing index strategy")
    void testIndexedMissingStrategy() {
        String template = "{0} - {1} - {2}";
        String preserved = StringInterpolator.interpolateIndexed(template, MissingStrategy.PRESERVE, "First");
        assertEquals("First - {1} - {2}", preserved);

        String emptied = StringInterpolator.interpolateIndexed(template, MissingStrategy.EMPTY, "First");
        assertEquals("First -  - ", emptied);

        assertThrows(MissingVariableException.class, () ->
                StringInterpolator.interpolateIndexed(template, MissingStrategy.THROW, "First")
        );
    }

    @Test
    @DisplayName("Null or empty template returns empty string")
    void testEmptyAndNull() {
        assertEquals("", StringInterpolator.interpolate(null, Map.of()));
        assertEquals("", StringInterpolator.interpolate("", Map.of()));
        assertEquals("", StringInterpolator.interpolateIndexed(null));
        assertEquals("", StringInterpolator.interpolateIndexed(""));
    }
}
