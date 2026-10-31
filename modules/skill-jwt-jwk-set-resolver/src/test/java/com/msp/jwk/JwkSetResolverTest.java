package com.msp.jwk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwkSetResolverTest {

    @Test
    @DisplayName("Parse RSA JWK JSON and verify signature with resolved RSAPublicKey")
    void testRsaJwkParsingAndSignatureVerification() throws Exception {
        // 1. Generate RSA Key Pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();

        // 2. Convert public key to JWK
        JwkKey jwk = JwkKey.fromPublicKey("test-rsa-key-1", keyPair.getPublic(), "sig", "RS256");
        String jwksJson = JwkSetResolver.toJwksJson(List.of(jwk));

        // 3. Load JWKS JSON
        JwkSetResolver resolver = new JwkSetResolver(Duration.ofMinutes(10));
        resolver.loadJwkSetJson(jwksJson);

        assertEquals(1, resolver.getKeys().size());
        assertFalse(resolver.isExpired());

        // 4. Resolve key by kid
        PublicKey resolvedKey = resolver.resolveKey("test-rsa-key-1");
        assertNotNull(resolvedKey);
        assertInstanceOf(RSAPublicKey.class, resolvedKey);

        // 5. Sign a message with private key and verify with resolved public key
        byte[] payload = "Hello, RFC 7517 JWKS Resolver!".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(payload);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(resolvedKey);
        verifier.update(payload);
        assertTrue(verifier.verify(signature));
    }

    @Test
    @DisplayName("Parse EC JWK JSON and verify signature with resolved ECPublicKey")
    void testEcJwkParsingAndSignatureVerification() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair keyPair = kpg.generateKeyPair();

        JwkKey jwk = JwkKey.fromPublicKey("ec-key-p256", keyPair.getPublic(), "sig", "ES256");
        String jwksJson = JwkSetResolver.toJwksJson(List.of(jwk));

        JwkSetResolver resolver = new JwkSetResolver(Duration.ofMinutes(5));
        resolver.loadJwkSetJson(jwksJson);

        PublicKey resolvedKey = resolver.resolveKey("ec-key-p256");
        assertNotNull(resolvedKey);

        byte[] payload = "EC Signature Test".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(payload);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(resolvedKey);
        verifier.update(payload);
        assertTrue(verifier.verify(signature));
    }

    @Test
    @DisplayName("Handle unknown kid with IllegalArgumentException")
    void testUnknownKidHandling() {
        String json = """
            {
              "keys": [
                {
                  "kty": "RSA",
                  "kid": "key-99",
                  "use": "sig",
                  "alg": "RS256",
                  "n": "u1II2w",
                  "e": "AQAB"
                }
              ]
            }
            """;
        JwkSetResolver resolver = new JwkSetResolver();
        resolver.loadJwkSetJson(json);

        assertThrows(IllegalArgumentException.class, () -> resolver.resolveKey("non-existent-kid"));
        assertTrue(resolver.findKey("non-existent-kid").isEmpty());
        assertTrue(resolver.findKey("key-99").isPresent());
    }

    @Test
    @DisplayName("TTL cache expiration enforces reload")
    void testTtlCacheExpiration() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();

        JwkKey jwk = JwkKey.fromPublicKey("temp-key", keyPair.getPublic(), "sig", "RS256");
        String jwksJson = JwkSetResolver.toJwksJson(List.of(jwk));

        // Short TTL of 20 milliseconds
        JwkSetResolver resolver = new JwkSetResolver(Duration.ofMillis(20));
        resolver.loadJwkSetJson(jwksJson);

        assertFalse(resolver.isExpired());
        assertNotNull(resolver.resolveKey("temp-key"));

        // Wait for TTL to expire
        Thread.sleep(35);
        assertTrue(resolver.isExpired());

        assertThrows(IllegalStateException.class, () -> resolver.resolveKey("temp-key"));

        // Reload resets expiration
        resolver.loadJwkSetJson(jwksJson);
        assertFalse(resolver.isExpired());
        assertNotNull(resolver.resolveKey("temp-key"));
    }

    @Test
    @DisplayName("Invalid JSON JWKS format throws IllegalArgumentException")
    void testInvalidJwksJson() {
        JwkSetResolver resolver = new JwkSetResolver();
        assertThrows(IllegalArgumentException.class, () -> resolver.loadJwkSetJson("{\"not_keys\": []}"));
        assertThrows(IllegalArgumentException.class, () -> resolver.loadJwkSetJson("invalid json"));
    }
}
