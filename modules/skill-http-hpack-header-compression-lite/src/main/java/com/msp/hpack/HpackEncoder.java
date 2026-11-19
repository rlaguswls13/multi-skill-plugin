package com.msp.hpack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * RFC 7541 HPACK Header Encoder.
 * Stateful encoder maintaining a Dynamic Table across HTTP/2 header blocks.
 */
public class HpackEncoder {

    private final HpackDynamicTable dynamicTable;
    private final boolean huffmanEnabled;

    public HpackEncoder(HpackDynamicTable dynamicTable, boolean huffmanEnabled) {
        this.dynamicTable = Objects.requireNonNull(dynamicTable, "dynamicTable must not be null");
        this.huffmanEnabled = huffmanEnabled;
    }

    public HpackEncoder(int maxDynamicTableSize, boolean huffmanEnabled) {
        this(new HpackDynamicTable(maxDynamicTableSize), huffmanEnabled);
    }

    public HpackEncoder() {
        this(HpackDynamicTable.DEFAULT_MAX_CAPACITY, true);
    }

    public HpackDynamicTable getDynamicTable() {
        return dynamicTable;
    }

    /**
     * Encodes a list of HeaderFields into an RFC 7541 HPACK header block.
     */
    public synchronized byte[] encode(List<HeaderField> headers) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (HeaderField header : headers) {
                encodeHeader(out, header);
            }
        } catch (IOException e) {
            throw new HpackException("Failed to encode HPACK headers", e);
        }
        return out.toByteArray();
    }

    /**
     * Emits a dynamic table size update header representation (Section 6.3).
     */
    public synchronized byte[] encodeTableSizeUpdate(int newMaxSize) {
        dynamicTable.setMaxCapacity(newMaxSize);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            encodeInt(out, 0x20, 5, newMaxSize);
        } catch (IOException e) {
            throw new HpackException("Failed to encode table size update", e);
        }
        return out.toByteArray();
    }

    private void encodeHeader(ByteArrayOutputStream out, HeaderField field) throws IOException {
        String name = field.name();
        String value = field.value();

        // 1. Check exact match in static table
        int exactIdx = HpackStaticTable.findExact(name, value);
        if (exactIdx > 0) {
            encodeInt(out, 0x80, 7, exactIdx);
            return;
        }

        // 2. Check exact match in dynamic table
        exactIdx = dynamicTable.findExact(name, value);
        if (exactIdx > 0) {
            encodeInt(out, 0x80, 7, exactIdx);
            return;
        }

        // 3. Find name match
        int nameIdx = HpackStaticTable.findName(name);
        if (nameIdx < 0) {
            nameIdx = dynamicTable.findName(name);
        }

        if (field.sensitive()) {
            // Literal Header Field Never Indexed (Section 6.2.3: 0001xxxx)
            if (nameIdx > 0) {
                encodeInt(out, 0x10, 4, nameIdx);
            } else {
                encodeInt(out, 0x10, 4, 0);
                encodeString(out, name);
            }
            encodeString(out, value);
        } else {
            // Literal Header Field with Incremental Indexing (Section 6.2.1: 01xxxxxx)
            if (nameIdx > 0) {
                encodeInt(out, 0x40, 6, nameIdx);
            } else {
                encodeInt(out, 0x40, 6, 0);
                encodeString(out, name);
            }
            encodeString(out, value);
            dynamicTable.add(field);
        }
    }

    private void encodeString(ByteArrayOutputStream out, String value) throws IOException {
        byte[] rawBytes = value.getBytes(StandardCharsets.ISO_8859_1);
        if (huffmanEnabled) {
            int huffmanLen = HpackHuffman.encodedLength(rawBytes);
            if (huffmanLen < rawBytes.length) {
                encodeInt(out, 0x80, 7, huffmanLen);
                out.write(HpackHuffman.encode(rawBytes));
                return;
            }
        }
        encodeInt(out, 0x00, 7, rawBytes.length);
        out.write(rawBytes);
    }

    static void encodeInt(ByteArrayOutputStream out, int prefixByte, int n, int value) throws IOException {
        int maxPrefix = (1 << n) - 1;
        if (value < maxPrefix) {
            out.write(prefixByte | value);
        } else {
            out.write(prefixByte | maxPrefix);
            value -= maxPrefix;
            while (value >= 128) {
                out.write((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            out.write(value);
        }
    }
}
