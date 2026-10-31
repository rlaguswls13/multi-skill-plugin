package com.msp.base58;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Base58 and Base58Check Codec Tests")
class Base58CodecTest {

    @Test
    @DisplayName("Bitcoin standard test vector: 'hello world' <-> 'StV1DL6CwTryKyV'")
    void testStandardBitcoinVector() {
        byte[] input = "hello world".getBytes(StandardCharsets.UTF_8);
        String encoded = Base58Codec.encode(input);
        assertEquals("StV1DL6CwTryKyV", encoded);

        byte[] decoded = Base58Codec.decode(encoded);
        assertArrayEquals(input, decoded);
    }

    @Test
    @DisplayName("Empty byte array encodes to empty string and vice versa")
    void testEmptyInput() {
        assertEquals("", Base58Codec.encode(new byte[0]));
        assertArrayEquals(new byte[0], Base58Codec.decode(""));
    }

    @Test
    @DisplayName("Leading zero bytes are preserved as leading '1' characters")
    void testLeadingZeros() {
        // Single zero byte
        byte[] oneZero = new byte[]{0x00};
        assertEquals("1", Base58Codec.encode(oneZero));
        assertArrayEquals(oneZero, Base58Codec.decode("1"));

        // Two zero bytes
        byte[] twoZeros = new byte[]{0x00, 0x00};
        assertEquals("11", Base58Codec.encode(twoZeros));
        assertArrayEquals(twoZeros, Base58Codec.decode("11"));

        // Two zeros followed by 1
        byte[] twoZerosAndOne = new byte[]{0x00, 0x00, 0x01};
        assertEquals("112", Base58Codec.encode(twoZerosAndOne));
        assertArrayEquals(twoZerosAndOne, Base58Codec.decode("112"));

        // Leading zeros followed by arbitrary data
        byte[] withZeros = new byte[]{0x00, 0x00, 0x61, 0x62, 0x63};
        String enc = Base58Codec.encode(withZeros);
        assertTrue(enc.startsWith("11"));
        assertArrayEquals(withZeros, Base58Codec.decode(enc));
    }

    @Test
    @DisplayName("Random binary data round-trip encoding and decoding")
    void testRandomRoundTrip() {
        Random random = new Random(42);
        for (int i = 0; i < 50; i++) {
            byte[] data = new byte[random.nextInt(256) + 1];
            random.nextBytes(data);

            String encoded = Base58Codec.encode(data);
            byte[] decoded = Base58Codec.decode(encoded);

            assertArrayEquals(data, decoded, "Failed at iteration " + i);
        }
    }

    @Test
    @DisplayName("Base58Check encode and decode roundtrip with version and payload")
    void testBase58CheckRoundTrip() {
        int version = 0x00; // Bitcoin P2PKH mainnet
        byte[] payload = "2026-crypto-payload-secure".getBytes(StandardCharsets.UTF_8);

        String checkedStr = Base58Codec.encodeChecked(version, payload);
        assertNotNull(checkedStr);
        assertFalse(checkedStr.isEmpty());

        byte[] decodedPayload = Base58Codec.decodeChecked(checkedStr);
        assertArrayEquals(payload, decodedPayload);

        Base58CheckPayload fullResult = Base58Codec.decodeCheckedWithVersion(checkedStr);
        assertEquals(version, fullResult.version());
        assertArrayEquals(payload, fullResult.payload());
    }

    @Test
    @DisplayName("Base58Check with custom version number (e.g., 111 for testnet)")
    void testBase58CheckWithCustomVersion() {
        int version = 111;
        byte[] payload = new byte[]{10, 20, 30, 40, 50};

        String encoded = Base58Codec.encodeChecked(version, payload);
        Base58CheckPayload result = Base58Codec.decodeCheckedWithVersion(encoded);

        assertEquals(111, result.version());
        assertArrayEquals(payload, result.payload());
    }

    @Test
    @DisplayName("Base58Check detects tampered checksum")
    void testTamperedChecksumDetection() {
        int version = 0;
        byte[] payload = "valid-transaction-data".getBytes(StandardCharsets.UTF_8);
        String encoded = Base58Codec.encodeChecked(version, payload);

        // Tamper with last character
        char lastChar = encoded.charAt(encoded.length() - 1);
        char tamperedChar = (lastChar == 'z') ? 'y' : 'z';
        String tampered = encoded.substring(0, encoded.length() - 1) + tamperedChar;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                Base58Codec.decodeChecked(tampered));
        assertTrue(ex.getMessage().contains("checksum") || ex.getMessage().contains("Invalid"));
    }

    @Test
    @DisplayName("Base58Check detects truncated input")
    void testBase58CheckTruncated() {
        // "1" decodes to 1 byte, which is less than the required 5 bytes for Base58Check
        assertThrows(IllegalArgumentException.class, () -> Base58Codec.decodeChecked("1"));
    }

    @Test
    @DisplayName("Invalid Base58 characters (0, O, I, l, symbols) throw IllegalArgumentException")
    void testInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> Base58Codec.decode("0invalid"));
        assertThrows(IllegalArgumentException.class, () -> Base58Codec.decode("Oinvalid"));
        assertThrows(IllegalArgumentException.class, () -> Base58Codec.decode("Iinvalid"));
        assertThrows(IllegalArgumentException.class, () -> Base58Codec.decode("linvalid"));
        assertThrows(IllegalArgumentException.class, () -> Base58Codec.decode("hello@world"));
        assertThrows(IllegalArgumentException.class, () -> Base58Codec.decode("hello world")); // space
    }

    @Test
    @DisplayName("Null arguments validation")
    void testNullValidation() {
        assertThrows(IllegalArgumentException.class, () -> Base58Codec.encode(null));
        assertThrows(IllegalArgumentException.class, () -> Base58Codec.decode(null));
        assertThrows(NullPointerException.class, () -> Base58Codec.encodeChecked(0, null));
        assertThrows(IllegalArgumentException.class, () -> Base58Codec.encodeChecked(-1, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> Base58Codec.encodeChecked(256, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> Base58Codec.decodeChecked(null));
    }
}
