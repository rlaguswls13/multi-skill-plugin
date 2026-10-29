package com.msp.url;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryParamsTest {

    @Test
    @DisplayName("Set and get parameters")
    void setAndGet() {
        QueryParams params = new QueryParams();
        params.set("page", "1");
        params.set("size", "20");

        assertEquals("1", params.getFirst("page").orElseThrow());
        assertEquals("20", params.getFirst("size").orElseThrow());
        assertTrue(params.containsKey("page"));
        assertFalse(params.containsKey("sort"));
        assertEquals(2, params.size());

        // Overwrite
        params.set("page", "2");
        assertEquals("2", params.getFirst("page").orElseThrow());
    }

    @Test
    @DisplayName("Multi-value parameters support")
    void multiValues() {
        QueryParams params = new QueryParams();
        params.add("tag", "java");
        params.add("tag", "spring");
        params.add("tag", "boot");

        List<String> tags = params.getAll("tag");
        assertEquals(List.of("java", "spring", "boot"), tags);
        assertEquals("java", params.getFirst("tag").orElseThrow());

        assertEquals("tag=java&tag=spring&tag=boot", params.encode());
    }

    @Test
    @DisplayName("Encoding and decoding special characters")
    void encodingDecoding() {
        QueryParams params = new QueryParams();
        params.set("search", "안녕 세상");
        params.set("symbol", "a+b=c&d");

        String encoded = params.encode();
        QueryParams parsed = QueryParams.parse(encoded);

        assertEquals("안녕 세상", parsed.getFirst("search").orElseThrow());
        assertEquals("a+b=c&d", parsed.getFirst("symbol").orElseThrow());
    }

    @Test
    @DisplayName("Parsing query strings with and without question mark")
    void parsing() {
        QueryParams p1 = QueryParams.parse("?foo=bar&baz=123");
        assertEquals("bar", p1.getFirst("foo").orElseThrow());
        assertEquals("123", p1.getFirst("baz").orElseThrow());

        QueryParams p2 = QueryParams.parse("foo=bar&baz=123");
        assertEquals("bar", p2.getFirst("foo").orElseThrow());

        QueryParams empty = QueryParams.parse("");
        assertTrue(empty.isEmpty());

        QueryParams nullStr = QueryParams.parse(null);
        assertTrue(nullStr.isEmpty());
    }

    @Test
    @DisplayName("To single value and multi value maps")
    void mapConversions() {
        QueryParams params = new QueryParams();
        params.add("tag", "v1");
        params.add("tag", "v2");
        params.set("limit", "10");

        assertEquals("v1", params.toSingleValueMap().get("tag"));
        assertEquals("10", params.toSingleValueMap().get("limit"));
        assertEquals(List.of("v1", "v2"), params.toMultiValueMap().get("tag"));
    }
}
