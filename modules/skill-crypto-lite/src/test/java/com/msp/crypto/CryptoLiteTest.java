package com.msp.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.*;

class CryptoLiteTest {

    @Test
    @DisplayName("CryptoLite facade full end-to-end integration test")
    void testFacadeEndToEnd() {
        // PBKDF2 -> AES-GCM
        byte[] salt = CryptoLite.generateSalt();
        SecretKey key = CryptoLite.deriveAesKey("super-secret", salt);

        String plaintext = "Sensitive Transaction Data";
        String aad = "context-metadata";

        String ciphertext = CryptoLite.encryptAesGcm(plaintext, key, aad);
        String decrypted = CryptoLite.decryptAesGcm(ciphertext, key, aad);
        assertEquals(plaintext, decrypted);

        // HMAC-SHA256
        String signKey = "api-secret";
        String signature = CryptoLite.signHmacHex(plaintext, signKey);
        assertTrue(CryptoLite.verifyHmacHex(plaintext, signature, signKey));
        assertFalse(CryptoLite.verifyHmacHex("altered text", signature, signKey));

        // SHA-256
        String shaHex = CryptoLite.sha256Hex("hello world");
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", shaHex);
    }
}
