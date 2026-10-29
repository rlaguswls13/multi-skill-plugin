package com.msp.bloom;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Byte-array backed sink implementation.
 */
final class ByteArraySink implements Sink {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    @Override
    public Sink putByte(byte b) {
        buffer.write(b);
        return this;
    }

    @Override
    public Sink putBytes(byte[] bytes) {
        if (bytes != null && bytes.length > 0) {
            buffer.write(bytes, 0, bytes.length);
        }
        return this;
    }

    @Override
    public Sink putBytes(byte[] bytes, int off, int len) {
        if (bytes != null && len > 0) {
            buffer.write(bytes, off, len);
        }
        return this;
    }

    @Override
    public Sink putInt(int value) {
        buffer.write((byte) (value & 0xFF));
        buffer.write((byte) ((value >> 8) & 0xFF));
        buffer.write((byte) ((value >> 16) & 0xFF));
        buffer.write((byte) ((value >> 24) & 0xFF));
        return this;
    }

    @Override
    public Sink putLong(long value) {
        for (int i = 0; i < 8; i++) {
            buffer.write((byte) ((value >> (i * 8)) & 0xFF));
        }
        return this;
    }

    @Override
    public Sink putString(CharSequence charSequence) {
        if (charSequence != null) {
            byte[] bytes = charSequence.toString().getBytes(StandardCharsets.UTF_8);
            putBytes(bytes);
        }
        return this;
    }

    public byte[] toByteArray() {
        return buffer.toByteArray();
    }
}
