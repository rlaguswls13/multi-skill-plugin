package com.msp.pkcs8;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PEM PKCS#8 & PKCS#1 RSA Key Loader Tests")
class PemKeyLoaderTest {

    private static KeyPair testKeyPair;

    @BeforeAll
    static void generateTestKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        testKeyPair = generator.generateKeyPair();
    }

    @Test
    @DisplayName("PKCS#8 Private Key: export to PEM and re-parse successfully")
    void testPkcs8PrivateKeyRoundtrip() {
        String pem = PemKeyLoader.toPem(testKeyPair.getPrivate());
        assertTrue(pem.startsWith("-----BEGIN PRIVATE KEY-----"));
        assertTrue(pem.endsWith("-----END PRIVATE KEY-----"));

        PrivateKey parsedKey = PemKeyLoader.parsePrivateKey(pem);
        assertNotNull(parsedKey);
        assertEquals("RSA", parsedKey.getAlgorithm());
        assertArrayEquals(testKeyPair.getPrivate().getEncoded(), parsedKey.getEncoded());
    }

    @Test
    @DisplayName("X.509 Public Key: export to PEM and re-parse successfully")
    void testX509PublicKeyRoundtrip() {
        String pem = PemKeyLoader.toPem(testKeyPair.getPublic());
        assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"));
        assertTrue(pem.endsWith("-----END PUBLIC KEY-----"));

        PublicKey parsedKey = PemKeyLoader.parsePublicKey(pem);
        assertNotNull(parsedKey);
        assertEquals("RSA", parsedKey.getAlgorithm());
        assertArrayEquals(testKeyPair.getPublic().getEncoded(), parsedKey.getEncoded());
    }

    @Test
    @DisplayName("PKCS#1 RSA Private Key: parse ASN.1 DER successfully")
    void testPkcs1PrivateKeyRoundtrip() {
        RSAPrivateCrtKey crtKey = (RSAPrivateCrtKey) testKeyPair.getPrivate();
        String pkcs1Pem = PemKeyLoader.toPkcs1Pem(crtKey);

        assertTrue(pkcs1Pem.startsWith("-----BEGIN RSA PRIVATE KEY-----"));
        assertTrue(pkcs1Pem.endsWith("-----END RSA PRIVATE KEY-----"));

        PrivateKey parsedKey = PemKeyLoader.parsePrivateKey(pkcs1Pem);
        assertNotNull(parsedKey);
        assertEquals("RSA", parsedKey.getAlgorithm());
        RSAPrivateCrtKey parsedCrt = (RSAPrivateCrtKey) parsedKey;
        assertEquals(crtKey.getModulus(), parsedCrt.getModulus());
        assertEquals(crtKey.getPrivateExponent(), parsedCrt.getPrivateExponent());
        assertEquals(crtKey.getPublicExponent(), parsedCrt.getPublicExponent());
    }

    @Test
    @DisplayName("Sign and Verify SHA256withRSA with parsed keys")
    void testSignAndVerifyWithParsedKeys() throws Exception {
        String privPem = PemKeyLoader.toPem(testKeyPair.getPrivate());
        String pubPem = PemKeyLoader.toPem(testKeyPair.getPublic());

        PrivateKey loadedPrivate = PemKeyLoader.parsePrivateKey(privPem);
        PublicKey loadedPublic = PemKeyLoader.parsePublicKey(pubPem);

        byte[] payload = "Hello RS256 JWT Payload!".getBytes(StandardCharsets.UTF_8);

        // Sign with loaded private key
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(loadedPrivate);
        signer.update(payload);
        byte[] signature = signer.sign();

        // Verify with loaded public key
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(loadedPublic);
        verifier.update(payload);
        assertTrue(verifier.verify(signature));

        // Negative check: altered payload must fail verification
        verifier.initVerify(loadedPublic);
        verifier.update("Tampered payload".getBytes(StandardCharsets.UTF_8));
        assertFalse(verifier.verify(signature));
    }

    @Test
    @DisplayName("parseKeyPair parses both private and public PEMs simultaneously")
    void testParseKeyPair() {
        String privPem = PemKeyLoader.toPem(testKeyPair.getPrivate());
        String pubPem = PemKeyLoader.toPem(testKeyPair.getPublic());

        KeyPair keyPair = PemKeyLoader.parseKeyPair(privPem, pubPem);
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
        assertEquals(testKeyPair.getPrivate().getAlgorithm(), keyPair.getPrivate().getAlgorithm());
        assertEquals(testKeyPair.getPublic().getAlgorithm(), keyPair.getPublic().getAlgorithm());
    }

    @Test
    @DisplayName("Invalid PEM format throws IllegalArgumentException")
    void testInvalidPemHandling() {
        assertThrows(NullPointerException.class, () -> PemKeyLoader.parsePrivateKey(null));
        assertThrows(IllegalArgumentException.class, () -> PemKeyLoader.parsePrivateKey(""));
        assertThrows(IllegalArgumentException.class, () -> PemKeyLoader.parsePrivateKey("Not a PEM key"));

        // Corrupted base64
        String corruptedPem = "-----BEGIN PRIVATE KEY-----\n###NOT_BASE64###\n-----END PRIVATE KEY-----";
        assertThrows(IllegalArgumentException.class, () -> PemKeyLoader.parsePrivateKey(corruptedPem));

        // Unsupported header
        String unsupportedPem = "-----BEGIN CERTIFICATE-----\nMIIB...\n-----END CERTIFICATE-----";
        assertThrows(IllegalArgumentException.class, () -> PemKeyLoader.parsePrivateKey(unsupportedPem));

        // Corrupted ASN.1 DER inside valid Base64
        String badDerPem = "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----";
        assertThrows(IllegalArgumentException.class, () -> PemKeyLoader.parsePrivateKey(badDerPem));
    }
}
