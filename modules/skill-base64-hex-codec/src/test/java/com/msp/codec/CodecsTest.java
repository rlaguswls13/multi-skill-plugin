package com.msp.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Codecs Unit Tests")
class CodecsTest {

    @Test
    @DisplayName("Should encode and decode Hex according to RFC test vectors")
    void testHexVectors() {
        assertEquals("", Codecs.toHex("".getBytes(StandardCharsets.UTF_8)));
        assertEquals("66", Codecs.toHex("f".getBytes(StandardCharsets.UTF_8)));
        assertEquals("666f", Codecs.toHex("fo".getBytes(StandardCharsets.UTF_8)));
        assertEquals("666f6f", Codecs.toHex("foo".getBytes(StandardCharsets.UTF_8)));
        assertEquals("666f6f62", Codecs.toHex("foob".getBytes(StandardCharsets.UTF_8)));
        assertEquals("666f6f6261", Codecs.toHex("fooba".getBytes(StandardCharsets.UTF_8)));
        assertEquals("666f6f626172", Codecs.toHex("foobar".getBytes(StandardCharsets.UTF_8)));

        // Uppercase
        assertEquals("666F6F", Codecs.toHex("foo".getBytes(StandardCharsets.UTF_8), true));

        // Decoding
        assertArrayEquals("foobar".getBytes(StandardCharsets.UTF_8), Codecs.fromHex("666f6f626172"));
        assertArrayEquals("foobar".getBytes(StandardCharsets.UTF_8), Codecs.fromHex("666F6F626172"));
    }

    @Test
    @DisplayName("Should encode and decode Base64 according to RFC 4648 vectors")
    void testBase64Vectors() {
        assertEquals("", Codecs.toBase64("".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Zg==", Codecs.toBase64("f".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Zm8=", Codecs.toBase64("fo".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Zm9v", Codecs.toBase64("foo".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Zm9vYg==", Codecs.toBase64("foob".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Zm9vYmE=", Codecs.toBase64("fooba".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Zm9vYmFy", Codecs.toBase64("foobar".getBytes(StandardCharsets.UTF_8)));

        assertArrayEquals("foobar".getBytes(StandardCharsets.UTF_8), Codecs.fromBase64("Zm9vYmFy"));
    }

    @Test
    @DisplayName("Should encode and decode Base64URL without padding")
    void testBase64Url() {
        byte[] bytes = new byte[]{(byte) 0xFB, (byte) 0xFF, (byte) 0xFE};
        String urlSafe = Codecs.toBase64Url(bytes);
        assertFalse(urlSafe.contains("+"));
        assertFalse(urlSafe.contains("/"));
        assertFalse(urlSafe.contains("="));

        byte[] decoded = Codecs.fromBase64Url(urlSafe);
        assertArrayEquals(bytes, decoded);
    }

    @Test
    @DisplayName("Should encode and decode Base32 according to RFC 4648 vectors")
    void testBase32Rfc4648Vectors() {
        assertEquals("", Codecs.toBase32("".getBytes(StandardCharsets.UTF_8)));
        assertEquals("MY======", Codecs.toBase32("f".getBytes(StandardCharsets.UTF_8)));
        assertEquals("MZXQ====", Codecs.toBase32("fo".getBytes(StandardCharsets.UTF_8)));
        assertEquals("MZXW6===", Codecs.toBase32("foo".getBytes(StandardCharsets.UTF_8)));
        assertEquals("MZXW6YQ=", Codecs.toBase32("foob".getBytes(StandardCharsets.UTF_8)));
        assertEquals("MZXW6YTB", Codecs.toBase32("fooba".getBytes(StandardCharsets.UTF_8)));
        assertEquals("MZXW6YTBOI======", Codecs.toBase32("foobar".getBytes(StandardCharsets.UTF_8)));

        // Unpadded
        assertEquals("MZXW6", Codecs.toBase32("foo".getBytes(StandardCharsets.UTF_8), false));

        // Decoding with and without padding, case-insensitive
        assertArrayEquals("foobar".getBytes(StandardCharsets.UTF_8), Codecs.fromBase32("MZXW6YTBOI======"));
        assertArrayEquals("foobar".getBytes(StandardCharsets.UTF_8), Codecs.fromBase32("mzxw6ytboi"));
    }

    @Test
    @DisplayName("Should encode and decode Crockford Base32 with alias mapping")
    void testCrockfordBase32() {
        byte[] original = "Hello Crockford".getBytes(StandardCharsets.UTF_8);
        String encoded = Codecs.toCrockfordBase32(original);

        byte[] decoded = Codecs.fromCrockfordBase32(encoded);
        assertArrayEquals(original, decoded);

        // Test alias mapping (I/i/L/l -> 1, O/o -> 0) and hyphen tolerance
        byte[] b1 = Codecs.fromCrockfordBase32("0123");
        byte[] b2 = Codecs.fromCrockfordBase32("o-l-2-3");
        byte[] b3 = Codecs.fromCrockfordBase32("O-I-2-3");
        assertArrayEquals(b1, b2);
        assertArrayEquals(b1, b3);
    }

    @Test
    @DisplayName("Should correctly round-trip arbitrary random byte arrays")
    void testRandomRoundTrips() {
        Random rand = new Random(42);
        for (int length = 0; length < 100; length++) {
            byte[] bytes = new byte[length];
            rand.nextBytes(bytes);

            // Hex
            assertArrayEquals(bytes, Codecs.fromHex(Codecs.toHex(bytes)));
            // Base64
            assertArrayEquals(bytes, Codecs.fromBase64(Codecs.toBase64(bytes)));
            // Base64Url
            assertArrayEquals(bytes, Codecs.fromBase64Url(Codecs.toBase64Url(bytes)));
            // Base32 RFC
            assertArrayEquals(bytes, Codecs.fromBase32(Codecs.toBase32(bytes)));
            // Base32 Crockford
            assertArrayEquals(bytes, Codecs.fromCrockfordBase32(Codecs.toCrockfordBase32(bytes)));
        }
    }

    @Test
    @DisplayName("Should throw CodecException on malformed input")
    void testInvalidInputs() {
        assertThrows(CodecException.class, () -> Codecs.fromHex("123")); // odd length
        assertThrows(CodecException.class, () -> Codecs.fromHex("ZZ")); // non-hex
        assertThrows(CodecException.class, () -> Codecs.fromBase64("!@#$%"));
        assertThrows(CodecException.class, () -> Codecs.fromBase32("INVALID89")); // '8' and '9' are not RFC32
    }
}
