package com.msp.bytepack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ByteBufferPackerTest {

    @Test
    @DisplayName("Big Endian binary round trip matches standard ByteBuffer")
    void testBigEndianRoundTrip() {
        ByteBufferPacker packer = ByteBufferPacker.allocate(ByteOrder.BIG_ENDIAN)
                .writeUInt8(255)
                .writeInt8(-12)
                .writeUInt16(65530)
                .writeInt16(-1234)
                .writeUInt32(4294967200L)
                .writeInt32(-987654)
                .writeInt64(0x1122334455667788L)
                .writeFloat(3.14159f)
                .writeDouble(2.718281828459045)
                .writeBoolean(true);

        byte[] bytes = packer.toByteArray();

        // Verify with java.nio.ByteBuffer
        ByteBuffer stdBuf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        assertEquals(255, stdBuf.get() & 0xFF);
        assertEquals(-12, stdBuf.get());
        assertEquals(65530, stdBuf.getShort() & 0xFFFF);
        assertEquals(-1234, stdBuf.getShort());
        assertEquals(4294967200L, stdBuf.getInt() & 0xFFFFFFFFL);
        assertEquals(-987654, stdBuf.getInt());
        assertEquals(0x1122334455667788L, stdBuf.getLong());
        assertEquals(3.14159f, stdBuf.getFloat());
        assertEquals(2.718281828459045, stdBuf.getDouble());
        assertEquals(1, stdBuf.get());

        // Unpack with ByteBufferUnpacker
        ByteBufferUnpacker unpacker = ByteBufferUnpacker.wrap(bytes, ByteOrder.BIG_ENDIAN);
        assertEquals(255, unpacker.readUInt8());
        assertEquals(-12, unpacker.readInt8());
        assertEquals(65530, unpacker.readUInt16());
        assertEquals(-1234, unpacker.readInt16());
        assertEquals(4294967200L, unpacker.readUInt32());
        assertEquals(-987654, unpacker.readInt32());
        assertEquals(0x1122334455667788L, unpacker.readInt64());
        assertEquals(3.14159f, unpacker.readFloat());
        assertEquals(2.718281828459045, unpacker.readDouble());
        assertTrue(unpacker.readBoolean());
        assertEquals(0, unpacker.remaining());
    }

    @Test
    @DisplayName("Little Endian binary round trip matches standard ByteBuffer")
    void testLittleEndianRoundTrip() {
        ByteBufferPacker packer = ByteBufferPacker.allocate(ByteOrder.LITTLE_ENDIAN)
                .writeUInt16(0x1234)
                .writeInt32(0x11223344)
                .writeInt64(0x0102030405060708L);

        byte[] bytes = packer.toByteArray();

        // Verify with java.nio.ByteBuffer
        ByteBuffer stdBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x1234, stdBuf.getShort() & 0xFFFF);
        assertEquals(0x11223344, stdBuf.getInt());
        assertEquals(0x0102030405060708L, stdBuf.getLong());

        // Unpack with ByteBufferUnpacker
        ByteBufferUnpacker unpacker = ByteBufferUnpacker.wrap(bytes, ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x1234, unpacker.readUInt16());
        assertEquals(0x11223344, unpacker.readInt32());
        assertEquals(0x0102030405060708L, unpacker.readInt64());
        assertEquals(0, unpacker.remaining());
    }

    @Test
    @DisplayName("VarInt and VarLong LEB128 encoding and decoding")
    void testVarIntAndVarLong() {
        ByteBufferPacker packer = new ByteBufferPacker();
        packer.writeVarInt(0);
        packer.writeVarInt(1);
        packer.writeVarInt(127);
        packer.writeVarInt(128);
        packer.writeVarInt(16384);
        packer.writeVarInt(Integer.MAX_VALUE);

        packer.writeVarLong(0L);
        packer.writeVarLong(128L);
        packer.writeVarLong(Long.MAX_VALUE);

        ByteBufferUnpacker unpacker = ByteBufferUnpacker.wrap(packer.toByteArray());
        assertEquals(0, unpacker.readVarInt());
        assertEquals(1, unpacker.readVarInt());
        assertEquals(127, unpacker.readVarInt());
        assertEquals(128, unpacker.readVarInt());
        assertEquals(16384, unpacker.readVarInt());
        assertEquals(Integer.MAX_VALUE, unpacker.readVarInt());

        assertEquals(0L, unpacker.readVarLong());
        assertEquals(128L, unpacker.readVarLong());
        assertEquals(Long.MAX_VALUE, unpacker.readVarLong());
    }

    @Test
    @DisplayName("ZigZag encoding handles negative integers and longs efficiently")
    void testZigZagEncoding() {
        ByteBufferPacker packer = new ByteBufferPacker();
        packer.writeZigZagInt(0);
        packer.writeZigZagInt(-1);
        packer.writeZigZagInt(1);
        packer.writeZigZagInt(-2147483648);
        packer.writeZigZagInt(2147483647);

        packer.writeZigZagLong(0L);
        packer.writeZigZagLong(-1L);
        packer.writeZigZagLong(-9223372036854775808L);
        packer.writeZigZagLong(9223372036854775807L);

        ByteBufferUnpacker unpacker = ByteBufferUnpacker.wrap(packer.toByteArray());
        assertEquals(0, unpacker.readZigZagInt());
        assertEquals(-1, unpacker.readZigZagInt());
        assertEquals(1, unpacker.readZigZagInt());
        assertEquals(-2147483648, unpacker.readZigZagInt());
        assertEquals(2147483647, unpacker.readZigZagInt());

        assertEquals(0L, unpacker.readZigZagLong());
        assertEquals(-1L, unpacker.readZigZagLong());
        assertEquals(-9223372036854775808L, unpacker.readZigZagLong());
        assertEquals(9223372036854775807L, unpacker.readZigZagLong());
    }

    @Test
    @DisplayName("String packing with VarInt prefix and fixed-length padding")
    void testStringPacking() {
        ByteBufferPacker packer = new ByteBufferPacker();
        packer.writeString("Hello World! 안녕하세요!");
        packer.writeFixedString("CODE", 8, StandardCharsets.UTF_8, ' ');
        packer.writeFixedString("LONG_TEXT_TRUNCATED", 4, StandardCharsets.UTF_8);

        ByteBufferUnpacker unpacker = ByteBufferUnpacker.wrap(packer.toByteArray());
        assertEquals("Hello World! 안녕하세요!", unpacker.readString());
        assertEquals("CODE", unpacker.readFixedString(8, StandardCharsets.UTF_8, ' '));
        assertEquals("LONG", unpacker.readFixedString(4, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Underflow exception when reading beyond buffer bounds")
    void testBufferUnderflow() {
        ByteBufferPacker packer = new ByteBufferPacker();
        packer.writeInt16(42);

        ByteBufferUnpacker unpacker = ByteBufferUnpacker.wrap(packer.toByteArray());
        assertEquals(42, unpacker.readInt16());

        assertThrows(ByteBufferException.class, unpacker::readInt8);
        assertThrows(ByteBufferException.class, () -> unpacker.readBytes(5));
    }

    @Test
    @DisplayName("Position control and skip operations")
    void testPositionAndSkip() {
        ByteBufferPacker packer = new ByteBufferPacker();
        packer.writeInt8(1);
        packer.writeInt8(2);
        packer.writeInt8(3);
        packer.writeInt8(4);

        ByteBufferUnpacker unpacker = ByteBufferUnpacker.wrap(packer.toByteArray());
        assertEquals(1, unpacker.readInt8());
        unpacker.skip(2); // skips 2 and 3
        assertEquals(4, unpacker.readInt8());

        unpacker.position(1);
        assertEquals(2, unpacker.readInt8());
    }
}
