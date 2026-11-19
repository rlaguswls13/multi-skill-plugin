package com.msp.tlvcodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Thread-safe streaming ASN.1 BER-TLV and EMV TLV encoder/decoder.
 *
 * <p>Supports:
 * <ul>
 *   <li>Single-byte and multi-byte high-tag-number tags across all 4 tag classes</li>
 *   <li>Definite short-form lengths (0..127) and definite long-form lengths (128..maxValueLength)</li>
 *   <li>Indefinite-length constructed encodings ({@code 0x80} ... EOC {@code 0x00 0x00})</li>
 *   <li>Configurable maximum nesting depth and value length limits to prevent resource exhaustion</li>
 * </ul>
 */
public final class BerTlvCodec {

    public static final int DEFAULT_MAX_DEPTH = 32;
    public static final int DEFAULT_MAX_VALUE_LENGTH = 16 * 1024 * 1024; // 16 MiB

    private static final int INDEFINITE_LENGTH = -1;

    private final int maxDepth;
    private final int maxValueLength;

    /**
     * Creates a codec with default depth (32) and value length (16 MiB) limits.
     */
    public BerTlvCodec() {
        this(DEFAULT_MAX_DEPTH, DEFAULT_MAX_VALUE_LENGTH);
    }

    /**
     * Creates a codec with explicit recursion depth and value length bounds.
     */
    public BerTlvCodec(int maxDepth, int maxValueLength) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be >= 1: " + maxDepth);
        }
        if (maxValueLength < 0) {
            throw new IllegalArgumentException("maxValueLength must be >= 0: " + maxValueLength);
        }
        this.maxDepth = maxDepth;
        this.maxValueLength = maxValueLength;
    }

    /**
     * Encodes {@code node} using definite length encoding into a byte array.
     */
    public byte[] encode(TlvNode node) {
        Objects.requireNonNull(node, "node must not be null");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            encode(node, baos);
        } catch (IOException e) {
            throw new TlvCodecException("Unexpected I/O error encoding TLV", e);
        }
        return baos.toByteArray();
    }

    /**
     * Encodes {@code node} using definite length encoding directly to {@code out}.
     */
    public void encode(TlvNode node, OutputStream out) throws IOException {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(out, "out must not be null");
        encodeNode(node, out, 0);
    }

    /**
     * Encodes a constructed TLV node using BER indefinite-length form ({@code 0x80} ... {@code 0x00 0x00}).
     */
    public byte[] encodeIndefinite(ConstructedTlv node) {
        Objects.requireNonNull(node, "node must not be null");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            encodeIndefinite(node, baos);
        } catch (IOException e) {
            throw new TlvCodecException("Unexpected I/O error encoding indefinite TLV", e);
        }
        return baos.toByteArray();
    }

    /**
     * Encodes a constructed TLV node using BER indefinite-length form to {@code out}.
     */
    public void encodeIndefinite(ConstructedTlv node, OutputStream out) throws IOException {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(out, "out must not be null");
        out.write(node.tag().encodedBytes());
        out.write(0x80);
        for (TlvNode child : node.children()) {
            encodeNode(child, out, 1);
        }
        out.write(0x00);
        out.write(0x00);
    }

    /**
     * Decodes a single BER-TLV node from a byte array.
     */
    public TlvNode decode(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
            Optional<TlvNode> node = decode(bais);
            if (node.isEmpty()) {
                throw new TlvCodecException("Empty BER-TLV input");
            }
            if (bais.available() > 0) {
                throw new TlvCodecException("Unexpected trailing bytes (" + bais.available() + ") after TLV node");
            }
            return node.get();
        } catch (IOException e) {
            throw new TlvCodecException("I/O error decoding BER-TLV", e);
        }
    }

    /**
     * Decodes a single BER-TLV node from {@code in}, returning {@link Optional#empty()} on clean EOF.
     */
    public Optional<TlvNode> decode(InputStream in) throws IOException {
        Objects.requireNonNull(in, "in must not be null");
        TlvTag tag = TlvTag.readFromOrNull(in);
        if (tag == null) {
            return Optional.empty();
        }
        return Optional.of(decodeAfterTag(tag, in, 0));
    }

    /**
     * Decodes all consecutive BER-TLV nodes from a byte array.
     */
    public List<TlvNode> decodeAll(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
            return decodeAll(bais);
        } catch (IOException e) {
            throw new TlvCodecException("I/O error decoding BER-TLV stream", e);
        }
    }

    /**
     * Decodes all consecutive BER-TLV nodes from {@code in} until EOF.
     */
    public List<TlvNode> decodeAll(InputStream in) throws IOException {
        Objects.requireNonNull(in, "in must not be null");
        List<TlvNode> nodes = new ArrayList<>();
        while (true) {
            Optional<TlvNode> next = decode(in);
            if (next.isEmpty()) {
                break;
            }
            nodes.add(next.get());
        }
        return List.copyOf(nodes);
    }

    private void encodeNode(TlvNode node, OutputStream out, int depth) throws IOException {
        if (depth > maxDepth) {
            throw new TlvCodecException("Maximum TLV nesting depth exceeded: " + maxDepth);
        }
        switch (node) {
            case PrimitiveTlv primitive -> {
                byte[] value = primitive.value();
                out.write(primitive.tag().encodedBytes());
                writeDefiniteLength(value.length, out);
                out.write(value);
            }
            case ConstructedTlv constructed -> {
                ByteArrayOutputStream childBytes = new ByteArrayOutputStream();
                for (TlvNode child : constructed.children()) {
                    encodeNode(child, childBytes, depth + 1);
                }
                byte[] payload = childBytes.toByteArray();
                out.write(constructed.tag().encodedBytes());
                writeDefiniteLength(payload.length, out);
                out.write(payload);
            }
        }
    }

    private TlvNode decodeAfterTag(TlvTag tag, InputStream in, int depth) throws IOException {
        if (depth > maxDepth) {
            throw new TlvCodecException("Maximum TLV nesting depth exceeded: " + maxDepth);
        }
        int length = readLength(in);
        if (length == INDEFINITE_LENGTH) {
            if (!tag.isConstructed()) {
                throw new TlvCodecException("Indefinite length (0x80) is not permitted for primitive tag: " + tag);
            }
            List<TlvNode> children = new ArrayList<>();
            while (true) {
                TlvTag childTag = TlvTag.readFrom(in);
                if (childTag.isEndOfContent()) {
                    int eocLen = in.read();
                    if (eocLen != 0x00) {
                        throw new TlvCodecException("Invalid End-of-Content marker length byte: " + eocLen);
                    }
                    break;
                }
                children.add(decodeAfterTag(childTag, in, depth + 1));
            }
            return new ConstructedTlv(tag, children);
        }

        if (length > maxValueLength) {
            throw new TlvCodecException("TLV value length " + length + " exceeds maxValueLength " + maxValueLength);
        }

        byte[] content = readExactBytes(in, length);
        if (!tag.isConstructed()) {
            return new PrimitiveTlv(tag, content);
        }

        List<TlvNode> children = new ArrayList<>();
        try (ByteArrayInputStream childStream = new ByteArrayInputStream(content)) {
            while (true) {
                TlvTag childTag = TlvTag.readFromOrNull(childStream);
                if (childTag == null) {
                    break;
                }
                children.add(decodeAfterTag(childTag, childStream, depth + 1));
            }
        }
        return new ConstructedTlv(tag, children);
    }

    private int readLength(InputStream in) throws IOException {
        int first = in.read();
        if (first == -1) {
            throw new TlvCodecException("Unexpected EOF while reading TLV length");
        }
        if ((first & 0x80) == 0) {
            return first;
        }
        int numOctets = first & 0x7F;
        if (numOctets == 0) {
            return INDEFINITE_LENGTH;
        }
        if (numOctets == 0x7F) {
            throw new TlvCodecException("Reserved BER length octet 0xFF is invalid");
        }
        if (numOctets > 4) {
            throw new TlvCodecException("Definite length exceeds 32-bit range: " + numOctets + " octets");
        }
        int length = 0;
        for (int i = 0; i < numOctets; i++) {
            int b = in.read();
            if (b == -1) {
                throw new TlvCodecException("Unexpected EOF while reading long-form TLV length");
            }
            if (i == 0 && numOctets == 4 && (b & 0x80) != 0) {
                throw new TlvCodecException("Definite length overflows signed 32-bit integer");
            }
            length = (length << 8) | b;
        }
        return length;
    }

    private static void writeDefiniteLength(int length, OutputStream out) throws IOException {
        if (length < 0x80) {
            out.write(length);
        } else if (length <= 0xFF) {
            out.write(0x81);
            out.write(length);
        } else if (length <= 0xFFFF) {
            out.write(0x82);
            out.write((length >>> 8) & 0xFF);
            out.write(length & 0xFF);
        } else if (length <= 0xFFFFFF) {
            out.write(0x83);
            out.write((length >>> 16) & 0xFF);
            out.write((length >>> 8) & 0xFF);
            out.write(length & 0xFF);
        } else {
            out.write(0x84);
            out.write((length >>> 24) & 0xFF);
            out.write((length >>> 16) & 0xFF);
            out.write((length >>> 8) & 0xFF);
            out.write(length & 0xFF);
        }
    }

    private static byte[] readExactBytes(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buf, offset, length - offset);
            if (read == -1) {
                throw new TlvCodecException(
                        "Unexpected EOF while reading TLV value: expected " + length + " bytes, got " + offset);
            }
            offset += read;
        }
        return buf;
    }
}
