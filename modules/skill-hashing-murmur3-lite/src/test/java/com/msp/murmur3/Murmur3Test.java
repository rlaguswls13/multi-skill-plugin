package com.msp.murmur3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Murmur3Test {

    @Test
    @DisplayName("Murmur3 32-bit empty input produces 0 with default seed")
    void testHash32EmptyString() {
        assertEquals(0, Murmur3.hash32(""));
        assertEquals(0, Murmur3.hash32(new byte[0]));
    }

    @Test
    @DisplayName("Murmur3 32-bit deterministic hashing and seed variation")
    void testHash32DeterminismAndSeeds() {
        String input = "Hello, MurmurHash3!";
        int hash1 = Murmur3.hash32(input);
        int hash2 = Murmur3.hash32(input);
        assertEquals(hash1, hash2);

        int hashSeed1 = Murmur3.hash32(input, 42);
        int hashSeed2 = Murmur3.hash32(input, 1337);
        assertNotEquals(hash1, hashSeed1);
        assertNotEquals(hashSeed1, hashSeed2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 7, 8, 15, 16, 17, 31, 32, 63, 64, 100})
    @DisplayName("Murmur3 32-bit and 128-bit tail boundary handling for different byte lengths")
    void testTailBoundaries(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) ((i * 31 + 7) & 0xFF);
        }

        int h32 = Murmur3.hash32(data);
        Murmur128Result h128 = Murmur3.hash128(data);

        assertNotNull(h128);
        assertNotNull(h128.toHexString());
        assertEquals(32, h128.toHexString().length());

        // Sub-slice check
        byte[] padded = new byte[length + 8];
        System.arraycopy(data, 0, padded, 4, length);
        int h32Sub = Murmur3.hash32(padded, 4, length, 0);
        assertEquals(h32, h32Sub);

        Murmur128Result h128Sub = Murmur3.hash128(padded, 4, length, 0);
        assertEquals(h128.val1(), h128Sub.val1());
        assertEquals(h128.val2(), h128Sub.val2());
    }

    @Test
    @DisplayName("Murmur128Result formatting, toHexString, and byte arrays")
    void testMurmur128ResultMethods() {
        Murmur128Result result = new Murmur128Result(0x0123456789abcdefL, 0xfedcba9876543210L);
        String hexBigEndian = result.toHexString();
        assertEquals(32, hexBigEndian.length());
        assertEquals("0123456789abcdeffedcba9876543210", hexBigEndian);
        assertEquals(hexBigEndian, result.toString());

        byte[] bigEndianBytes = result.toByteArray();
        assertEquals(16, bigEndianBytes.length);
        assertEquals(0x01, bigEndianBytes[0]);
        assertEquals(0x10, bigEndianBytes[15]);
        assertArrayEquals(bigEndianBytes, result.asBytes());

        byte[] littleEndianBytes = result.toByteArray(true);
        assertEquals(16, littleEndianBytes.length);
        assertEquals((byte) 0xef, littleEndianBytes[0]);

        String hexLittleEndian = result.toHexString(true);
        assertEquals(32, hexLittleEndian.length());
        assertNotEquals(hexBigEndian, hexLittleEndian);
    }

    @Test
    @DisplayName("Avalanche effect: 1-bit input flip changes more than 25% of hash bits")
    void testAvalancheEffect() {
        byte[] input1 = "TestingMurmur3AvalancheEffect1".getBytes(StandardCharsets.UTF_8);
        byte[] input2 = "TestingMurmur3AvalancheEffect2".getBytes(StandardCharsets.UTF_8);

        int h32_1 = Murmur3.hash32(input1);
        int h32_2 = Murmur3.hash32(input2);
        int diffBits32 = Integer.bitCount(h32_1 ^ h32_2);
        assertTrue(diffBits32 >= 8, "Expected at least 8 bits difference in 32-bit hash, got " + diffBits32);

        Murmur128Result h128_1 = Murmur3.hash128(input1);
        Murmur128Result h128_2 = Murmur3.hash128(input2);
        int diffBits128 = Long.bitCount(h128_1.val1() ^ h128_2.val1()) + Long.bitCount(h128_1.val2() ^ h128_2.val2());
        assertTrue(diffBits128 >= 32, "Expected at least 32 bits difference in 128-bit hash, got " + diffBits128);
    }

    @Test
    @DisplayName("Collision resistance across 1000 incremental strings")
    void testCollisionResistance() {
        Set<Integer> hashes32 = new HashSet<>();
        Set<Murmur128Result> hashes128 = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            String val = "unique_item_key_" + i;
            hashes32.add(Murmur3.hash32(val));
            hashes128.add(Murmur3.hash128(val));
        }

        assertEquals(1000, hashes32.size());
        assertEquals(1000, hashes128.size());
    }

    @Test
    @DisplayName("Exception handling on null and invalid bounds")
    void testExceptions() {
        assertThrows(NullPointerException.class, () -> Murmur3.hash32((String) null));
        assertThrows(NullPointerException.class, () -> Murmur3.hash32((byte[]) null, 0, 0, 0));
        assertThrows(NullPointerException.class, () -> Murmur3.hash128((String) null));
        assertThrows(NullPointerException.class, () -> Murmur3.hash128((byte[]) null, 0, 0, 0));

        byte[] sample = new byte[10];
        assertThrows(IndexOutOfBoundsException.class, () -> Murmur3.hash32(sample, -1, 5, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> Murmur3.hash32(sample, 5, 10, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> Murmur3.hash128(sample, -1, 5, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> Murmur3.hash128(sample, 5, 10, 0));
    }
}
