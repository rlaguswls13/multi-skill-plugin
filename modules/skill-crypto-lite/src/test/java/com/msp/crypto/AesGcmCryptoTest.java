package com.msp.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmCryptoTest {

    @Test
    @DisplayName("AES-GCM encrypt and decrypt byte array roundtrip")
    void testByteRoundtrip() {
        SecretKey key = AesGcmCrypto.generateKey();
        byte[] plaintext = "Hello, Multi-Skill Plugin AES-GCM!".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = AesGcmCrypto.encrypt(plaintext, key);
        assertNotNull(encrypted);
        assertTrue(encrypted.length >= plaintext.length + AesGcmCrypto.MIN_PAYLOAD_LENGTH_BYTES);

        byte[] decrypted = AesGcmCrypto.decrypt(encrypted, key);
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("AES-GCM Base64 String encrypt and decrypt roundtrip")
    void testBase64StringRoundtrip() {
        SecretKey key = AesGcmCrypto.generateKey();
        String originalText = "Sensitive user information: PII-987654321";

        String base64Encrypted = AesGcmCrypto.encryptToBase64(originalText, key);
        assertNotNull(base64Encrypted);
        assertNotEquals(originalText, base64Encrypted);

        String decrypted = AesGcmCrypto.decryptFromBase64(base64Encrypted, key);
        assertEquals(originalText, decrypted);
    }

    @Test
    @DisplayName("Repeated encryption of identical plaintext must produce distinct ciphertexts due to random IV")
    void testRandomIvPerEncryption() {
        SecretKey key = AesGcmCrypto.generateKey();
        String message = "Confidential Payload";

        String cipher1 = AesGcmCrypto.encryptToBase64(message, key);
        String cipher2 = AesGcmCrypto.encryptToBase64(message, key);

        assertNotEquals(cipher1, cipher2, "Ciphertexts must differ due to randomized 12-byte IV");
        assertEquals(message, AesGcmCrypto.decryptFromBase64(cipher1, key));
        assertEquals(message, AesGcmCrypto.decryptFromBase64(cipher2, key));
    }

    @Test
    @DisplayName("Tampered ciphertext or IV must cause authentication failure (CryptoException)")
    void testTamperDetection() {
        SecretKey key = AesGcmCrypto.generateKey();
        byte[] plaintext = "Integrity check test".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = AesGcmCrypto.encrypt(plaintext, key);

        // Tamper with a byte in ciphertext/tag
        byte[] tampered = ciphertext.clone();
        tampered[tampered.length - 1] ^= 0x01; // flip 1 bit in tag

        assertThrows(CryptoException.class, () -> AesGcmCrypto.decrypt(tampered, key));

        // Tamper with IV
        byte[] tamperedIv = ciphertext.clone();
        tamperedIv[0] ^= 0x01; // flip 1 bit in IV
        assertThrows(CryptoException.class, () -> AesGcmCrypto.decrypt(tamperedIv, key));
    }

    @Test
    @DisplayName("AAD (Additional Authenticated Data) must be verified successfully")
    void testAadVerification() {
        SecretKey key = AesGcmCrypto.generateKey();
        String plaintext = "Financial Transaction";
        String aad = "user-id-1002";

        String encrypted = AesGcmCrypto.encryptToBase64(plaintext, key, aad);

        // Successful decryption with matching AAD
        String decrypted = AesGcmCrypto.decryptFromBase64(encrypted, key, aad);
        assertEquals(plaintext, decrypted);

        // Failure when AAD does not match
        assertThrows(CryptoException.class, () ->
                AesGcmCrypto.decryptFromBase64(encrypted, key, "user-id-9999")
        );

        // Failure when AAD is omitted
        assertThrows(CryptoException.class, () ->
                AesGcmCrypto.decryptFromBase64(encrypted, key, null)
        );
    }

    @Test
    @DisplayName("Ciphertext shorter than minimum length must throw IllegalArgumentException")
    void testShortPayload() {
        SecretKey key = AesGcmCrypto.generateKey();
        byte[] shortPayload = new byte[27]; // Less than MIN_PAYLOAD_LENGTH_BYTES (28)
        assertThrows(IllegalArgumentException.class, () -> AesGcmCrypto.decrypt(shortPayload, key));
    }

    @Test
    @DisplayName("Key conversion between bytes, Base64 and SecretKey")
    void testKeyConversions() {
        SecretKey originalKey = AesGcmCrypto.generateKey();
        byte[] keyBytes = originalKey.getEncoded();
        assertEquals(32, keyBytes.length); // 256 bits

        SecretKey fromBytes = AesGcmCrypto.keyFromBytes(keyBytes);
        assertArrayEquals(keyBytes, fromBytes.getEncoded());

        String base64 = java.util.Base64.getEncoder().encodeToString(keyBytes);
        SecretKey fromBase64 = AesGcmCrypto.keyFromBase64(base64);
        assertArrayEquals(keyBytes, fromBase64.getEncoded());
    }
}
