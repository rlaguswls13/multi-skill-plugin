package com.msp.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimpleJsonTest {

    @Test
    @DisplayName("SimpleJson serialize and deserialize roundtrip")
    void testRoundtrip() {
        Map<String, Object> original = Map.of(
                "sub", "user-12345",
                "admin", true,
                "count", 42L,
                "roles", List.of("ROLE_USER", "ROLE_ADMIN")
        );

        String json = SimpleJson.toJson(original);
        assertNotNull(json);

        Map<String, Object> parsed = SimpleJson.parseObject(json);
        assertEquals("user-12345", parsed.get("sub"));
        assertEquals(Boolean.TRUE, parsed.get("admin"));
        assertEquals(42L, parsed.get("count"));

        Object roles = parsed.get("roles");
        assertInstanceOf(List.class, roles);
        List<?> roleList = (List<?>) roles;
        assertEquals(2, roleList.size());
        assertEquals("ROLE_USER", roleList.get(0));
        assertEquals("ROLE_ADMIN", roleList.get(1));
    }

    @Test
    @DisplayName("Escape characters in JSON strings")
    void testEscapeCharacters() {
        Map<String, Object> data = Map.of("quote", "Hello \"World\"\nLine 2\tTabbed\\Backslash");
        String json = SimpleJson.toJson(data);
        Map<String, Object> parsed = SimpleJson.parseObject(json);

        assertEquals("Hello \"World\"\nLine 2\tTabbed\\Backslash", parsed.get("quote"));
    }
}
