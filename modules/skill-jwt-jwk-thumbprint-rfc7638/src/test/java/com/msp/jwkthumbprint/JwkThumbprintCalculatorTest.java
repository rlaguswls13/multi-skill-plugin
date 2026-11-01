package com.msp.jwkthumbprint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwkThumbprintCalculator Unit Tests")
class JwkThumbprintCalculatorTest {

    // RFC 7638 Section 3.1 Official Test Vectors
    private static final String RFC7638_N =
            "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw";
    private static final String RFC7638_E = "AQAB";
    private static final String EXPECTED_THUMBPRINT = "NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs";

    @Test
    @DisplayName("Verify RFC 7638 official RSA test vector from modulus and exponent")
    void testRfc7638OfficialVectorDirect() {
        String thumbprint = JwkThumbprintCalculator.computeRsaThumbprint(RFC7638_N, RFC7638_E);
        assertEquals(EXPECTED_THUMBPRINT, thumbprint, "Calculated thumbprint must strictly match RFC 7638 Section 3.1");
    }

    @Test
    @DisplayName("Verify RFC 7638 official RSA test vector from full JWK JSON")
    void testRfc7638OfficialVectorFromJson() {
        String jwkJson = """
                {
                  "kty": "RSA",
                  "n": "%s",
                  "e": "%s",
                  "alg": "RS256",
                  "kid": "2011-04-29"
                }
                """.formatted(RFC7638_N, RFC7638_E);

        String thumbprint = JwkThumbprintCalculator.computeThumbprintFromJwkJson(jwkJson);
        assertEquals(EXPECTED_THUMBPRINT, thumbprint);
    }

    @Test
    @DisplayName("JSON key ordering and whitespace invariance")
    void testJsonOrderingAndWhitespaceInvariance() {
        // Different key order, extra spaces, arbitrary additional fields like 'kid', 'use'
        String jwkJsonReversed = """
                {
                   "use": "sig",
                   "e": "%s",
                   "alg": "RS256",
                   "n": "%s",
                   "kty": "RSA"
                }
                """.formatted(RFC7638_E, RFC7638_N);

        String thumbprint = JwkThumbprintCalculator.computeThumbprintFromJwkJson(jwkJsonReversed);
        assertEquals(EXPECTED_THUMBPRINT, thumbprint);
    }

    @Test
    @DisplayName("Verify EC public key thumbprint")
    void testEcThumbprint() {
        String crv = "P-256";
        String x = "f83OJ3D2xFMTbKEBaGizcl76jcDxqJbn8YQChIIwvTk";
        String y = "x_daQauaubbgFGsqaDZTlIGMbQZsK_n9SPTHqOkpmrE";

        String thumbprint = JwkThumbprintCalculator.computeEcThumbprint(crv, x, y);
        assertNotNull(thumbprint);
        assertEquals(43, thumbprint.length(), "SHA-256 Base64URL string without padding has length 43");

        String jwkJson = """
                {
                  "kty": "EC",
                  "crv": "%s",
                  "x": "%s",
                  "y": "%s"
                }
                """.formatted(crv, x, y);

        assertEquals(thumbprint, JwkThumbprintCalculator.computeThumbprintFromJwkJson(jwkJson));
    }

    @Test
    @DisplayName("Verify RSA RSAPublicKey thumbprint calculation")
    void testRsaPublicKeyInstance() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPublicKey pubKey = (RSAPublicKey) kp.getPublic();

        String thumbprint = JwkThumbprintCalculator.computeRsaThumbprint(pubKey);
        assertNotNull(thumbprint);
        assertEquals(43, thumbprint.length());

        // Verify matches manual n & e
        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(JwkThumbprintCalculator.toUnsignedByteArray(pubKey.getModulus()));
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(JwkThumbprintCalculator.toUnsignedByteArray(pubKey.getPublicExponent()));
        assertEquals(thumbprint, JwkThumbprintCalculator.computeRsaThumbprint(n, e));
    }

    @Test
    @DisplayName("Error cases: invalid JSON, missing required fields, unsupported kty")
    void testErrorCases() {
        // Not a JSON object
        assertThrows(IllegalArgumentException.class, () ->
                JwkThumbprintCalculator.computeThumbprintFromJwkJson("not-json"));

        // Missing kty
        assertThrows(IllegalArgumentException.class, () ->
                JwkThumbprintCalculator.computeThumbprintFromJwkJson("{\"n\":\"abc\",\"e\":\"def\"}"));

        // Missing n in RSA
        assertThrows(IllegalArgumentException.class, () ->
                JwkThumbprintCalculator.computeThumbprintFromJwkJson("{\"kty\":\"RSA\",\"e\":\"AQAB\"}"));

        // Missing crv in EC
        assertThrows(IllegalArgumentException.class, () ->
                JwkThumbprintCalculator.computeThumbprintFromJwkJson("{\"kty\":\"EC\",\"x\":\"1\",\"y\":\"2\"}"));

        // Unsupported kty
        assertThrows(IllegalArgumentException.class, () ->
                JwkThumbprintCalculator.computeThumbprintFromJwkJson("{\"kty\":\"UNKNOWN\"}"));

        // Blank parameters
        assertThrows(IllegalArgumentException.class, () ->
                JwkThumbprintCalculator.computeRsaThumbprint("", "AQAB"));
        assertThrows(IllegalArgumentException.class, () ->
                JwkThumbprintCalculator.computeEcThumbprint("P-256", " ", "y"));
    }
}
