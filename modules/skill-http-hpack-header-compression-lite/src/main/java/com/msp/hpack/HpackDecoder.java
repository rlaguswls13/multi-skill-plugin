package com.msp.hpack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * RFC 7541 HPACK Header Decoder.
 * Stateful decoder maintaining a Dynamic Table across HTTP/2 header blocks.
 */
public class HpackDecoder {

    private final HpackDynamicTable dynamicTable;

    public HpackDecoder(HpackDynamicTable dynamicTable) {
        this.dynamicTable = Objects.requireNonNull(dynamicTable, "dynamicTable must not be null");
    }

    public HpackDecoder(int maxDynamicTableSize) {
        this(new HpackDynamicTable(maxDynamicTableSize));
    }

    public HpackDecoder() {
        this(HpackDynamicTable.DEFAULT_MAX_CAPACITY);
    }

    public HpackDynamicTable getDynamicTable() {
        return dynamicTable;
    }

    /**
     * Decodes an HPACK header block into a list of HeaderFields.
     */
    public synchronized List<HeaderField> decode(byte[] buffer) {
        ByteArrayInputStream in = new ByteArrayInputStream(buffer);
        List<HeaderField> headers = new ArrayList<>();

        try {
            while (in.available() > 0) {
                int b = in.read() & 0xFF;

                if ((b & 0x80) != 0) {
                    // Indexed Header Field (Section 6.1: 1xxxxxxx)
                    int index = decodeInt(in, b, 7);
                    if (index == 0) {
                        throw new HpackException("Indexed header field index cannot be 0");
                    }
                    headers.add(getHeaderField(index));
                } else if ((b & 0xC0) == 0x40) {
                    // Literal Header Field with Incremental Indexing (Section 6.2.1: 01xxxxxx)
                    int index = decodeInt(in, b, 6);
                    String name = (index == 0) ? decodeString(in) : getHeaderField(index).name();
                    String value = decodeString(in);
                    HeaderField field = new HeaderField(name, value);
                    dynamicTable.add(field);
                    headers.add(field);
                } else if ((b & 0xE0) == 0x20) {
                    // Dynamic Table Size Update (Section 6.3: 001xxxxx)
                    int newMaxSize = decodeInt(in, b, 5);
                    dynamicTable.setMaxCapacity(newMaxSize);
                } else if ((b & 0xF0) == 0x10) {
                    // Literal Header Field Never Indexed (Section 6.2.3: 0001xxxx)
                    int index = decodeInt(in, b, 4);
                    String name = (index == 0) ? decodeString(in) : getHeaderField(index).name();
                    String value = decodeString(in);
                    headers.add(new HeaderField(name, value, true));
                } else if ((b & 0xF0) == 0x00) {
                    // Literal Header Field without Indexing (Section 6.2.2: 0000xxxx)
                    int index = decodeInt(in, b, 4);
                    String name = (index == 0) ? decodeString(in) : getHeaderField(index).name();
                    String value = decodeString(in);
                    headers.add(new HeaderField(name, value, false));
                } else {
                    throw new HpackException("Invalid HPACK header representation byte: 0x" + Integer.toHexString(b));
                }
            }
        } catch (IOException e) {
            throw new HpackException("Failed to decode HPACK header block", e);
        }

        return headers;
    }

    private HeaderField getHeaderField(int index) {
        if (index <= HpackStaticTable.size()) {
            return HpackStaticTable.get(index);
        }
        return dynamicTable.get(index);
    }

    private String decodeString(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new HpackException("Unexpected EOF while reading string literal prefix");
        }
        boolean isHuffman = (b & 0x80) != 0;
        int length = decodeInt(in, b, 7);
        byte[] bytes = in.readNBytes(length);
        if (bytes.length < length) {
            throw new HpackException("Truncated string literal: expected " + length + ", got " + bytes.length);
        }

        if (isHuffman) {
            return HpackHuffman.decodeToString(bytes);
        } else {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    static int decodeInt(InputStream in, int firstByte, int n) throws IOException {
        int maxPrefix = (1 << n) - 1;
        int value = firstByte & maxPrefix;
        if (value < maxPrefix) {
            return value;
        }
        int m = 0;
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new HpackException("Unexpected EOF while decoding HPACK integer");
            }
            value += (b & 0x7F) << m;
            m += 7;
            if ((b & 0x80) == 0) {
                break;
            }
        }
        return value;
    }
}
