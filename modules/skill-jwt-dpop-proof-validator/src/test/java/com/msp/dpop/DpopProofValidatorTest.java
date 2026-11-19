package com.msp.dpop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DpopProofValidatorTest {

    private DpopProofValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DpopProofValidator();
    }

    @Test
    @DisplayName("Validates valid ES256 DPoP proof successfully with thumbprint and claims")
    void testValidEs256Proof() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) kp.getPublic();

        String x = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsigned32ByteArray(pub.getW().getAffineX()));
        String y = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsigned32ByteArray(pub.getW().getAffineY()));

        String header = String.format("{\"typ\":\"dpop+jwt\",\"alg\":\"ES256\",\"jwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"%s\",\"y\":\"%s\"}}", x, y);
        long now = Instant.now().getEpochSecond();
        String payload = String.format("{\"jti\":\"jti-ec-1001\",\"htm\":\"POST\",\"htu\":\"https://api.example.com/resource?debug=true\",\"iat\":%d}", now);

        String jwt = createJwt(header, payload, "ES256", kp.getPrivate());

        DpopValidationResult result = validator.validate(jwt, "POST", "https://api.example.com/resource");

        assertTrue(result.isValid(), "Proof should be valid: " + result.failureReason());
        assertNotNull(result.thumbprint());
        assertTrue(result.thumbprint().length() > 20);
        assertTrue(result.getClaims().isPresent());
        assertEquals("jti-ec-1001", result.claims().jti());
        assertEquals("POST", result.claims().htm());
    }

    @Test
    @DisplayName("Validates valid RS256 DPoP proof with bound access token hash (ath)")
    void testValidRs256WithAth() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();

        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getPublicExponent().toByteArray());

        String accessToken = "secret_access_token_xyz_98765";
        String ath = DpopKeyUtil.computeAccessTokenHash(accessToken);

        String header = String.format("{\"typ\":\"dpop+jwt\",\"alg\":\"RS256\",\"jwk\":{\"kty\":\"RSA\",\"n\":\"%s\",\"e\":\"%s\"}}", n, e);
        long now = Instant.now().getEpochSecond();
        String payload = String.format("{\"jti\":\"jti-rsa-2001\",\"htm\":\"GET\",\"htu\":\"https://auth.example.com/userinfo\",\"iat\":%d,\"ath\":\"%s\"}", now, ath);

        String jwt = createJwt(header, payload, "RS256", kp.getPrivate());

        DpopValidationResult result = validator.validate(jwt, "GET", "https://auth.example.com/userinfo", accessToken);

        assertTrue(result.isValid(), "Validation should succeed: " + result.failureReason());
        assertEquals(Optional.of(ath), result.claims().ath());
    }

    @Test
    @DisplayName("Detects and blocks JTI replay attack")
    void testReplayAttackDetection() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) kp.getPublic();

        String x = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsigned32ByteArray(pub.getW().getAffineX()));
        String y = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsigned32ByteArray(pub.getW().getAffineY()));

        String header = String.format("{\"typ\":\"dpop+jwt\",\"alg\":\"ES256\",\"jwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"%s\",\"y\":\"%s\"}}", x, y);
        long now = Instant.now().getEpochSecond();
        String payload = String.format("{\"jti\":\"replay-token-unique-id\",\"htm\":\"DELETE\",\"htu\":\"https://api.example.com/items/12\",\"iat\":%d}", now);

        String jwt = createJwt(header, payload, "ES256", kp.getPrivate());

        // First use: success
        DpopValidationResult first = validator.validate(jwt, "DELETE", "https://api.example.com/items/12");
        assertTrue(first.isValid());

        // Second use: replay blocked!
        DpopValidationResult second = validator.validate(jwt, "DELETE", "https://api.example.com/items/12");
        assertFalse(second.isValid());
        assertTrue(second.failureReason().contains("replay detected"));
    }

    @Test
    @DisplayName("Fails validation when HTTP method or URI does not match")
    void testMethodAndUriMismatch() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) kp.getPublic();

        String x = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsigned32ByteArray(pub.getW().getAffineX()));
        String y = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsigned32ByteArray(pub.getW().getAffineY()));

        String header = String.format("{\"typ\":\"dpop+jwt\",\"alg\":\"ES256\",\"jwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"%s\",\"y\":\"%s\"}}", x, y);
        long now = Instant.now().getEpochSecond();
        String payload = String.format("{\"jti\":\"jti-mismatch-test\",\"htm\":\"POST\",\"htu\":\"https://api.example.com/transfer\",\"iat\":%d}", now);

        String jwt = createJwt(header, payload, "ES256", kp.getPrivate());

        // Wrong method
        DpopValidationResult resMethod = validator.validate(jwt, "GET", "https://api.example.com/transfer");
        assertFalse(resMethod.isValid());
        assertTrue(resMethod.failureReason().contains("HTTP method mismatch"));

        // Wrong URI
        DpopValidationResult resUri = validator.validate(jwt, "POST", "https://api.example.com/other-endpoint");
        assertFalse(resUri.isValid());
        assertTrue(resUri.failureReason().contains("HTTP URI mismatch"));
    }

    @Test
    @DisplayName("Fails validation when typ is not dpop+jwt or JWK contains private key")
    void testInvalidTypOrPrivateKeyInJwk() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) kp.getPublic();

        String x = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsigned32ByteArray(pub.getW().getAffineX()));
        String y = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsigned32ByteArray(pub.getW().getAffineY()));

        // Invalid typ: "JWT" instead of "dpop+jwt"
        String badTypHeader = String.format("{\"typ\":\"JWT\",\"alg\":\"ES256\",\"jwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"%s\",\"y\":\"%s\"}}", x, y);
        long now = Instant.now().getEpochSecond();
        String payload = String.format("{\"jti\":\"jti-typ-test\",\"htm\":\"POST\",\"htu\":\"https://api.example.com/test\",\"iat\":%d}", now);

        String badTypJwt = createJwt(badTypHeader, payload, "ES256", kp.getPrivate());
        DpopValidationResult resTyp = validator.validate(badTypJwt, "POST", "https://api.example.com/test");
        assertFalse(resTyp.isValid());
        assertTrue(resTyp.failureReason().contains("Invalid header 'typ'"));

        // JWK containing private key parameter "d"
        String privateKeyHeader = String.format("{\"typ\":\"dpop+jwt\",\"alg\":\"ES256\",\"jwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"%s\",\"y\":\"%s\",\"d\":\"private_leak\"}}", x, y);
        String badJwkJwt = createJwt(privateKeyHeader, payload, "ES256", kp.getPrivate());
        DpopValidationResult resPriv = validator.validate(badJwkJwt, "POST", "https://api.example.com/test");
        assertFalse(resPriv.isValid());
        assertTrue(resPriv.failureReason().contains("private key"));
    }

    private static byte[] toUnsigned32ByteArray(BigInteger bi) {
        byte[] raw = bi.toByteArray();
        if (raw.length == 32) return raw;
        byte[] out = new byte[32];
        if (raw.length > 32) {
            System.arraycopy(raw, raw.length - 32, out, 0, 32);
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        }
        return out;
    }

    private static String createJwt(String headerJson, String payloadJson, String alg, PrivateKey privateKey) throws Exception {
        String hB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String pB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        byte[] signingInput = (hB64 + "." + pB64).getBytes(StandardCharsets.US_ASCII);

        byte[] sigBytes;
        if ("RS256".equalsIgnoreCase(alg)) {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(signingInput);
            sigBytes = sig.sign();
        } else {
            // For ES256, use IEEE P1363 format
            Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
            sig.initSign(privateKey);
            sig.update(signingInput);
            sigBytes = sig.sign();
        }

        String sB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
        return hB64 + "." + pB64 + "." + sB64;
    }
}
