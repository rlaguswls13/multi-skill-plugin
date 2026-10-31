package com.msp.murmur3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Immutable record representing the 128-bit output of MurmurHash3_x64_128.
 *
 * @param val1 the first 64-bit hash word (h1)
 * @param val2 the second 64-bit hash word (h2)
 */
public record Murmur128Result(long val1, long val2) {

    /**
     * Formats the 128-bit hash as a 32-character lowercase hexadecimal string (big-endian).
     *
     * @return 32-character hex representation
     */
    public String toHexString() {
        return String.format("%016x%016x", val1, val2);
    }

    /**
     * Formats the 128-bit hash in byte-order hexadecimal representation (matching standard byte sequence).
     *
     * @param littleEndian true for little-endian byte ordering (Guava compatible), false for big-endian
     * @return 32-character hex representation
     */
    public String toHexString(boolean littleEndian) {
        if (!littleEndian) {
            return toHexString();
        }
        byte[] bytes = toByteArray(true);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * Converts the 128-bit hash into a 16-byte array using standard big-endian order.
     *
     * @return 16-byte array
     */
    public byte[] toByteArray() {
        return toByteArray(false);
    }

    /**
     * Converts the 128-bit hash into a 16-byte array.
     *
     * @param littleEndian true for little-endian order, false for big-endian order
     * @return 16-byte array
     */
    public byte[] toByteArray(boolean littleEndian) {
        ByteBuffer buf = ByteBuffer.allocate(16);
        buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buf.putLong(val1);
        buf.putLong(val2);
        return buf.array();
    }

    /**
     * Alias for {@link #toByteArray()}.
     */
    public byte[] asBytes() {
        return toByteArray();
    }

    @Override
    public String toString() {
        return toHexString();
    }
}
