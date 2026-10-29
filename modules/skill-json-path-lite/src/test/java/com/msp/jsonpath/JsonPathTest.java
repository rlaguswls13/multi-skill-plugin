package com.msp.jsonpath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonPath & JsonPaths Unit Tests")
class JsonPathTest {

    record Item(String name, double price) {}
    record Order(String orderId, List<Item> items) {}
    record UserProfile(String username, int age, boolean vip, Order order) {}

    private final String sampleJson = """
            {
              "user": {
                "name": "Alice",
                "profile": {
                  "age": 28,
                  "verified": true,
                  "balance": 1500.75
                },
                "orders": [
                  {
                    "id": "ORD-101",
                    "total": 55.0,
                    "items": [
                      {"sku": "A1", "title": "Mechanical Keyboard", "qty": 1},
                      {"sku": "A2", "title": "Wireless Mouse", "qty": 2}
                    ]
                  },
                  {
                    "id": "ORD-102",
                    "total": 120.0,
                    "items": [
                      {"sku": "B1", "title": "Gaming Monitor", "qty": 1}
                    ]
                  }
                ],
                "roles": ["USER", "TESTER"]
              }
            }
            """;

    @Nested
    @DisplayName("Deep Navigation on Raw JSON String")
    class JsonStringNavigation {

        @Test
        @DisplayName("Extracts nested properties and array indices")
        void navigateDeepJson() {
            assertEquals(Optional.of("Alice"), JsonPath.getString(sampleJson, "user.name"));
            assertEquals(Optional.of(28), JsonPath.getInt(sampleJson, "$.user.profile.age"));
            assertEquals(Optional.of(true), JsonPath.getBool(sampleJson, "user.profile.verified"));
            assertEquals(Optional.of(1500.75), JsonPath.getDouble(sampleJson, "user.profile.balance"));

            // Deep array indexing: user.orders[0].items[1].title
            assertEquals(Optional.of("Wireless Mouse"), JsonPath.getString(sampleJson, "user.orders[0].items[1].title"));
            assertEquals(Optional.of(2), JsonPath.getInt(sampleJson, "user.orders[0].items[1].qty"));
        }

        @Test
        @DisplayName("Array length property inspection")
        void inspectLength() {
            assertEquals(Optional.of(2), JsonPath.getInt(sampleJson, "user.orders.length"));
            assertEquals(Optional.of(2), JsonPath.getInt(sampleJson, "user.orders[0].items.size"));
            assertEquals(Optional.of(1), JsonPath.getInt(sampleJson, "user.orders[1].items.length"));
            assertEquals(Optional.of(2), JsonPath.getInt(sampleJson, "user.roles.length"));
        }

        @Test
        @DisplayName("Negative index access: [-1] gets last element")
        void negativeIndex() {
            assertEquals(Optional.of("ORD-102"), JsonPath.getString(sampleJson, "user.orders[-1].id"));
            assertEquals(Optional.of("TESTER"), JsonPath.getString(sampleJson, "user.roles[-1]"));
        }

        @Test
        @DisplayName("Extracts sub-list and sub-map")
        void extractListAndMap() {
            List<Object> roles = JsonPath.getList(sampleJson, "user.roles");
            assertEquals(List.of("USER", "TESTER"), roles);

            Optional<Map<String, Object>> profile = JsonPath.getMap(sampleJson, "user.profile");
            assertTrue(profile.isPresent());
            assertEquals(28, profile.get().get("age"));
        }

        @Test
        @DisplayName("Missing path returns empty Optional, does not throw")
        void missingPathReturnsEmpty() {
            assertTrue(JsonPath.find(sampleJson, "user.nonExistentKey").isEmpty());
            assertTrue(JsonPath.find(sampleJson, "user.orders[999]").isEmpty());
            assertTrue(JsonPath.find(sampleJson, "user.orders[0].items[99].title").isEmpty());
            assertFalse(JsonPath.exists(sampleJson, "user.notHere"));
        }
    }

    @Nested
    @DisplayName("Navigation on Nested Maps and Java Records")
    class JavaObjectNavigation {

        @Test
        @DisplayName("Navigates Java records with dot-notation")
        void recordNavigation() {
            UserProfile user = new UserProfile(
                    "Bob",
                    32,
                    true,
                    new Order("ORD-777", List.of(new Item("Coffee Mug", 12.5), new Item("Notebook", 4.0)))
            );

            assertEquals(Optional.of("Bob"), JsonPaths.getString(user, "username"));
            assertEquals(Optional.of(32), JsonPaths.getInt(user, "age"));
            assertEquals(Optional.of(true), JsonPaths.getBool(user, "vip"));
            assertEquals(Optional.of("ORD-777"), JsonPaths.getString(user, "order.orderId"));
            assertEquals(Optional.of("Coffee Mug"), JsonPaths.getString(user, "order.items[0].name"));
            assertEquals(Optional.of(4.0), JsonPaths.getDouble(user, "order.items[1].price"));
            assertEquals(Optional.of(2), JsonPaths.getInt(user, "order.items.length"));
        }

        @Test
        @DisplayName("Bracket notation with quotes: ['special-key']")
        void bracketWithQuotes() {
            Map<String, Object> map = Map.of(
                    "special-key", Map.of("sub.key", "Found Value")
            );

            assertEquals(Optional.of("Found Value"), JsonPath.getString(map, "['special-key']['sub.key']"));
        }
    }

    @Nested
    @DisplayName("Edge cases and null handling")
    class EdgeCases {

        @Test
        @DisplayName("Null root or empty path returns safely")
        void nullHandling() {
            assertTrue(JsonPath.find((Object) null, "a.b").isEmpty());
            assertEquals(Optional.of("root"), JsonPath.find((Object) "root", ""));
            assertEquals(Optional.of("root"), JsonPath.find((Object) "root", "$"));
            assertEquals(Optional.of("root"), JsonPath.find("\"root\"", "$"));
        }
    }
}
