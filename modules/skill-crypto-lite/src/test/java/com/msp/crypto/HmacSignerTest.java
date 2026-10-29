package com.msp.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HmacSignerTest {

    @Test
    @DisplayName("HMAC-SHA256 test against RFC 4231 Test Case 2 vector")
    void testRfc4231TestCase2() {
        // RFC 4231 Test Case 2
        // Key: "Jefe"
        // Data: "what do ya want for nothing?"
        // Digest: 5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843
        byte[] key = "Jefe".getBytes(StandardCharsets.US_ASCII);
        byte[] data = "what do ya want for nothing?".getBytes(StandardCharsets.US_ASCII);
        String expectedHex = "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843";

        String signatureHex = HmacSigner.signHex(data, key);
        assertEquals(expectedHex, signatureHex);

        assertTrue(HmacSigner.verify(data, HashUtil.hexToBytes(expectedHex), key));
        assertTrue(HmacSigner.verifyHex("what do ya want for nothing?", expectedHex, "Jefe"));
    }

    @Test
    @DisplayName("HMAC signature verification fails on tampered data or key")
    void testVerificationFailure() {
        String data = "orderId=12345&amount=50000";
        String key = "secret-api-key";

        String signature = HmacSigner.signHex(data, key);

        assertTrue(HmacSigner.verifyHex(data, signature, key));

        // Tampered data
        assertFalse(HmacSigner.verifyHex("orderId=12345&amount=100", signature, key));

        // Tampered key
        assertFalse(HmacSigner.verifyHex(data, signature, "wrong-key"));

        // Tampered signature
        String tamperedSig = signature.substring(0, signature.length() - 1) + "0";
        assertFalse(HmacSigner.verifyHex(data, tamperedSig, key));
    }

    @Test
    @DisplayName("Base64 and Base64Url signatures")
    void testBase64Signatures() {
        byte[] key = "secret".getBytes(StandardCharsets.UTF_8);
        byte[] data = "payload data".getBytes(StandardCharsets.UTF_8);

        String b64 = HmacSigner.signBase64(data, key);
        assertNotNull(b64);
        assertTrue(HmacSigner.verifyBase64("payload data", b64, "secret"));

        String b64Url = HmacSigner.signBase64Url(data, key);
        assertNotNull(b64Url);
        assertFalse(b64Url.contains("="));
    }
}
