package com.msp.sessioncookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SignedSessionCookieTest {

    private static final String SECRET_KEY = "my-super-secret-secure-key-123456789";

    @Test
    @DisplayName("Serialization and deserialization roundtrip with UTF-8 characters")
    void testRoundTrip() {
        Map<String, String> session = Map.of(
                "userId", "user-1234",
                "role", "ADMIN",
                "name", "홍길동",
                "email", "user@test.org"
        );

        String cookie = SignedSessionCookie.serialize(session, SECRET_KEY, Duration.ofMinutes(15));
        assertNotNull(cookie);
        assertEquals(3, cookie.split("\\.").length);

        Optional<Map<String, String>> result = SignedSessionCookie.deserialize(cookie, SECRET_KEY);
        assertTrue(result.isPresent());
        assertEquals("user-1234", result.get().get("userId"));
        assertEquals("ADMIN", result.get().get("role"));
        assertEquals("홍길동", result.get().get("name"));
        assertEquals("user@test.org", result.get().get("email"));
    }

    @Test
    @DisplayName("Tampered payload is rejected")
    void testTamperedPayload() {
        Map<String, String> session = Map.of("role", "USER");
        String cookie = SignedSessionCookie.serialize(session, SECRET_KEY, Duration.ofHours(1));

        String[] parts = cookie.split("\\.");
        // Tamper with base64 payload
        String tamperedCookie = "e30" + "." + parts[1] + "." + parts[2];

        Optional<Map<String, String>> result = SignedSessionCookie.deserialize(tamperedCookie, SECRET_KEY);
        assertTrue(result.isEmpty(), "Tampered payload must return empty Optional");
    }

    @Test
    @DisplayName("Tampered expiration timestamp is rejected")
    void testTamperedExpiration() {
        Map<String, String> session = Map.of("userId", "100");
        String cookie = SignedSessionCookie.serialize(session, SECRET_KEY, Duration.ofHours(1));

        String[] parts = cookie.split("\\.");
        // Extend expiration timestamp
        long newExpiry = Long.parseLong(parts[1]) + 100000;
        String tamperedCookie = parts[0] + "." + newExpiry + "." + parts[2];

        Optional<Map<String, String>> result = SignedSessionCookie.deserialize(tamperedCookie, SECRET_KEY);
        assertTrue(result.isEmpty(), "Tampered expiration timestamp must fail signature verification");
    }

    @Test
    @DisplayName("Expired cookie is rejected")
    void testExpiredCookie() {
        Instant now = Instant.parse("2026-10-31T10:00:00Z");
        Clock currentClock = Clock.fixed(now, ZoneOffset.UTC);

        Map<String, String> session = Map.of("key", "val");
        // Expires at 10:05:00Z
        String cookie = SignedSessionCookie.serialize(session, SECRET_KEY, now.plus(Duration.ofMinutes(5)));

        // 1. Valid before expiration (10:04:00Z)
        Clock clockBefore = Clock.fixed(now.plus(Duration.ofMinutes(4)), ZoneOffset.UTC);
        assertTrue(SignedSessionCookie.deserialize(cookie, SECRET_KEY, clockBefore).isPresent());

        // 2. Expired after expiration (10:06:00Z)
        Clock clockAfter = Clock.fixed(now.plus(Duration.ofMinutes(6)), ZoneOffset.UTC);
        assertTrue(SignedSessionCookie.deserialize(cookie, SECRET_KEY, clockAfter).isEmpty());
    }

    @Test
    @DisplayName("Different secret key fails deserialization")
    void testWrongSecretKey() {
        Map<String, String> session = Map.of("user", "alice");
        String cookie = SignedSessionCookie.serialize(session, SECRET_KEY, Duration.ofMinutes(10));

        Optional<Map<String, String>> result = SignedSessionCookie.deserialize(cookie, "wrong-secret-key-99999");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Empty or blank inputs fail gracefully")
    void testEdgeCases() {
        assertTrue(SignedSessionCookie.deserialize(null, SECRET_KEY).isEmpty());
        assertTrue(SignedSessionCookie.deserialize("", SECRET_KEY).isEmpty());
        assertTrue(SignedSessionCookie.deserialize("invalid.cookie", SECRET_KEY).isEmpty());
        assertTrue(SignedSessionCookie.deserialize("a.b.c.d", SECRET_KEY).isEmpty());

        // Empty session data
        String emptyCookie = SignedSessionCookie.serialize(Map.of(), SECRET_KEY, Duration.ofMinutes(5));
        Optional<Map<String, String>> emptyResult = SignedSessionCookie.deserialize(emptyCookie, SECRET_KEY);
        assertTrue(emptyResult.isPresent());
        assertTrue(emptyResult.get().isEmpty());
    }

    @Test
    @DisplayName("Extraction of expiration instant")
    void testGetExpiration() {
        Instant expires = Instant.ofEpochSecond(1761900000L);
        String cookie = SignedSessionCookie.serialize(Map.of("a", "b"), SECRET_KEY, expires);

        Optional<Instant> parsed = SignedSessionCookie.getExpiration(cookie);
        assertTrue(parsed.isPresent());
        assertEquals(expires, parsed.get());
        assertTrue(SignedSessionCookie.getExpiration("corrupted").isEmpty());
    }
}
