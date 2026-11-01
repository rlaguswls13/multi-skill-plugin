package com.msp.checksum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MultiCrcCalculator Tests")
class MultiCrcCalculatorTest {

    private static final byte[] TEST_VECTOR_123456789 = "123456789".getBytes(StandardCharsets.US_ASCII);

    @Test
    @DisplayName("Verify official test vector '123456789' for all 5 CRC algorithms")
    void testStandardVectors() {
        // 1. CRC16 MODBUS -> 0x4B37 (19255)
        int modbus = MultiCrcCalculator.compute16(CrcAlgorithm.CRC16_MODBUS, TEST_VECTOR_123456789);
        assertEquals(0x4B37, modbus);
        assertEquals("4B37", MultiCrcCalculator.toHexString16(modbus));

        // 2. CRC16 CCITT -> 0x29B1 (10673)
        int ccitt = MultiCrcCalculator.compute16(CrcAlgorithm.CRC16_CCITT, TEST_VECTOR_123456789);
        assertEquals(0x29B1, ccitt);
        assertEquals("29B1", MultiCrcCalculator.toHexString16(ccitt));

        // 3. CRC16 XMODEM -> 0x31C3 (12739)
        int xmodem = MultiCrcCalculator.compute16(CrcAlgorithm.CRC16_XMODEM, TEST_VECTOR_123456789);
        assertEquals(0x31C3, xmodem);
        assertEquals("31C3", MultiCrcCalculator.toHexString16(xmodem));

        // 4. CRC32 IEEE 802.3 -> 0xCBF43926 (3421780262)
        long ieee = MultiCrcCalculator.compute32(CrcAlgorithm.CRC32_IEEE, TEST_VECTOR_123456789);
        assertEquals(0xCBF43926L, ieee);
        assertEquals("CBF43926", MultiCrcCalculator.toHexString32(ieee));

        // 5. CRC32 CASTAGNOLI (CRC-32C) -> 0xE3069283 (3808859779)
        long castagnoli = MultiCrcCalculator.compute32(CrcAlgorithm.CRC32_CASTAGNOLI, TEST_VECTOR_123456789);
        assertEquals(0xE3069283L, castagnoli);
        assertEquals("E3069283", MultiCrcCalculator.toHexString32(castagnoli));
    }

    @Test
    @DisplayName("CRC32_IEEE matches standard JDK java.util.zip.CRC32 across multiple payloads")
    void testJdkCrc32Compatibility() {
        byte[] payload = "Antigravity Multi-Skill Plugin Java 21 Zero Dependency Test Suite".getBytes(StandardCharsets.UTF_8);

        CRC32 jdkCrc = new CRC32();
        jdkCrc.update(payload);
        long expected = jdkCrc.getValue();

        long actual = MultiCrcCalculator.compute32(CrcAlgorithm.CRC32_IEEE, payload);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Empty byte array checksums")
    void testEmptyByteArray() {
        byte[] empty = new byte[0];

        assertEquals(0xFFFF, MultiCrcCalculator.compute16(CrcAlgorithm.CRC16_MODBUS, empty));
        assertEquals(0xFFFF, MultiCrcCalculator.compute16(CrcAlgorithm.CRC16_CCITT, empty));
        assertEquals(0x0000, MultiCrcCalculator.compute16(CrcAlgorithm.CRC16_XMODEM, empty));
        assertEquals(0x00000000L, MultiCrcCalculator.compute32(CrcAlgorithm.CRC32_IEEE, empty));
        assertEquals(0x00000000L, MultiCrcCalculator.compute32(CrcAlgorithm.CRC32_CASTAGNOLI, empty));
    }

    @Test
    @DisplayName("Streaming update (byte-by-byte and chunks) matches one-shot compute")
    void testStreamingUpdateEquality() {
        byte[] data = "Streaming test string for all CRC algorithms verification".getBytes(StandardCharsets.UTF_8);

        for (CrcAlgorithm alg : CrcAlgorithm.values()) {
            CrcChecksum stream = MultiCrcCalculator.create(alg);

            // 1. One-shot
            long oneShotValue = alg.is16Bit()
                    ? MultiCrcCalculator.compute16(alg, data)
                    : MultiCrcCalculator.compute32(alg, data);

            // 2. Byte by byte
            for (byte b : data) {
                stream.update(b);
            }
            assertEquals(oneShotValue, stream.getValue(), "Mismatch in byte-by-byte for " + alg);

            // 3. Reset and test chunked update
            stream.reset();
            stream.update(data, 0, 10);
            stream.update(data, 10, 15);
            stream.update(data, 25, data.length - 25);
            assertEquals(oneShotValue, stream.getValue(), "Mismatch in chunked for " + alg);
        }
    }

    @Test
    @DisplayName("Formatting hex strings correctly with zero padding")
    void testHexFormatting() {
        assertEquals("0001", MultiCrcCalculator.toHexString16(1));
        assertEquals("FFFF", MultiCrcCalculator.toHexString16(0xFFFF));
        assertEquals("00000001", MultiCrcCalculator.toHexString32(1L));
        assertEquals("FFFFFFFF", MultiCrcCalculator.toHexString32(0xFFFFFFFFL));

        CrcChecksum checksum = MultiCrcCalculator.create(CrcAlgorithm.CRC16_MODBUS);
        checksum.update(TEST_VECTOR_123456789);
        assertEquals("4B37", checksum.toHexString());
    }

    @Test
    @DisplayName("Invalid algorithm or bounds throw IllegalArgumentException / IndexOutOfBoundsException")
    void testExceptions() {
        byte[] buf = new byte[10];

        // Mismatched algorithm bit width
        assertThrows(IllegalArgumentException.class, () ->
                MultiCrcCalculator.compute16(CrcAlgorithm.CRC32_IEEE, buf));
        assertThrows(IllegalArgumentException.class, () ->
                MultiCrcCalculator.compute32(CrcAlgorithm.CRC16_MODBUS, buf));

        // Null pointer
        assertThrows(NullPointerException.class, () ->
                MultiCrcCalculator.create(null));

        // Array slice bounds
        CrcChecksum crc = MultiCrcCalculator.create(CrcAlgorithm.CRC16_MODBUS);
        assertThrows(IndexOutOfBoundsException.class, () ->
                crc.update(buf, -1, 5));
        assertThrows(IndexOutOfBoundsException.class, () ->
                crc.update(buf, 0, 11));
    }
}
