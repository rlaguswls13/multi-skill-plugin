package com.msp.varint;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Protocol Buffers / WebAssembly Standard VarInt, Unsigned LEB128, Signed LEB128,
 * and ZigZag Variable-Length Integer Codec in pure Java 21.
 * <p>
 * Supports:
 * <ul>
 *     <li>Unsigned VarInt / ULEB128 (7 bits per byte with MSB {@code 0x80} continuation bit, up to 10 bytes for 64-bit)</li>
 *     <li>ZigZag VarInt ({@code (n << 1) ^ (n >> 63)} mapping signed negative integers to small unsigned integers)</li>
 *     <li>Signed LEB128 (DWARF / WebAssembly two's complement sign-extended LEB128)</li>
 *     <li>Zero-allocation size computation ({@link #computeUnsignedVarIntSize(long)}, {@link #computeZigZagVarIntSize(long)}, {@link #computeSignedLeb128Size(long)})</li>
 *     <li>Encoding/decoding across {@code byte[]}, {@link ByteBuffer}, and {@link InputStream}/{@link OutputStream}</li>
 * </ul>
 */
public final class VarIntLeb128Codec {

    /**
     * Maximum number of bytes required to encode a 64-bit integer in VarInt / LEB128 format.
     */
    public static final int MAX_VARINT64_BYTES = 10;

    private VarIntLeb128Codec() {
    }

    // =========================================================================
    // Exact Byte Length Computations
    // =========================================================================

    /**
     * Computes the exact number of bytes (1..10) required to encode a 64-bit value as an unsigned VarInt / ULEB128.
     * Negative values are treated as 64-bit unsigned integers (requiring 10 bytes).
     */
    public static int computeUnsignedVarIntSize(long value) {
        int leadingZeros = Long.numberOfLeadingZeros(value);
        int relevantBits = 64 - leadingZeros;
        if (relevantBits == 0) {
            return 1;
        }
        return (relevantBits + 6) / 7;
    }

    /**
     * Computes the exact number of bytes (1..10) required to encode a signed 64-bit value using ZigZag VarInt encoding.
     */
    public static int computeZigZagVarIntSize(long signedValue) {
        return computeUnsignedVarIntSize(encodeZigZag64(signedValue));
    }

    /**
     * Computes the exact number of bytes (1..10) required to encode a signed 64-bit value using two's complement Signed LEB128.
     */
    public static int computeSignedLeb128Size(long value) {
        int count = 0;
        long current = value;
        boolean more = true;
        while (more) {
            byte b = (byte) (current & 0x7FL);
            current >>= 7;
            boolean signBitClear = (b & 0x40) == 0;
            if ((current == 0 && signBitClear) || (current == -1 && !signBitClear)) {
                more = false;
            }
            count++;
        }
        return count;
    }

    // =========================================================================
    // ZigZag 64-bit Bijection Helpers
    // =========================================================================

    /**
     * Encodes a signed 64-bit integer into an unsigned ZigZag integer: {@code (n << 1) ^ (n >> 63)}.
     * Maps {@code 0 -> 0, -1 -> 1, 1 -> 2, -2 -> 3, 2 -> 4, ...}
     */
    public static long encodeZigZag64(long signedValue) {
        return (signedValue << 1) ^ (signedValue >> 63);
    }

    /**
     * Decodes an unsigned ZigZag integer back into a signed 64-bit integer: {@code (u >>> 1) ^ -(u & 1)}.
     */
    public static long decodeZigZag64(long rawUnsigned) {
        return (rawUnsigned >>> 1) ^ -(rawUnsigned & 1L);
    }

    // =========================================================================
    // 1. Unsigned VarInt / ULEB128
    // =========================================================================

    /**
     * Encodes a 64-bit value (treated as unsigned 64-bit) into a newly allocated byte array of exact length (1..10 bytes).
     */
    public static byte[] writeUnsignedVarInt(long value) {
        int size = computeUnsignedVarIntSize(value);
        byte[] out = new byte[size];
        long current = value;
        int idx = 0;
        while ((current & ~0x7FL) != 0L) {
            out[idx++] = (byte) (((int) current & 0x7F) | 0x80);
            current >>>= 7;
        }
        out[idx] = (byte) ((int) current & 0x7F);
        return out;
    }

    /**
     * Writes an unsigned 64-bit VarInt into the given {@link ByteBuffer} and returns the number of bytes written.
     */
    public static int writeUnsignedVarInt(long value, ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer must not be null");
        long current = value;
        int written = 0;
        while ((current & ~0x7FL) != 0L) {
            buffer.put((byte) (((int) current & 0x7F) | 0x80));
            current >>>= 7;
            written++;
        }
        buffer.put((byte) ((int) current & 0x7F));
        return written + 1;
    }

    /**
     * Writes an unsigned 64-bit VarInt into the given {@link OutputStream} and returns the number of bytes written.
     */
    public static int writeUnsignedVarInt(long value, OutputStream out) throws IOException {
        Objects.requireNonNull(out, "out must not be null");
        byte[] bytes = writeUnsignedVarInt(value);
        out.write(bytes);
        return bytes.length;
    }

    /**
     * Reads an unsigned 64-bit VarInt from the beginning of {@code bytes} (offset 0).
     */
    public static long readUnsignedVarInt(byte[] bytes) {
        return readUnsignedVarInt(bytes, 0);
    }

    /**
     * Reads an unsigned 64-bit VarInt from {@code bytes} starting at {@code offset}.
     */
    public static long readUnsignedVarInt(byte[] bytes, int offset) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        if (offset < 0 || offset >= bytes.length) {
            throw new IllegalArgumentException("Invalid offset " + offset + " for byte array of length " + bytes.length);
        }
        long result = 0L;
        int shift = 0;
        int idx = offset;
        while (shift < 64) {
            if (idx >= bytes.length) {
                throw new IllegalArgumentException("Truncated Unsigned VarInt: unexpected end of byte array");
            }
            byte b = bytes[idx++];
            long payload = b & 0x7FL;
            if (shift == 63 && payload > 1L) {
                throw new IllegalArgumentException("Unsigned VarInt overflow: 10th byte has bits beyond 64-bit range");
            }
            result |= (payload << shift);
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new IllegalArgumentException("Malformed Unsigned VarInt: exceeds maximum 10 bytes (64 bits)");
    }

    /**
     * Reads an unsigned 64-bit VarInt from the current position of {@link ByteBuffer}.
     */
    public static long readUnsignedVarInt(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer must not be null");
        long result = 0L;
        int shift = 0;
        while (shift < 64) {
            if (!buffer.hasRemaining()) {
                throw new IllegalArgumentException("Truncated Unsigned VarInt: ByteBuffer underflow", new BufferUnderflowException());
            }
            byte b = buffer.get();
            long payload = b & 0x7FL;
            if (shift == 63 && payload > 1L) {
                throw new IllegalArgumentException("Unsigned VarInt overflow: 10th byte has bits beyond 64-bit range");
            }
            result |= (payload << shift);
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new IllegalArgumentException("Malformed Unsigned VarInt: exceeds maximum 10 bytes (64 bits)");
    }

    /**
     * Reads an unsigned 64-bit VarInt from {@link InputStream}.
     */
    public static long readUnsignedVarInt(InputStream in) throws IOException {
        Objects.requireNonNull(in, "in must not be null");
        long result = 0L;
        int shift = 0;
        while (shift < 64) {
            int read = in.read();
            if (read == -1) {
                throw new EOFException("Truncated Unsigned VarInt: unexpected EOF");
            }
            long payload = read & 0x7FL;
            if (shift == 63 && payload > 1L) {
                throw new IllegalArgumentException("Unsigned VarInt overflow: 10th byte has bits beyond 64-bit range");
            }
            result |= (payload << shift);
            if ((read & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new IllegalArgumentException("Malformed Unsigned VarInt: exceeds maximum 10 bytes (64 bits)");
    }

    // =========================================================================
    // 2. ZigZag VarInt (Protocol Buffers sint64)
    // =========================================================================

    /**
     * Encodes a signed 64-bit integer using ZigZag + Unsigned VarInt encoding.
     */
    public static byte[] writeZigZagVarInt(long signedValue) {
        return writeUnsignedVarInt(encodeZigZag64(signedValue));
    }

    /**
     * Writes a signed 64-bit integer using ZigZag VarInt encoding into {@link ByteBuffer}.
     */
    public static int writeZigZagVarInt(long signedValue, ByteBuffer buffer) {
        return writeUnsignedVarInt(encodeZigZag64(signedValue), buffer);
    }

    /**
     * Writes a signed 64-bit integer using ZigZag VarInt encoding into {@link OutputStream}.
     */
    public static int writeZigZagVarInt(long signedValue, OutputStream out) throws IOException {
        return writeUnsignedVarInt(encodeZigZag64(signedValue), out);
    }

    /**
     * Reads a ZigZag-encoded signed 64-bit VarInt from {@code bytes} at offset 0.
     */
    public static long readZigZagVarInt(byte[] bytes) {
        return decodeZigZag64(readUnsignedVarInt(bytes, 0));
    }

    /**
     * Reads a ZigZag-encoded signed 64-bit VarInt from {@code bytes} at {@code offset}.
     */
    public static long readZigZagVarInt(byte[] bytes, int offset) {
        return decodeZigZag64(readUnsignedVarInt(bytes, offset));
    }

    /**
     * Reads a ZigZag-encoded signed 64-bit VarInt from {@link ByteBuffer}.
     */
    public static long readZigZagVarInt(ByteBuffer buffer) {
        return decodeZigZag64(readUnsignedVarInt(buffer));
    }

    /**
     * Reads a ZigZag-encoded signed 64-bit VarInt from {@link InputStream}.
     */
    public static long readZigZagVarInt(InputStream in) throws IOException {
        return decodeZigZag64(readUnsignedVarInt(in));
    }

    // =========================================================================
    // 3. Signed LEB128 (DWARF / WebAssembly s64)
    // =========================================================================

    /**
     * Encodes a signed 64-bit integer using standard DWARF / WebAssembly two's complement Signed LEB128.
     */
    public static byte[] writeSignedLeb128(long value) {
        int size = computeSignedLeb128Size(value);
        byte[] out = new byte[size];
        long current = value;
        int idx = 0;
        boolean more = true;
        while (more) {
            byte b = (byte) (current & 0x7FL);
            current >>= 7; // arithmetic shift preserves sign
            boolean signBitClear = (b & 0x40) == 0;
            if ((current == 0 && signBitClear) || (current == -1 && !signBitClear)) {
                more = false;
            } else {
                b |= (byte) 0x80;
            }
            out[idx++] = b;
        }
        return out;
    }

    /**
     * Writes a signed 64-bit integer using Signed LEB128 into {@link ByteBuffer}.
     */
    public static int writeSignedLeb128(long value, ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer must not be null");
        byte[] bytes = writeSignedLeb128(value);
        buffer.put(bytes);
        return bytes.length;
    }

    /**
     * Writes a signed 64-bit integer using Signed LEB128 into {@link OutputStream}.
     */
    public static int writeSignedLeb128(long value, OutputStream out) throws IOException {
        Objects.requireNonNull(out, "out must not be null");
        byte[] bytes = writeSignedLeb128(value);
        out.write(bytes);
        return bytes.length;
    }

    /**
     * Reads a two's complement Signed LEB128 64-bit integer from {@code bytes} at offset 0.
     */
    public static long readSignedLeb128(byte[] bytes) {
        return readSignedLeb128(bytes, 0);
    }

    /**
     * Reads a two's complement Signed LEB128 64-bit integer from {@code bytes} starting at {@code offset}.
     */
    public static long readSignedLeb128(byte[] bytes, int offset) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        if (offset < 0 || offset >= bytes.length) {
            throw new IllegalArgumentException("Invalid offset " + offset + " for byte array of length " + bytes.length);
        }
        long result = 0L;
        int shift = 0;
        int idx = offset;
        byte b;
        while (true) {
            if (idx >= bytes.length) {
                throw new IllegalArgumentException("Truncated Signed LEB128: unexpected end of byte array");
            }
            if (shift >= 64) {
                throw new IllegalArgumentException("Malformed Signed LEB128: exceeds maximum 10 bytes (64 bits)");
            }
            b = bytes[idx++];
            long payload = b & 0x7FL;
            if (shift == 63 && payload != 0L && payload != 0x7FL) {
                throw new IllegalArgumentException("Signed LEB128 overflow: 10th byte has invalid sign-extension bits");
            }
            result |= (payload << shift);
            shift += 7;
            if ((b & 0x80) == 0) {
                break;
            }
        }
        // Sign extend if the sign bit (0x40) of the final byte is set and we haven't filled all 64 bits
        if (shift < 64 && (b & 0x40) != 0) {
            result |= (~0L << shift);
        }
        return result;
    }

    /**
     * Reads a two's complement Signed LEB128 64-bit integer from {@link ByteBuffer}.
     */
    public static long readSignedLeb128(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer must not be null");
        long result = 0L;
        int shift = 0;
        byte b;
        while (true) {
            if (!buffer.hasRemaining()) {
                throw new IllegalArgumentException("Truncated Signed LEB128: ByteBuffer underflow", new BufferUnderflowException());
            }
            if (shift >= 64) {
                throw new IllegalArgumentException("Malformed Signed LEB128: exceeds maximum 10 bytes (64 bits)");
            }
            b = buffer.get();
            long payload = b & 0x7FL;
            if (shift == 63 && payload != 0L && payload != 0x7FL) {
                throw new IllegalArgumentException("Signed LEB128 overflow: 10th byte has invalid sign-extension bits");
            }
            result |= (payload << shift);
            shift += 7;
            if ((b & 0x80) == 0) {
                break;
            }
        }
        if (shift < 64 && (b & 0x40) != 0) {
            result |= (~0L << shift);
        }
        return result;
    }

    /**
     * Reads a two's complement Signed LEB128 64-bit integer from {@link InputStream}.
     */
    public static long readSignedLeb128(InputStream in) throws IOException {
        Objects.requireNonNull(in, "in must not be null");
        long result = 0L;
        int shift = 0;
        int read;
        while (true) {
            read = in.read();
            if (read == -1) {
                throw new EOFException("Truncated Signed LEB128: unexpected EOF");
            }
            if (shift >= 64) {
                throw new IllegalArgumentException("Malformed Signed LEB128: exceeds maximum 10 bytes (64 bits)");
            }
            long payload = read & 0x7FL;
            if (shift == 63 && payload != 0L && payload != 0x7FL) {
                throw new IllegalArgumentException("Signed LEB128 overflow: 10th byte has invalid sign-extension bits");
            }
            result |= (payload << shift);
            shift += 7;
            if ((read & 0x80) == 0) {
                break;
            }
        }
        if (shift < 64 && (read & 0x40) != 0) {
            result |= (~0L << shift);
        }
        return result;
    }
}
