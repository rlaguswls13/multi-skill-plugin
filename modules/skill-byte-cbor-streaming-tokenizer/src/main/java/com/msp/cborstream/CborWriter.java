package com.msp.cborstream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Pure Java 21 CBOR binary stream encoder conforming to RFC 8949.
 */
public class CborWriter implements AutoCloseable {

    private final OutputStream out;

    public CborWriter(OutputStream out) {
        this.out = Objects.requireNonNull(out, "out cannot be null");
    }

    public static CborWriter create() {
        return new CborWriter(new ByteArrayOutputStream());
    }

    public byte[] toByteArray() {
        if (out instanceof ByteArrayOutputStream baos) {
            return baos.toByteArray();
        }
        throw new IllegalStateException("CborWriter was not created with an internal ByteArrayOutputStream");
    }

    public CborWriter writeInt(long val) throws IOException {
        if (val >= 0) {
            writeMajorAndLength(0, val);
        } else {
            writeMajorAndLength(1, -1L - val);
        }
        return this;
    }

    public CborWriter writeByteString(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        writeMajorAndLength(2, bytes.length);
        out.write(bytes);
        return this;
    }

    public CborWriter writeTextString(String text) throws IOException {
        Objects.requireNonNull(text, "text cannot be null");
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        writeMajorAndLength(3, utf8.length);
        out.write(utf8);
        return this;
    }

    public CborWriter writeStartArray(long length) throws IOException {
        writeMajorAndLength(4, length);
        return this;
    }

    public CborWriter writeStartIndefiniteArray() throws IOException {
        out.write((4 << 5) | 31);
        return this;
    }

    public CborWriter writeStartMap(long length) throws IOException {
        writeMajorAndLength(5, length);
        return this;
    }

    public CborWriter writeStartIndefiniteMap() throws IOException {
        out.write((5 << 5) | 31);
        return this;
    }

    public CborWriter writeTag(long tag) throws IOException {
        writeMajorAndLength(6, tag);
        return this;
    }

    public CborWriter writeBoolean(boolean b) throws IOException {
        out.write((7 << 5) | (b ? 21 : 20));
        return this;
    }

    public CborWriter writeNull() throws IOException {
        out.write((7 << 5) | 22);
        return this;
    }

    public CborWriter writeUndefined() throws IOException {
        out.write((7 << 5) | 23);
        return this;
    }

    public CborWriter writeFloat(float f) throws IOException {
        out.write((7 << 5) | 26);
        int bits = Float.floatToIntBits(f);
        writeUint32(bits);
        return this;
    }

    public CborWriter writeDouble(double d) throws IOException {
        out.write((7 << 5) | 27);
        long bits = Double.doubleToLongBits(d);
        writeInt64(bits);
        return this;
    }

    public CborWriter writeBreak() throws IOException {
        out.write(0xFF);
        return this;
    }

    private void writeMajorAndLength(int major, long length) throws IOException {
        int m = major << 5;
        if (length < 24) {
            out.write(m | (int) length);
        } else if (length <= 0xFFL) {
            out.write(m | 24);
            out.write((int) length);
        } else if (length <= 0xFFFFL) {
            out.write(m | 25);
            writeUint16((int) length);
        } else if (length <= 0xFFFFFFFFL) {
            out.write(m | 26);
            writeUint32((int) length);
        } else {
            out.write(m | 27);
            writeInt64(length);
        }
    }

    private void writeUint16(int val) throws IOException {
        out.write((val >> 8) & 0xFF);
        out.write(val & 0xFF);
    }

    private void writeUint32(long val) throws IOException {
        out.write((int) ((val >> 24) & 0xFF));
        out.write((int) ((val >> 16) & 0xFF));
        out.write((int) ((val >> 8) & 0xFF));
        out.write((int) (val & 0xFF));
    }

    private void writeInt64(long val) throws IOException {
        for (int i = 7; i >= 0; i--) {
            out.write((int) ((val >> (i * 8)) & 0xFF));
        }
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
