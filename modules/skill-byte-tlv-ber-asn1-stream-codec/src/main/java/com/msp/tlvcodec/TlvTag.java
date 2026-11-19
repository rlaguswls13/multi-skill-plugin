package com.msp.tlvcodec;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable ASN.1 BER-TLV / EMV tag identifier supporting both single-byte (tag number 0..30)
 * and multi-byte high-tag-number encodings (tag number &gt;= 31).
 */
public final class TlvTag {

    private static final int CONSTRUCTED_MASK = 0x20;
    private static final int HIGH_TAG_NUMBER_MARKER = 0x1F;
    private static final int MAX_TAG_BYTES = 5;

    private final TagClass tagClass;
    private final boolean constructed;
    private final int tagNumber;
    private final byte[] encoded;

    private TlvTag(TagClass tagClass, boolean constructed, int tagNumber, byte[] encoded) {
        this.tagClass = tagClass;
        this.constructed = constructed;
        this.tagNumber = tagNumber;
        this.encoded = encoded;
    }

    /**
     * Constructs a {@link TlvTag} from its semantic components and computes its minimal BER encoding.
     */
    public static TlvTag of(TagClass tagClass, boolean constructed, int tagNumber) {
        Objects.requireNonNull(tagClass, "tagClass must not be null");
        if (tagNumber < 0) {
            throw new IllegalArgumentException("tagNumber must be non-negative: " + tagNumber);
        }
        int first = tagClass.mask() | (constructed ? CONSTRUCTED_MASK : 0);
        if (tagNumber < HIGH_TAG_NUMBER_MARKER) {
            byte[] bytes = new byte[]{(byte) (first | tagNumber)};
            return new TlvTag(tagClass, constructed, tagNumber, bytes);
        }
        byte[] tmp = new byte[MAX_TAG_BYTES];
        int count = 0;
        int remaining = tagNumber;
        while (remaining > 0) {
            tmp[count++] = (byte) (remaining & 0x7F);
            remaining >>>= 7;
        }
        byte[] bytes = new byte[1 + count];
        bytes[0] = (byte) (first | HIGH_TAG_NUMBER_MARKER);
        for (int i = 0; i < count; i++) {
            int b = tmp[count - 1 - i] & 0x7F;
            if (i < count - 1) {
                b |= 0x80;
            }
            bytes[1 + i] = (byte) b;
        }
        return new TlvTag(tagClass, constructed, tagNumber, bytes);
    }

    /**
     * Parses a BER-TLV tag from a hexadecimal string (e.g. {@code "9F26"}, {@code "6F"}, {@code "5F2A"}).
     */
    public static TlvTag fromHex(String hex) {
        byte[] raw = TlvHexFormatter.fromHex(hex);
        if (raw.length == 0) {
            throw new TlvCodecException("Empty tag hex string");
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(raw)) {
            TlvTag tag = readFrom(bais);
            if (bais.available() != 0) {
                throw new TlvCodecException("Trailing bytes after tag in hex: " + hex);
            }
            return tag;
        } catch (IOException e) {
            throw new TlvCodecException("Failed to parse tag hex: " + hex, e);
        }
    }

    /**
     * Reads a single BER-TLV tag from {@code in}. Returns {@code null} only if EOF is reached before the first byte.
     */
    public static TlvTag readFromOrNull(InputStream in) throws IOException {
        int first = in.read();
        if (first == -1) {
            return null;
        }
        return readRest(first, in);
    }

    /**
     * Reads a single BER-TLV tag from {@code in}, throwing {@link TlvCodecException} on unexpected EOF.
     */
    public static TlvTag readFrom(InputStream in) throws IOException {
        int first = in.read();
        if (first == -1) {
            throw new TlvCodecException("Unexpected EOF while reading TLV tag");
        }
        return readRest(first, in);
    }

    private static TlvTag readRest(int first, InputStream in) throws IOException {
        TagClass tagClass = TagClass.fromFirstByte(first);
        boolean constructed = (first & CONSTRUCTED_MASK) != 0;
        int lowBits = first & HIGH_TAG_NUMBER_MARKER;
        if (lowBits < HIGH_TAG_NUMBER_MARKER) {
            return new TlvTag(tagClass, constructed, lowBits, new byte[]{(byte) first});
        }
        byte[] buf = new byte[MAX_TAG_BYTES];
        buf[0] = (byte) first;
        int len = 1;
        int tagNumber = 0;
        while (true) {
            if (len >= MAX_TAG_BYTES) {
                throw new TlvCodecException("BER-TLV multi-byte tag exceeds maximum length of " + MAX_TAG_BYTES + " bytes");
            }
            int next = in.read();
            if (next == -1) {
                throw new TlvCodecException("Unexpected EOF inside multi-byte TLV tag");
            }
            if (len == 1 && (next & 0x7F) == 0) {
                throw new TlvCodecException("Non-canonical multi-byte TLV tag with leading zero payload");
            }
            buf[len++] = (byte) next;
            tagNumber = (tagNumber << 7) | (next & 0x7F);
            if ((next & 0x80) == 0) {
                break;
            }
        }
        return new TlvTag(tagClass, constructed, tagNumber, Arrays.copyOf(buf, len));
    }

    public TagClass tagClass() {
        return tagClass;
    }

    public boolean isConstructed() {
        return constructed;
    }

    public boolean isPrimitive() {
        return !constructed;
    }

    public int tagNumber() {
        return tagNumber;
    }

    /**
     * Returns {@code true} if this tag is the ASN.1 End-of-Content marker ({@code 0x00}).
     */
    public boolean isEndOfContent() {
        return encoded.length == 1 && encoded[0] == 0x00;
    }

    /**
     * Returns a defensive copy of the raw BER-encoded tag bytes.
     */
    public byte[] encodedBytes() {
        return encoded.clone();
    }

    /**
     * Returns the uppercase hexadecimal representation of this tag (e.g. {@code "9F26"}).
     */
    public String toHex() {
        return TlvHexFormatter.toHex(encoded);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TlvTag other)) return false;
        return Arrays.equals(encoded, other.encoded);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(encoded);
    }

    @Override
    public String toString() {
        return toHex() + "(" + tagClass + "," + (constructed ? "C" : "P") + ",#" + tagNumber + ")";
    }
}
