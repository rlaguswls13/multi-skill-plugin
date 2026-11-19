package com.msp.varint;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class VarIntLeb128CodecTest {

    private static final long[] TEST_VALUES = {
            0L, 1L, -1L, 2L, -2L,
            63L, 64L, -64L, -65L,
            127L, 128L, -128L, -129L,
            255L, 256L, 300L, -300L,
            16383L, 16384L, -16384L, -16385L,
            Integer.MAX_VALUE, Integer.MIN_VALUE,
            Long.MAX_VALUE, Long.MIN_VALUE
    };

    @Test
    void unsignedVarIntRoundTripAndExactSize() throws Exception {
        // Protobuf spec test vector: 300 -> 0xAC 0x02
        byte[] encoded300 = VarIntLeb128Codec.writeUnsignedVarInt(300L);
        assertArrayEquals(new byte[]{(byte) 0xAC, 0x02}, encoded300);
        assertEquals(2, VarIntLeb128Codec.computeUnsignedVarIntSize(300L));
        assertEquals(300L, VarIntLeb128Codec.readUnsignedVarInt(encoded300));

        // 0 -> 1 byte [0x00]
        assertEquals(1, VarIntLeb128Codec.computeUnsignedVarIntSize(0L));
        assertArrayEquals(new byte[]{0x00}, VarIntLeb128Codec.writeUnsignedVarInt(0L));

        // 127 -> 1 byte [0x7F]
        assertEquals(1, VarIntLeb128Codec.computeUnsignedVarIntSize(127L));
        // 128 -> 2 bytes [0x80, 0x01]
        assertEquals(2, VarIntLeb128Codec.computeUnsignedVarIntSize(128L));
        // -1L (all 64 bits set) -> 10 bytes
        assertEquals(10, VarIntLeb128Codec.computeUnsignedVarIntSize(-1L));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(512);

        for (long v : TEST_VALUES) {
            byte[] arr = VarIntLeb128Codec.writeUnsignedVarInt(v);
            assertEquals(VarIntLeb128Codec.computeUnsignedVarIntSize(v), arr.length);
            assertEquals(v, VarIntLeb128Codec.readUnsignedVarInt(arr));
            assertEquals(VarIntLeb128Codec. writeUnsignedVarInt(v, buffer), arr.length);
            assertEquals(VarIntLeb128Codec.writeUnsignedVarInt(v, baos), arr.length);
        }

        buffer.flip();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        for (long v : TEST_VALUES) {
            assertEquals(v, VarIntLeb128Codec.readUnsignedVarInt(buffer));
            assertEquals(v, VarIntLeb128Codec.readUnsignedVarInt(bais));
        }
    }

    @Test
    void zigZagVarIntMapsSmallNegativeNumbersToSingleByte() throws Exception {
        // Verify standard Protobuf ZigZag mapping: 0 -> 0, -1 -> 1, 1 -> 2, -2 -> 3
        assertEquals(0L, VarIntLeb128Codec.encodeZigZag64(0L));
        assertEquals(1L, VarIntLeb128Codec.encodeZigZag64(-1L));
        assertEquals(2L, VarIntLeb128Codec.encodeZigZag64(1L));
        assertEquals(3L, VarIntLeb128Codec.encodeZigZag64(-2L));

        // -1 and -64 fit in a single byte with ZigZag!
        byte[] minusOne = VarIntLeb128Codec.writeZigZagVarInt(-1L);
        assertEquals(1, minusOne.length);
        assertEquals(-1L, VarIntLeb128Codec.readZigZagVarInt(minusOne));

        byte[] minus64 = VarIntLeb128Codec.writeZigZagVarInt(-64L);
        assertEquals(1, minus64.length);
        assertEquals(-64L, VarIntLeb128Codec.readZigZagVarInt(minus64));

        ByteBuffer buffer = ByteBuffer.allocate(512);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        for (long v : TEST_VALUES) {
            byte[] encoded = VarIntLeb128Codec.writeZigZagVarInt(v);
            assertEquals(VarIntLeb128Codec.computeZigZagVarIntSize(v), encoded.length);
            assertEquals(v, VarIntLeb128Codec.readZigZagVarInt(encoded));
            VarIntLeb128Codec.writeZigZagVarInt(v, buffer);
            VarIntLeb128Codec.writeZigZagVarInt(v, baos);
        }

        buffer.flip();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        for (long v : TEST_VALUES) {
            assertEquals(v, VarIntLeb128Codec.readZigZagVarInt(buffer));
            assertEquals(v, VarIntLeb128Codec.readZigZagVarInt(bais));
        }
    }

    @Test
    void signedLeb128DwarfAndWasmVectorsAndRoundTrip() throws Exception {
        // DWARF / Wikipedia Signed LEB128 canonical example: -123456 -> 0xC0 0xBB 0x78
        byte[] minus123456 = VarIntLeb128Codec.writeSignedLeb128(-123456L);
        assertArrayEquals(new byte[]{(byte) 0xC0, (byte) 0xBB, 0x78}, minus123456);
        assertEquals(-123456L, VarIntLeb128Codec.readSignedLeb128(minus123456));

        // -1 in Signed LEB128 is a single byte 0x7F
        byte[] minusOne = VarIntLeb128Codec.writeSignedLeb128(-1L);
        assertArrayEquals(new byte[]{0x7F}, minusOne);
        assertEquals(-1L, VarIntLeb128Codec.readSignedLeb128(minusOne));

        // 64 has bit 6 (0x40) set, so Signed LEB128 requires 2 bytes [0xC0, 0x00] to avoid sign extension
        byte[] plus64 = VarIntLeb128Codec.writeSignedLeb128(64L);
        assertArrayEquals(new byte[]{(byte) 0xC0, 0x00}, plus64);
        assertEquals(64L, VarIntLeb128Codec.readSignedLeb128(plus64));

        ByteBuffer buffer = ByteBuffer.allocate(512);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        for (long v : TEST_VALUES) {
            byte[] encoded = VarIntLeb128Codec.writeSignedLeb128(v);
            assertEquals(VarIntLeb128Codec.computeSignedLeb128Size(v), encoded.length);
            assertEquals(v, VarIntLeb128Codec.readSignedLeb128(encoded));
            VarIntLeb128Codec.writeSignedLeb128(v, buffer);
            VarIntLeb128Codec.writeSignedLeb128(v, baos);
        }

        buffer.flip();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        for (long v : TEST_VALUES) {
            assertEquals(v, VarIntLeb128Codec.readSignedLeb128(buffer));
            assertEquals(v, VarIntLeb128Codec.readSignedLeb128(bais));
        }
    }

    @Test
    void detectsTruncatedAndOverflowMalformedInputs() {
        // Truncated byte array (MSB continuation bit set on last byte)
        byte[] truncated = new byte[]{(byte) 0x80, (byte) 0x81};
        assertThrows(IllegalArgumentException.class, () -> VarIntLeb128Codec.readUnsignedVarInt(truncated));
        assertThrows(IllegalArgumentException.class, () -> VarIntLeb128Codec.readSignedLeb128(truncated));
        assertThrows(EOFException.class, () -> VarIntLeb128Codec.readUnsignedVarInt(new ByteArrayInputStream(truncated)));
        assertThrows(EOFException.class, () -> VarIntLeb128Codec.readSignedLeb128(new ByteArrayInputStream(truncated)));

        // 11-byte overflow sequence
        byte[] elevenBytes = new byte[]{
                (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01
        };
        assertThrows(IllegalArgumentException.class, () -> VarIntLeb128Codec.readUnsignedVarInt(elevenBytes));
        assertThrows(IllegalArgumentException.class, () -> VarIntLeb128Codec.readSignedLeb128(elevenBytes));
    }
}
