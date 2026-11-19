package com.msp.jwelite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RFC 7516 JWE Lite Tests")
class JweTest {

    @Test
    @DisplayName("Encrypt and decrypt using RSA-OAEP-256 and A256GCM")
    void testRsaOaep256AndA256Gcm() {
        KeyPair keyPair = JweKeyUtils.generateRsaKeyPair(2048);
        String payload = "{\"sub\":\"user-42\",\"role\":\"admin\",\"iss\":\"auth-server\"}";

        JweToken token = JweToken.builder()
                .algorithm(JweAlgorithm.RSA_OAEP_256)
                .encryption(JweEncryption.A256GCM)
                .contentType("JWT")
                .keyId("key-2026-v1")
                .payload(payload)
                .encrypt(keyPair.getPublic());

        String compact = token.toCompact();
        assertNotNull(compact);
        assertEquals(5, compact.split("\\.", -1).length);

        // Parse and decrypt
        JweToken parsed = JweToken.parse(compact);
        assertEquals(JweAlgorithm.RSA_OAEP_256, parsed.getHeader().alg());
        assertEquals(JweEncryption.A256GCM, parsed.getHeader().enc());
        assertEquals("JWT", parsed.getHeader().cty());
        assertEquals("key-2026-v1", parsed.getHeader().kid());

        String decrypted = parsed.decryptToString(keyPair.getPrivate());
        assertEquals(payload, decrypted);
    }

    @Test
    @DisplayName("Encrypt and decrypt using RSA-OAEP (SHA-1) and A128GCM")
    void testRsaOaepAndA128Gcm() {
        KeyPair keyPair = JweKeyUtils.generateRsaKeyPair(2048);
        String secretMessage = "Top Secret Classified Payload 12345";

        JweToken token = JweToken.builder()
                .algorithm(JweAlgorithm.RSA_OAEP)
                .encryption(JweEncryption.A128GCM)
                .payload(secretMessage)
                .encrypt(keyPair.getPublic());

        String compact = token.toCompact();
        JweToken parsed = JweToken.parse(compact);
        String decrypted = parsed.decryptToString(keyPair.getPrivate());
        assertEquals(secretMessage, decrypted);
    }

    @Test
    @DisplayName("Direct key encryption ('dir') has empty encrypted key segment")
    void testDirectKeyEncryption() {
        SecretKey directKey = JweKeyUtils.generateAesKey(256);
        String payload = "Direct pre-shared AES key payload";

        JweToken token = JweToken.builder()
                .algorithm(JweAlgorithm.DIR)
                .encryption(JweEncryption.A256GCM)
                .payload(payload)
                .encrypt(directKey);

        String compact = token.toCompact();
        String[] parts = compact.split("\\.", -1);
        assertEquals(5, parts.length);
        assertEquals("", parts[1]); // JWE Encrypted Key must be empty for 'dir'

        JweToken parsed = JweToken.parse(compact);
        String decrypted = parsed.decryptToString(directKey);
        assertEquals(payload, decrypted);
    }

    @Test
    @DisplayName("Decryption with wrong RSA private key fails with JweException")
    void testWrongKeyFails() {
        KeyPair keyPair1 = JweKeyUtils.generateRsaKeyPair(2048);
        KeyPair keyPair2 = JweKeyUtils.generateRsaKeyPair(2048);

        JweToken token = JweToken.builder()
                .algorithm(JweAlgorithm.RSA_OAEP_256)
                .encryption(JweEncryption.A256GCM)
                .payload("Confidential message")
                .encrypt(keyPair1.getPublic());

        assertThrows(JweException.class, () -> token.decrypt(keyPair2.getPrivate()));
    }

    @Test
    @DisplayName("Tampered ciphertext or auth tag causes tag verification failure")
    void testTamperedCiphertextFails() {
        SecretKey directKey = JweKeyUtils.generateAesKey(256);

        JweToken token = JweToken.builder()
                .algorithm(JweAlgorithm.DIR)
                .encryption(JweEncryption.A256GCM)
                .payload("Sensitive financial transaction")
                .encrypt(directKey);

        String compact = token.toCompact();
        String[] parts = compact.split("\\.", -1);

        // Tamper with ciphertext by altering last byte
        byte[] cipherBytes = Base64.getUrlDecoder().decode(parts[3]);
        cipherBytes[cipherBytes.length - 1] ^= 0x01;
        String tamperedCipher = Base64.getUrlEncoder().withoutPadding().encodeToString(cipherBytes);

        String tamperedCompact = parts[0] + "." + parts[1] + "." + parts[2] + "." + tamperedCipher + "." + parts[4];
        JweToken tamperedToken = JweToken.parse(tamperedCompact);

        assertThrows(JweException.class, () -> tamperedToken.decrypt(directKey));
    }
}
