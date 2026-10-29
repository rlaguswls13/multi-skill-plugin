package com.msp.bytepack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Binary packet parser with Big/Little Endian byte orders and VarInt/VarLong decoding.
 */
public final class ByteBufferUnpacker {

    private final byte[] buffer;
    private final int offset;
    private final int limit;
    private int position;
    private ByteOrder byteOrder;

    public ByteBufferUnpacker(byte[] data) {
        this(data, 0, (data != null) ? data.length : 0, ByteOrder.BIG_ENDIAN);
    }

    public ByteBufferUnpacker(byte[] data, ByteOrder byteOrder) {
        this(data, 0, (data != null) ? data.length : 0, byteOrder);
    }

    public ByteBufferUnpacker(byte[] data, int offset, int length, ByteOrder byteOrder) {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(byteOrder, "byteOrder must not be null");
        Objects.checkFromIndexSize(offset, length, data.length);
        this.buffer = data;
        this.offset = offset;
        this.position = offset;
        this.limit = offset + length;
        this.byteOrder = byteOrder;
    }

    public static ByteBufferUnpacker wrap(byte[] data) {
        return new ByteBufferUnpacker(data);
    }

    public static ByteBufferUnpacker wrap(byte[] data, ByteOrder byteOrder) {
        return new ByteBufferUnpacker(data, byteOrder);
    }

    public static ByteBufferUnpacker wrap(ByteBuffer buf) {
        Objects.requireNonNull(buf, "buf must not be null");
        byte[] arr;
        int off;
        int len = buf.remaining();
        if (buf.hasArray()) {
            arr = buf.array();
            off = buf.arrayOffset() + buf.position();
        } else {
            arr = new byte[len];
            int currentPos = buf.position();
            buf.get(arr);
            buf.position(currentPos);
            off = 0;
        }
        return new ByteBufferUnpacker(arr, off, len, buf.order());
    }

    public ByteOrder order() {
        return byteOrder;
    }

    public ByteBufferUnpacker order(ByteOrder byteOrder) {
        this.byteOrder = Objects.requireNonNull(byteOrder, "byteOrder must not be null");
        return this;
    }

    public int position() {
        return position - offset;
    }

    public ByteBufferUnpacker position(int newPosition) {
        if (newPosition < 0 || offset + newPosition > limit) {
            throw new IllegalArgumentException("Invalid position: " + newPosition + " (limit=" + (limit - offset) + ")");
        }
        this.position = offset + newPosition;
        return this;
    }

    public int remaining() {
        return limit - position;
    }

    public boolean hasRemaining() {
        return position < limit;
    }

    public ByteBufferUnpacker skip(int bytes) {
        checkRemaining(bytes);
        position += bytes;
        return this;
    }

    private void checkRemaining(int bytes) {
        if (remaining() < bytes) {
            throw new ByteBufferException(
                    "Buffer underflow: requested " + bytes + " bytes but only " + remaining() +
                            " remaining (position=" + (position - offset) + ", limit=" + (limit - offset) + ")"
            );
        }
    }

    public byte readInt8() {
        checkRemaining(1);
        return buffer[position++];
    }

    public int readUInt8() {
        return readInt8() & 0xFF;
    }

    public boolean readBoolean() {
        return readInt8() != 0;
    }

    public short readInt16() {
        checkRemaining(2);
        int b0 = buffer[position++] & 0xFF;
        int b1 = buffer[position++] & 0xFF;
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return (short) ((b0 << 8) | b1);
        } else {
            return (short) ((b1 << 8) | b0);
        }
    }

    public int readUInt16() {
        return readInt16() & 0xFFFF;
    }

    public int readInt32() {
        checkRemaining(4);
        int b0 = buffer[position++] & 0xFF;
        int b1 = buffer[position++] & 0xFF;
        int b2 = buffer[position++] & 0xFF;
        int b3 = buffer[position++] & 0xFF;
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
        } else {
            return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
        }
    }

    public long readUInt32() {
        return readInt32() & 0xFFFFFFFFL;
    }

    public long readInt64() {
        checkRemaining(8);
        long b0 = buffer[position++] & 0xFFL;
        long b1 = buffer[position++] & 0xFFL;
        long b2 = buffer[position++] & 0xFFL;
        long b3 = buffer[position++] & 0xFFL;
        long b4 = buffer[position++] & 0xFFL;
        long b5 = buffer[position++] & 0xFFL;
        long b6 = buffer[position++] & 0xFFL;
        long b7 = buffer[position++] & 0xFFL;
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return (b0 << 56) | (b1 << 48) | (b2 << 40) | (b3 << 32)
                    | (b4 << 24) | (b5 << 16) | (b6 << 8) | b7;
        } else {
            return (b7 << 56) | (b6 << 48) | (b5 << 40) | (b4 << 32)
                    | (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
        }
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt32());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readInt64());
    }

    public byte[] readBytes(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Length must not be negative: " + length);
        }
        checkRemaining(length);
        byte[] result = new byte[length];
        System.arraycopy(buffer, position, result, 0, length);
        position += length;
        return result;
    }

    /**
     * Reads a LEB128 unsigned VarInt.
     */
    public int readVarInt() {
        int result = 0;
        int shift = 0;
        while (shift < 35) {
            byte b = readInt8();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new ByteBufferException("Malformed VarInt: exceeds 32 bits");
    }

    /**
     * Reads a LEB128 unsigned VarLong.
     */
    public long readVarLong() {
        long result = 0;
        int shift = 0;
        while (shift < 70) {
            byte b = readInt8();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new ByteBufferException("Malformed VarLong: exceeds 64 bits");
    }

    /**
     * Reads a signed integer encoded with ZigZag VarInt.
     */
    public int readZigZagInt() {
        int n = readVarInt();
        return (n >>> 1) ^ -(n & 1);
    }

    /**
     * Reads a signed long encoded with ZigZag VarLong.
     */
    public long readZigZagLong() {
        long n = readVarLong();
        return (n >>> 1) ^ -(n & 1);
    }

    /**
     * Reads a UTF-8 string prefixed by its VarInt byte length.
     */
    public String readString() {
        return readString(StandardCharsets.UTF_8);
    }

    /**
     * Reads a string with specified charset prefixed by its VarInt byte length.
     */
    public String readString(Charset charset) {
        Objects.requireNonNull(charset, "charset must not be null");
        int length = readVarInt();
        if (length == 0) {
            return "";
        }
        byte[] bytes = readBytes(length);
        return new String(bytes, charset);
    }

    /**
     * Reads a fixed-length string, trimming trailing null characters or custom padding.
     */
    public String readFixedString(int length, Charset charset) {
        return readFixedString(length, charset, '\0');
    }

    public String readFixedString(int length, Charset charset, char padChar) {
        Objects.requireNonNull(charset, "charset must not be null");
        byte[] bytes = readBytes(length);
        String s = new String(bytes, charset);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == padChar) {
            end--;
        }
        return s.substring(0, end);
    }
}
