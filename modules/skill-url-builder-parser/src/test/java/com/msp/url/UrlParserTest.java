package com.msp.url;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlParserTest {

    @Test
    @DisplayName("Parse full absolute URL with port, query, fragment and user info")
    void parseFullUrl() {
        String input = "https://user:pass@api.service.com:8443/v1/resource?key=val&num=123#frag";
        ParsedUrl parsed = UrlParser.parse(input);

        assertEquals("https", parsed.scheme());
        assertEquals("user:pass", parsed.userInfo());
        assertEquals("api.service.com", parsed.host());
        assertEquals(8443, parsed.port());
        assertEquals("/v1/resource", parsed.path());
        assertEquals("val", parsed.queryParams().getFirst("key").orElseThrow());
        assertEquals("123", parsed.queryParams().getFirst("num").orElseThrow());
        assertEquals("frag", parsed.fragment());
    }

    @Test
    @DisplayName("Parse protocol relative URL")
    void parseProtocolRelative() {
        ParsedUrl parsed = UrlParser.parse("//cdn.example.com/assets/main.css");
        assertNull(parsed.scheme());
        assertEquals("cdn.example.com", parsed.host());
        assertEquals("/assets/main.css", parsed.path());
    }

    @Test
    @DisplayName("Parse relative URL path")
    void parseRelative() {
        ParsedUrl parsed = UrlParser.parse("/search?q=test");
        assertNull(parsed.scheme());
        assertNull(parsed.host());
        assertEquals("/search", parsed.path());
        assertEquals("test", parsed.queryParams().getFirst("q").orElseThrow());
    }

    @Test
    @DisplayName("Round-trip parsing and building")
    void roundTrip() {
        String input = "https://example.com/api/v1?page=10#top";
        ParsedUrl parsed = UrlParser.parse(input);
        assertEquals(input, parsed.toUrlString());
    }
}
