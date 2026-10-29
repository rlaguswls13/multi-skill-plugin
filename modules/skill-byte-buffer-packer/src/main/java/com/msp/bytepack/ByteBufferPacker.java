package com.msp.bytepack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Fluent binary packet builder supporting Big/Little Endian byte orders and VarInt/VarLong encoding.
 */
public final class ByteBufferPacker {

    private byte[] buffer;
    private int position;
    private ByteOrder byteOrder;

    public ByteBufferPacker() {
        this(64, ByteOrder.BIG_ENDIAN);
    }

    public ByteBufferPacker(int initialCapacity) {
        this(initialCapacity, ByteOrder.BIG_ENDIAN);
    }

    public ByteBufferPacker(ByteOrder byteOrder) {
        this(64, byteOrder);
    }

    public ByteBufferPacker(int initialCapacity, ByteOrder byteOrder) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity must not be negative: " + initialCapacity);
        }
        this.buffer = new byte[Math.max(16, initialCapacity)];
        this.position = 0;
        this.byteOrder = Objects.requireNonNull(byteOrder, "byteOrder must not be null");
    }

    public static ByteBufferPacker allocate() {
        return new ByteBufferPacker();
    }

    public static ByteBufferPacker allocate(int initialCapacity) {
        return new ByteBufferPacker(initialCapacity);
    }

    public static ByteBufferPacker allocate(ByteOrder byteOrder) {
        return new ByteBufferPacker(byteOrder);
    }

    public ByteOrder order() {
        return byteOrder;
    }

    public ByteBufferPacker order(ByteOrder byteOrder) {
        this.byteOrder = Objects.requireNonNull(byteOrder, "byteOrder must not be null");
        return this;
    }

    public int size() {
        return position;
    }

    public ByteBufferPacker clear() {
        position = 0;
        return this;
    }

    private void ensureCapacity(int additionalBytes) {
        int required = position + additionalBytes;
        if (required > buffer.length) {
            int newCap = Math.max(buffer.length * 2, required + 32);
            buffer = Arrays.copyOf(buffer, newCap);
        }
    }

    public ByteBufferPacker writeInt8(int value) {
        ensureCapacity(1);
        buffer[position++] = (byte) value;
        return this;
    }

    public ByteBufferPacker writeUInt8(int value) {
        return writeInt8(value & 0xFF);
    }

    public ByteBufferPacker writeBoolean(boolean value) {
        return writeInt8(value ? 1 : 0);
    }

    public ByteBufferPacker writeInt16(int value) {
        ensureCapacity(2);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            buffer[position++] = (byte) (value >>> 8);
            buffer[position++] = (byte) value;
        } else {
            buffer[position++] = (byte) value;
            buffer[position++] = (byte) (value >>> 8);
        }
        return this;
    }

    public ByteBufferPacker writeUInt16(int value) {
        return writeInt16(value & 0xFFFF);
    }

    public ByteBufferPacker writeInt32(int value) {
        ensureCapacity(4);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            buffer[position++] = (byte) (value >>> 24);
            buffer[position++] = (byte) (value >>> 16);
            buffer[position++] = (byte) (value >>> 8);
            buffer[position++] = (byte) value;
        } else {
            buffer[position++] = (byte) value;
            buffer[position++] = (byte) (value >>> 8);
            buffer[position++] = (byte) (value >>> 16);
            buffer[position++] = (byte) (value >>> 24);
        }
        return this;
    }

    public ByteBufferPacker writeUInt32(long value) {
        return writeInt32((int) (value & 0xFFFFFFFFL));
    }

    public ByteBufferPacker writeInt64(long value) {
        ensureCapacity(8);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            buffer[position++] = (byte) (value >>> 56);
            buffer[position++] = (byte) (value >>> 48);
            buffer[position++] = (byte) (value >>> 40);
            buffer[position++] = (byte) (value >>> 32);
            buffer[position++] = (byte) (value >>> 24);
            buffer[position++] = (byte) (value >>> 16);
            buffer[position++] = (byte) (value >>> 8);
            buffer[position++] = (byte) value;
        } else {
            buffer[position++] = (byte) value;
            buffer[position++] = (byte) (value >>> 8);
            buffer[position++] = (byte) (value >>> 16);
            buffer[position++] = (byte) (value >>> 24);
            buffer[position++] = (byte) (value >>> 32);
            buffer[position++] = (byte) (value >>> 40);
            buffer[position++] = (byte) (value >>> 48);
            buffer[position++] = (byte) (value >>> 56);
        }
        return this;
    }

    public ByteBufferPacker writeFloat(float value) {
        return writeInt32(Float.floatToIntBits(value));
    }

    public ByteBufferPacker writeDouble(double value) {
        return writeInt64(Double.doubleToLongBits(value));
    }

    public ByteBufferPacker writeBytes(byte[] bytes) {
        if (bytes == null) {
            return this;
        }
        return writeBytes(bytes, 0, bytes.length);
    }

    public ByteBufferPacker writeBytes(byte[] bytes, int offset, int length) {
        if (bytes == null || length == 0) {
            return this;
        }
        Objects.checkFromIndexSize(offset, length, bytes.length);
        ensureCapacity(length);
        System.arraycopy(bytes, offset, buffer, position, length);
        position += length;
        return this;
    }

    /**
     * Writes an integer using LEB128 unsigned VarInt encoding.
     */
    public ByteBufferPacker writeVarInt(int value) {
        while ((value & ~0x7F) != 0) {
            writeInt8((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        writeInt8(value & 0x7F);
        return this;
    }

    /**
     * Writes a long using LEB128 unsigned VarLong encoding.
     */
    public ByteBufferPacker writeVarLong(long value) {
        while ((value & ~0x7FL) != 0) {
            writeInt8(((int) value & 0x7F) | 0x80);
            value >>>= 7;
        }
        writeInt8((int) value & 0x7F);
        return this;
    }

    /**
     * Writes a signed integer using ZigZag encoding mapped into VarInt.
     */
    public ByteBufferPacker writeZigZagInt(int value) {
        return writeVarInt((value << 1) ^ (value >> 31));
    }

    /**
     * Writes a signed long using ZigZag encoding mapped into VarLong.
     */
    public ByteBufferPacker writeZigZagLong(long value) {
        return writeVarLong((value << 1) ^ (value >> 63));
    }

    /**
     * Writes a UTF-8 string prefixed with its byte length as a VarInt.
     */
    public ByteBufferPacker writeString(String value) {
        return writeString(value, StandardCharsets.UTF_8);
    }

    /**
     * Writes a string encoded with the specified Charset, prefixed by its byte length as a VarInt.
     */
    public ByteBufferPacker writeString(String value, Charset charset) {
        Objects.requireNonNull(charset, "charset must not be null");
        if (value == null) {
            return writeVarInt(0);
        }
        byte[] bytes = value.getBytes(charset);
        writeVarInt(bytes.length);
        writeBytes(bytes);
        return this;
    }

    /**
     * Writes a fixed-length string, truncating or padding with 0x00 to reach exactly targetLength bytes.
     */
    public ByteBufferPacker writeFixedString(String value, int length, Charset charset) {
        return writeFixedString(value, length, charset, '\0');
    }

    public ByteBufferPacker writeFixedString(String value, int length, Charset charset, char padChar) {
        Objects.requireNonNull(charset, "charset must not be null");
        if (length < 0) {
            throw new IllegalArgumentException("Length must not be negative: " + length);
        }
        byte[] bytes = (value == null) ? new byte[0] : value.getBytes(charset);
        if (bytes.length >= length) {
            writeBytes(bytes, 0, length);
        } else {
            writeBytes(bytes);
            byte padByte = (byte) padChar;
            for (int i = bytes.length; i < length; i++) {
                writeInt8(padByte);
            }
        }
        return this;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(buffer, position);
    }

    public ByteBuffer toByteBuffer() {
        ByteBuffer buf = ByteBuffer.wrap(toByteArray());
        buf.order(byteOrder);
        return buf;
    }
}
