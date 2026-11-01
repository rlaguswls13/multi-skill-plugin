package com.msp.cose;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Lightweight RFC 8949 CBOR Encoder in Pure Java 21.
 */
public final class CborWriter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public static byte[] encode(CborValue value) {
        CborWriter writer = new CborWriter();
        writer.write(value);
        return writer.toByteArray();
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }

    public void write(CborValue value) {
        switch (value) {
            case CborValue.IntegerVal(long val) -> writeInteger(val);
            case CborValue.ByteStringVal(byte[] bytes) -> writeByteString(bytes);
            case CborValue.TextStringVal(String text) -> writeTextString(text);
            case CborValue.ArrayVal(var items) -> {
                writeHeader(4, items.size());
                for (CborValue item : items) {
                    write(item);
                }
            }
            case CborValue.MapVal(var entries) -> {
                writeHeader(5, entries.size());
                for (Map.Entry<CborValue, CborValue> entry : entries.entrySet()) {
                    write(entry.getKey());
                    write(entry.getValue());
                }
            }
            case CborValue.BooleanVal(boolean val) -> out.write(val ? 0xf5 : 0xf4);
            case CborValue.NullVal() -> out.write(0xf6);
            case CborValue.TagVal(long tag, CborValue target) -> {
                writeHeader(6, tag);
                write(target);
            }
        }
    }

    public void writeInteger(long val) {
        if (val >= 0) {
            writeHeader(0, val);
        } else {
            writeHeader(1, -1 - val);
        }
    }

    public void writeByteString(byte[] bytes) {
        writeHeader(2, bytes.length);
        out.writeBytes(bytes);
    }

    public void writeTextString(String text) {
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        writeHeader(3, utf8.length);
        out.writeBytes(utf8);
    }

    private void writeHeader(int majorType, long value) {
        int mt = majorType << 5;
        if (value < 24) {
            out.write(mt | (int) value);
        } else if (value <= 0xffL) {
            out.write(mt | 24);
            out.write((int) value);
        } else if (value <= 0xffffL) {
            out.write(mt | 25);
            out.write((int) ((value >>> 8) & 0xff));
            out.write((int) (value & 0xff));
        } else if (value <= 0xffffffffL) {
            out.write(mt | 26);
            out.write((int) ((value >>> 24) & 0xff));
            out.write((int) ((value >>> 16) & 0xff));
            out.write((int) ((value >>> 8) & 0xff));
            out.write((int) (value & 0xff));
        } else {
            out.write(mt | 27);
            for (int i = 7; i >= 0; i--) {
                out.write((int) ((value >>> (i * 8)) & 0xff));
            }
        }
    }
}
