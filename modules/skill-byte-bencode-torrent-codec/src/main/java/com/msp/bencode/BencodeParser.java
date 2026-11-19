package com.msp.bencode;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Strict parser for BitTorrent Bencode format (BEP 0003).
 */
public final class BencodeParser {

    private final InputStream in;

    public BencodeParser(InputStream in) {
        this.in = Objects.requireNonNull(in, "in cannot be null");
    }

    public static BencodeValue parse(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        try {
            BencodeParser parser = new BencodeParser(new ByteArrayInputStream(bytes));
            BencodeValue value = parser.parseValue();
            if (parser.in.read() != -1) {
                throw new BencodeException("Unexpected trailing bytes after root element");
            }
            return value;
        } catch (IOException e) {
            throw new BencodeException("I/O error during bencode parsing", e);
        }
    }

    public static BencodeDict parseDict(byte[] bytes) {
        BencodeValue val = parse(bytes);
        if (val instanceof BencodeDict dict) {
            return dict;
        }
        throw new BencodeException("Expected bencode dictionary, found: " + val.getClass().getSimpleName());
    }

    public static BencodeValue parse(InputStream is) throws IOException {
        BencodeParser parser = new BencodeParser(is);
        return parser.parseValue();
    }

    public BencodeValue parseValue() throws IOException {
        int b = peekByte();
        if (b == -1) {
            throw new EOFException("Unexpected end of bencode stream");
        }
        return switch (b) {
            case 'i' -> parseInteger();
            case 'l' -> parseList();
            case 'd' -> parseDict();
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseString();
            default -> throw new BencodeException("Unexpected token at start of value: '" + (char) b + "' (0x" + Integer.toHexString(b) + ")");
        };
    }

    private BencodeInteger parseInteger() throws IOException {
        readExpected('i');
        int first = in.read();
        if (first == -1) {
            throw new EOFException("Unexpected EOF while parsing integer");
        }
        if (first == 'e') {
            throw new BencodeException("Empty integer specification 'ie'");
        }

        boolean negative = false;
        StringBuilder sb = new StringBuilder();

        if (first == '-') {
            negative = true;
            int next = in.read();
            if (next == -1) {
                throw new EOFException("Unexpected EOF after '-' in integer");
            }
            if (next == '0') {
                throw new BencodeException("Negative zero 'i-0e' is forbidden by BEP 0003");
            }
            if (!isDigit(next)) {
                throw new BencodeException("Invalid character after '-' in integer: '" + (char) next + "'");
            }
            sb.append((char) next);
        } else if (first == '0') {
            int end = in.read();
            if (end != 'e') {
                throw new BencodeException("Leading zeroes are forbidden in integers (found '0" + (char) end + "')");
            }
            return new BencodeInteger(0L);
        } else if (isDigit(first)) {
            sb.append((char) first);
        } else {
            throw new BencodeException("Invalid character in integer: '" + (char) first + "'");
        }

        while (true) {
            int ch = in.read();
            if (ch == -1) {
                throw new EOFException("Unexpected EOF while parsing integer");
            }
            if (ch == 'e') {
                break;
            }
            if (!isDigit(ch)) {
                throw new BencodeException("Invalid character in integer: '" + (char) ch + "'");
            }
            sb.append((char) ch);
        }

        try {
            long val = Long.parseLong(sb.toString());
            return new BencodeInteger(negative ? -val : val);
        } catch (NumberFormatException nfe) {
            throw new BencodeException("Integer overflow: " + (negative ? "-" : "") + sb, nfe);
        }
    }

    private BencodeString parseString() throws IOException {
        int first = in.read();
        if (!isDigit(first)) {
            throw new BencodeException("Expected string length digit, found: " + (char) first);
        }

        StringBuilder lenSb = new StringBuilder();
        lenSb.append((char) first);

        if (first == '0') {
            int colon = in.read();
            if (colon != ':') {
                throw new BencodeException("Leading zeroes forbidden in string length");
            }
            return new BencodeString(new byte[0]);
        }

        while (true) {
            int ch = in.read();
            if (ch == -1) {
                throw new EOFException("Unexpected EOF while parsing string length");
            }
            if (ch == ':') {
                break;
            }
            if (!isDigit(ch)) {
                throw new BencodeException("Invalid character in string length: '" + (char) ch + "'");
            }
            lenSb.append((char) ch);
        }

        int length;
        try {
            length = Integer.parseInt(lenSb.toString());
        } catch (NumberFormatException e) {
            throw new BencodeException("String length too large: " + lenSb, e);
        }

        if (length < 0) {
            throw new BencodeException("Negative string length: " + length);
        }

        byte[] bytes = in.readNBytes(length);
        if (bytes.length < length) {
            throw new EOFException("Unexpected EOF while reading string bytes: expected " + length + ", got " + bytes.length);
        }

        return new BencodeString(bytes);
    }

    private BencodeList parseList() throws IOException {
        readExpected('l');
        List<BencodeValue> elements = new ArrayList<>();

        while (true) {
            int b = peekByte();
            if (b == -1) {
                throw new EOFException("Unexpected EOF inside list");
            }
            if (b == 'e') {
                in.read(); // consume 'e'
                break;
            }
            elements.add(parseValue());
        }

        return new BencodeList(elements);
    }

    private BencodeDict parseDict() throws IOException {
        readExpected('d');
        TreeMap<BencodeString, BencodeValue> entries = new TreeMap<>();
        BencodeString lastKey = null;

        while (true) {
            int b = peekByte();
            if (b == -1) {
                throw new EOFException("Unexpected EOF inside dictionary");
            }
            if (b == 'e') {
                in.read(); // consume 'e'
                break;
            }

            if (!isDigit(b)) {
                throw new BencodeException("Dictionary key must be a string, found: '" + (char) b + "'");
            }

            BencodeString key = parseString();

            if (lastKey != null) {
                int cmp = key.compareTo(lastKey);
                if (cmp < 0) {
                    throw new BencodeException("Dictionary keys must be sorted: '" + key + "' followed '" + lastKey + "'");
                }
                if (cmp == 0) {
                    throw new BencodeException("Duplicate dictionary key: '" + key + "'");
                }
            }
            lastKey = key;

            BencodeValue value = parseValue();
            entries.put(key, value);
        }

        return new BencodeDict(entries);
    }

    private void readExpected(int expected) throws IOException {
        int actual = in.read();
        if (actual != expected) {
            throw new BencodeException("Expected '" + (char) expected + "', got: " + (actual == -1 ? "EOF" : "'" + (char) actual + "'"));
        }
    }

    private int peekByte() throws IOException {
        if (!in.markSupported()) {
            throw new IllegalStateException("InputStream must support mark/reset");
        }
        in.mark(1);
        int b = in.read();
        in.reset();
        return b;
    }

    private static boolean isDigit(int ch) {
        return ch >= '0' && ch <= '9';
    }
}
