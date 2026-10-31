package com.msp.jsonpathfast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FastJsonPathExtractorTest {

    private final String sampleJson = """
            {
              "store": {
                "name": "Central Books",
                "open": true,
                "rating": 4.85,
                "founded": 1998,
                "books": [
                  {
                    "title": "Java Concurrency in Practice",
                    "price": 45,
                    "isbn": "978-0321349606",
                    "available": true,
                    "authors": ["Brian Goetz", "Tim Peierls"]
                  },
                  {
                    "title": "Effective Java",
                    "price": 52.5,
                    "isbn": "978-0134685991",
                    "available": false,
                    "authors": ["Joshua Bloch"]
                  }
                ],
                "location": {
                  "city": "Seoul",
                  "coordinates": [37.5665, 126.9780]
                }
              }
            }
            """;

    @Test
    @DisplayName("Extract string values from nested objects and arrays")
    void testExtractString() {
        Optional<String> storeName = FastJsonPathExtractor.extractString(sampleJson, "store.name");
        assertTrue(storeName.isPresent());
        assertEquals("Central Books", storeName.get());

        Optional<String> book0Title = FastJsonPathExtractor.extractString(sampleJson, "store.books[0].title");
        assertTrue(book0Title.isPresent());
        assertEquals("Java Concurrency in Practice", book0Title.get());

        Optional<String> author = FastJsonPathExtractor.extractString(sampleJson, "store.books[0].authors[1]");
        assertTrue(author.isPresent());
        assertEquals("Tim Peierls", author.get());

        // Supports $. prefix
        Optional<String> city = FastJsonPathExtractor.extractString(sampleJson, "$.store.location.city");
        assertTrue(city.isPresent());
        assertEquals("Seoul", city.get());
    }

    @Test
    @DisplayName("Extract long values")
    void testExtractLong() {
        Optional<Long> founded = FastJsonPathExtractor.extractLong(sampleJson, "store.founded");
        assertTrue(founded.isPresent());
        assertEquals(1998L, founded.get());

        Optional<Long> price = FastJsonPathExtractor.extractLong(sampleJson, "store.books[0].price");
        assertTrue(price.isPresent());
        assertEquals(45L, price.get());
    }

    @Test
    @DisplayName("Extract double values")
    void testExtractDouble() {
        Optional<Double> rating = FastJsonPathExtractor.extractDouble(sampleJson, "store.rating");
        assertTrue(rating.isPresent());
        assertEquals(4.85, rating.get(), 0.001);

        Optional<Double> price = FastJsonPathExtractor.extractDouble(sampleJson, "store.books[1].price");
        assertTrue(price.isPresent());
        assertEquals(52.5, price.get(), 0.001);

        Optional<Double> lat = FastJsonPathExtractor.extractDouble(sampleJson, "store.location.coordinates[0]");
        assertTrue(lat.isPresent());
        assertEquals(37.5665, lat.get(), 0.0001);
    }

    @Test
    @DisplayName("Extract boolean values")
    void testExtractBoolean() {
        Optional<Boolean> open = FastJsonPathExtractor.extractBoolean(sampleJson, "store.open");
        assertTrue(open.isPresent());
        assertTrue(open.get());

        Optional<Boolean> available = FastJsonPathExtractor.extractBoolean(sampleJson, "store.books[1].available");
        assertTrue(available.isPresent());
        assertFalse(available.get());
    }

    @Test
    @DisplayName("Extract raw JSON subtree and slices")
    void testExtractRawJson() {
        Optional<String> locationRaw = FastJsonPathExtractor.extractRawJson(sampleJson, "store.location");
        assertTrue(locationRaw.isPresent());
        assertTrue(locationRaw.get().startsWith("{") && locationRaw.get().endsWith("}"));
        assertTrue(locationRaw.get().contains("\"city\": \"Seoul\""));

        Optional<String> booksRaw = FastJsonPathExtractor.extractRawJson(sampleJson, "store.books");
        assertTrue(booksRaw.isPresent());
        assertTrue(booksRaw.get().startsWith("[") && booksRaw.get().endsWith("]"));

        Optional<String> book0Raw = FastJsonPathExtractor.extractRawJson(sampleJson, "store.books[0]");
        assertTrue(book0Raw.isPresent());
        assertTrue(book0Raw.get().contains("Java Concurrency in Practice"));
    }

    @Test
    @DisplayName("Root array indexing")
    void testRootArray() {
        String jsonArray = """
                [
                  {"id": 101, "name": "Alpha"},
                  {"id": 102, "name": "Beta"}
                ]
                """;

        Optional<String> betaName = FastJsonPathExtractor.extractString(jsonArray, "[1].name");
        assertTrue(betaName.isPresent());
        assertEquals("Beta", betaName.get());

        Optional<Long> id0 = FastJsonPathExtractor.extractLong(jsonArray, "[0].id");
        assertTrue(id0.isPresent());
        assertEquals(101L, id0.get());
    }

    @Test
    @DisplayName("Handle edge cases, missing paths, and malformed inputs gracefully")
    void testEdgeCases() {
        // Missing keys
        assertTrue(FastJsonPathExtractor.extractString(sampleJson, "store.notExisting").isEmpty());
        // Index out of bounds
        assertTrue(FastJsonPathExtractor.extractString(sampleJson, "store.books[99].title").isEmpty());
        assertTrue(FastJsonPathExtractor.extractString(sampleJson, "store.books[-1].title").isEmpty());
        // Null or empty inputs
        assertTrue(FastJsonPathExtractor.extractString(null, "store.name").isEmpty());
        assertTrue(FastJsonPathExtractor.extractString("", "store.name").isEmpty());
        assertTrue(FastJsonPathExtractor.extractString(sampleJson, null).isEmpty());
        // Malformed JSON
        assertTrue(FastJsonPathExtractor.extractString("{invalid_json", "foo").isEmpty());
    }

    @Test
    @DisplayName("Escaped strings in JSON parsing")
    void testEscapedString() {
        String json = "{\"message\": \"Hello \\\"World\\\"\\nLine2\\tTab\"}";
        Optional<String> msg = FastJsonPathExtractor.extractString(json, "message");
        assertTrue(msg.isPresent());
        assertEquals("Hello \"World\"\nLine2\tTab", msg.get());
    }
}
