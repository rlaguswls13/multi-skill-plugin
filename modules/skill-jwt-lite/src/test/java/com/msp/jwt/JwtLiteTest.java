package com.msp.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtLiteTest {

    private static final String SECRET_KEY = "my-ultra-secure-256-bit-secret-key-multi-skill!";

    @Test
    @DisplayName("HS256 sign and parse happy path with standard and custom claims")
    void testHs256HappyPath() {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(3600); // +1 hour

        String token = JwtLite.builder()
                .subject("john.doe@example.com")
                .issuer("https://auth.example.com")
                .audience("api-gateway")
                .issuedAt(now)
                .expiresAt(exp)
                .claim("userId", 1001L)
                .claim("role", "ADMIN")
                .signWith(JwtLite.hs256(SECRET_KEY))
                .compact();

        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);

        JwtToken parsed = JwtLite.parser()
                .verifyWith(JwtLite.hs256(SECRET_KEY))
                .requireIssuer("https://auth.example.com")
                .requireAudience("api-gateway")
                .requireSubject("john.doe@example.com")
                .parse(token);

        assertEquals("HS256", parsed.algorithm());
        assertEquals("JWT", parsed.type());
        assertEquals("john.doe@example.com", parsed.subject());
        assertEquals("https://auth.example.com", parsed.issuer());
        assertEquals("api-gateway", parsed.claims().audience());
        assertEquals(1001L, parsed.claims().getLong("userId"));
        assertEquals("ADMIN", parsed.claims().getString("role"));
        assertEquals(exp.getEpochSecond(), parsed.expiresAt().getEpochSecond());
    }

    @Test
    @DisplayName("Tampered signature must be rejected with JwtSignatureException")
    void testTamperedSignature() {
        String token = JwtLite.builder()
                .subject("victim")
                .expiresAt(Instant.now().plusSeconds(300))
                .signWith(JwtLite.hs256(SECRET_KEY))
                .compact();

        String[] parts = token.split("\\.");
        // Modify last character of signature
        String tamperedSig = parts[2].substring(0, parts[2].length() - 1) + (parts[2].endsWith("A") ? "B" : "A");
        String tamperedToken = parts[0] + "." + parts[1] + "." + tamperedSig;

        assertThrows(JwtSignatureException.class, () ->
                JwtLite.parser().verifyWith(JwtLite.hs256(SECRET_KEY)).parse(tamperedToken)
        );
    }

    @Test
    @DisplayName("Tampered payload must be rejected with JwtSignatureException")
    void testTamperedPayload() {
        String token = JwtLite.builder()
                .subject("normal-user")
                .expiresAt(Instant.now().plusSeconds(300))
                .signWith(JwtLite.hs256(SECRET_KEY))
                .compact();

        String[] parts = token.split("\\.");
        // Change payload to grant admin
        String fakePayloadJson = "{\"sub\":\"admin\",\"role\":\"ROOT\"}";
        String fakePayloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fakePayloadJson.getBytes(StandardCharsets.UTF_8));
        String tamperedToken = parts[0] + "." + fakePayloadB64 + "." + parts[2];

        assertThrows(JwtSignatureException.class, () ->
                JwtLite.parser().verifyWith(JwtLite.hs256(SECRET_KEY)).parse(tamperedToken)
        );
    }

    @Test
    @DisplayName("Expired token must be rejected with JwtExpiredException")
    void testExpiredToken() {
        Instant pastExp = Instant.now().minusSeconds(10); // Expired 10 seconds ago

        String token = JwtLite.builder()
                .subject("expired-user")
                .expiresAt(pastExp)
                .signWith(JwtLite.hs256(SECRET_KEY))
                .compact();

        assertThrows(JwtExpiredException.class, () ->
                JwtLite.parser().verifyWith(JwtLite.hs256(SECRET_KEY)).parse(token)
        );

        // However, with enough clockSkew, it should succeed
        JwtToken accepted = JwtLite.parser()
                .verifyWith(JwtLite.hs256(SECRET_KEY))
                .clockSkew(Duration.ofSeconds(30))
                .parse(token);

        assertNotNull(accepted);
    }

    @Test
    @DisplayName("Premature token (not valid before nbf) must be rejected with JwtPrematureException")
    void testPrematureToken() {
        Instant futureNbf = Instant.now().plusSeconds(300); // Valid in 5 minutes

        String token = JwtLite.builder()
                .subject("future-user")
                .notBefore(futureNbf)
                .signWith(JwtLite.hs256(SECRET_KEY))
                .compact();

        assertThrows(JwtPrematureException.class, () ->
                JwtLite.parser().verifyWith(JwtLite.hs256(SECRET_KEY)).parse(token)
        );
    }

    @Test
    @DisplayName("Issuer, audience, and subject mismatch should throw JwtValidationException")
    void testClaimValidationMismatches() {
        String token = JwtLite.builder()
                .subject("user-1")
                .issuer("auth.org")
                .audience("service-a")
                .signWith(JwtLite.hs256(SECRET_KEY))
                .compact();

        assertThrows(JwtValidationException.class, () ->
                JwtLite.parser().verifyWith(JwtLite.hs256(SECRET_KEY)).requireIssuer("wrong-issuer").parse(token)
        );

        assertThrows(JwtValidationException.class, () ->
                JwtLite.parser().verifyWith(JwtLite.hs256(SECRET_KEY)).requireAudience("wrong-audience").parse(token)
        );

        assertThrows(JwtValidationException.class, () ->
                JwtLite.parser().verifyWith(JwtLite.hs256(SECRET_KEY)).requireSubject("user-2").parse(token)
        );
    }

    @Test
    @DisplayName("RS256 asymmetric signing and verification using KeyPairGenerator")
    void testRs256HappyPath() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();

        String token = JwtLite.builder()
                .subject("rsa-user")
                .issuer("https://rsa-issuer.com")
                .claim("tenant", "enterprise")
                .signWith(JwtLite.rs256(keyPair.getPrivate()))
                .compact();

        assertNotNull(token);

        JwtToken parsed = JwtLite.parser()
                .verifyWith(JwtLite.rs256(keyPair.getPublic()))
                .requireIssuer("https://rsa-issuer.com")
                .parse(token);

        assertEquals("RS256", parsed.algorithm());
        assertEquals("rsa-user", parsed.subject());
        assertEquals("enterprise", parsed.claims().getString("tenant"));
    }

    @Test
    @DisplayName("Malformed token strings must throw JwtException")
    void testMalformedTokens() {
        assertThrows(JwtException.class, () -> JwtLite.parser().parse(null));
        assertThrows(JwtException.class, () -> JwtLite.parser().parse(""));
        assertThrows(JwtException.class, () -> JwtLite.parser().parse("only.two.parts"));
        assertThrows(JwtException.class, () -> JwtLite.parser().parse("one.two.three.four"));
    }
}
