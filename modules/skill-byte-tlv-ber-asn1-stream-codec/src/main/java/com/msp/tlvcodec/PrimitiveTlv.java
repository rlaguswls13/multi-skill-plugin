package com.msp.tlvcodec;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable primitive BER-TLV leaf node holding raw value bytes.
 */
public final class PrimitiveTlv implements TlvNode {

    private final TlvTag tag;
    private final byte[] value;

    public PrimitiveTlv(TlvTag tag, byte[] value) {
        Objects.requireNonNull(tag, "tag must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (tag.isConstructed()) {
            throw new IllegalArgumentException("PrimitiveTlv requires a primitive tag, got constructed tag: " + tag);
        }
        this.tag = tag;
        this.value = value.clone();
    }

    public static PrimitiveTlv of(TlvTag tag, byte[] value) {
        return new PrimitiveTlv(tag, value);
    }

    public static PrimitiveTlv ofHex(String tagHex, String valueHex) {
        return new PrimitiveTlv(TlvTag.fromHex(tagHex), TlvHexFormatter.fromHex(valueHex));
    }

    public static PrimitiveTlv ofUtf8(TlvTag tag, String text) {
        Objects.requireNonNull(text, "text must not be null");
        return new PrimitiveTlv(tag, text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public TlvTag tag() {
        return tag;
    }

    /**
     * Returns a defensive copy of the raw value bytes.
     */
    public byte[] value() {
        return value.clone();
    }

    /**
     * Returns the length of the value payload in bytes.
     */
    public int length() {
        return value.length;
    }

    /**
     * Returns the value bytes formatted as uppercase hexadecimal.
     */
    public String valueHex() {
        return TlvHexFormatter.toHex(value);
    }

    /**
     * Decodes the value bytes as UTF-8 text.
     */
    public String valueUtf8() {
        return new String(value, StandardCharsets.UTF_8);
    }

    @Override
    public void accept(TlvVisitor visitor, int depth) {
        Objects.requireNonNull(visitor, "visitor must not be null");
        visitor.visitPrimitive(this, depth);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrimitiveTlv other)) return false;
        return tag.equals(other.tag) && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return 31 * tag.hashCode() + Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "PrimitiveTlv[tag=" + tag.toHex() + ", len=" + value.length + ", value=" + valueHex() + "]";
    }
}
