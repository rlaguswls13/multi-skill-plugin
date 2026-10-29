package com.msp.url;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UrlBuilderTest {

    @Test
    @DisplayName("Fluent URL construction")
    void buildFullUrl() {
        String url = UrlBuilder.create()
                .https()
                .host("api.example.com")
                .port(8443)
                .path("/v1")
                .appendPath("users", "42")
                .queryParam("details", true)
                .queryParam("lang", "ko")
                .fragment("profile")
                .build();

        assertEquals("https://api.example.com:8443/v1/users/42?details=true&lang=ko#profile", url);
    }

    @Test
    @DisplayName("Path appending handles leading/trailing slashes cleanly")
    void pathCombiner() {
        String url1 = UrlBuilder.create()
                .path("/api/")
                .appendPath("/v1/", "/users/", "profile")
                .build();
        assertEquals("/api/v1/users/profile", url1);

        String url2 = UrlBuilder.create()
                .appendPath("api", "v2", "orders")
                .build();
        assertEquals("api/v2/orders", url2);
    }

    @Test
    @DisplayName("User info support")
    void userInfo() {
        String url = UrlBuilder.create()
                .https()
                .userInfo("admin", "secret")
                .host("example.com")
                .path("/dashboard")
                .build();

        assertEquals("https://admin:secret@example.com/dashboard", url);
    }

    @Test
    @DisplayName("Query parameter manipulation")
    void queryParamManipulation() {
        UrlBuilder builder = UrlBuilder.create()
                .https()
                .host("example.com")
                .queryParams(Map.of("page", 1, "size", 50))
                .addQueryParams("filter", List.of("active", "verified"))
                .queryParam("temp", "delete-me")
                .removeQueryParam("temp");

        String url = builder.build();
        assertTrue(url.contains("filter=active"));
        assertTrue(url.contains("filter=verified"));
        assertFalse(url.contains("delete-me"));
    }

    @Test
    @DisplayName("Pre-population from existing URL string")
    void fromUrlString() {
        String original = "https://api.github.com/repos/owner/repo/pulls?state=open&sort=created#comments";
        UrlBuilder builder = UrlBuilder.from(original);

        builder.queryParam("state", "closed")
                .appendPath("123");

        String modified = builder.build();
        assertTrue(modified.startsWith("https://api.github.com/repos/owner/repo/pulls/123?"));
        assertTrue(modified.contains("state=closed"));
        assertTrue(modified.contains("sort=created"));
        assertTrue(modified.endsWith("#comments"));
    }

    @Test
    @DisplayName("Build URI and ParsedUrl")
    void buildUriAndParsedUrl() {
        UrlBuilder builder = UrlBuilder.create()
                .https()
                .host("example.com")
                .path("/test");

        URI uri = builder.buildUri();
        assertEquals("https", uri.getScheme());
        assertEquals("example.com", uri.getHost());
        assertEquals("/test", uri.getPath());

        ParsedUrl parsed = builder.buildParsed();
        assertEquals("https", parsed.scheme());
        assertEquals("example.com", parsed.host());
    }
}
