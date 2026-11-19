package com.msp.zbase32;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ZBase32CodecTest {

    @Test
    void officialSpecVectorsForBytesAndBits() {
        ZBase32Codec codec = ZBase32Codec.strict();

        // Empty
        assertEquals("", codec.encode(new byte[0]));
        assertArrayEquals(new byte[0], codec.decode(""));

        // 1-bit vectors
        assertEquals("y", codec.encodeBits(new byte[]{(byte) 0x00}, 1));
        assertEquals("o", codec.encodeBits(new byte[]{(byte) 0x80}, 1));
        assertArrayEquals(new byte[]{(byte) 0x80}, codec.decodeBits("o", 1));

        // 8-bit vectors
        assertEquals("yy", codec.encode(new byte[]{0x00}));
        assertEquals("6y", codec.encode(new byte[]{(byte) 0xf0}));
        assertEquals("6r", codec.encode(new byte[]{(byte) 0xf1}));

        // 10-bit vector: 11111_11111 -> "99"
        assertEquals("99", codec.encodeBits(new byte[]{(byte) 0xff, (byte) 0xc0}, 10));
        assertArrayEquals(new byte[]{(byte) 0xff, (byte) 0xc0}, codec.decodeBits("99", 10));

        // 24-bit official z-base-32 vector: 0xd4, 0x7a, 0x04 -> "4t7ye"
        byte[] vec24 = new byte[]{(byte) 0xd4, 0x7a, 0x04};
        assertEquals("4t7ye", codec.encode(vec24));
        assertArrayEquals(vec24, codec.decode("4t7ye"));

        // 40-bit (5-byte) exact alignment
        byte[] hello = "hello".getBytes(StandardCharsets.UTF_8);
        String encodedHello = codec.encode(hello);
        assertEquals(8, encodedHello.length());
        assertArrayEquals(hello, codec.decode(encodedHello));
    }

    @Test
    void caseInsensitiveDecoding() {
        byte[] expected = new byte[]{(byte) 0xd4, 0x7a, 0x04};
        assertArrayEquals(expected, ZBase32Codec.decodeBytes("4T7YE"));
        assertArrayEquals(expected, ZBase32Codec.decodeBytes("4t7Ye"));
    }

    @Test
    void roundTripAllLengths0To64AndRandomPayloads() {
        Random rnd = new Random(20261119L);
        ZBase32Codec codec = ZBase32Codec.strict();

        for (int len = 0; len <= 64; len++) {
            byte[] payload = new byte[len];
            rnd.nextBytes(payload);
            String encoded = codec.encode(payload);
            assertTrue(codec.isValid(encoded), "Encoded output must be valid for length " + len);
            byte[] decoded = codec.decode(encoded);
            assertArrayEquals(payload, decoded, "Round-trip failed for length " + len);
        }
    }

    @Test
    void arbitraryBitLengthRoundTrip() {
        byte[] src = new byte[]{(byte) 0b10110101, (byte) 0b11001010, (byte) 0b01110011};
        ZBase32Codec codec = ZBase32Codec.strict();

        for (int bits = 0; bits <= 24; bits++) {
            String encoded = codec.encodeBits(src, bits);
            byte[] decoded = codec.decodeBits(encoded, bits);
            // Verify bit-for-bit equality for [0, bits)
            for (int b = 0; b < bits; b++) {
                int expectedBit = (src[b >>> 3] >>> (7 - (b & 7))) & 1;
                int actualBit = (decoded[b >>> 3] >>> (7 - (b & 7))) & 1;
                assertEquals(expectedBit, actualBit, "Bit mismatch at bit=" + b + " for bitCount=" + bits);
            }
        }
    }

    @Test
    void strictValidationRejectsInvalidCharsLengthsAndNonZeroPadding() {
        ZBase32Codec strict = ZBase32Codec.strict();

        // Characters excluded from z-base-32: 0, 2, l, v, space, '='
        assertThrows(ZBase32FormatException.class, () -> strict.decode("00"));
        assertThrows(ZBase32FormatException.class, () -> strict.decode("22"));
        assertThrows(ZBase32FormatException.class, () -> strict.decode("ll"));
        assertThrows(ZBase32FormatException.class, () -> strict.decode("vv"));
        assertThrows(ZBase32FormatException.class, () -> strict.decode("yy="));
        assertFalse(strict.isValid("invalid!"));

        // Illegal byte-aligned lengths (1, 3, 6)
        assertThrows(ZBase32FormatException.class, () -> strict.decode("y"));
        assertThrows(ZBase32FormatException.class, () -> strict.decode("yyy"));
        assertThrows(ZBase32FormatException.class, () -> strict.decode("yyyyyy"));

        // Non-zero trailing padding bits: 2 chars = 10 bits -> 8 data bits + 2 padding bits
        // 'b' = 00001 -> last bit is 1 (non-zero padding bit)
        assertThrows(ZBase32FormatException.class, () -> strict.decode("yb"));
        assertFalse(strict.isValid("yb"));

        // Lenient mode allows non-zero trailing padding bits and ignores them
        assertArrayEquals(new byte[]{0x00}, ZBase32Codec.lenient().decode("yb"));
    }
}
