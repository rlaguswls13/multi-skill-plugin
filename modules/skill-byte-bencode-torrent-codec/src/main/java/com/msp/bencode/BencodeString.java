package com.msp.bencode;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Representation of a Bencode byte string.
 * Compared lexicographically by unsigned byte order according to BEP 0003.
 */
public final class BencodeString implements BencodeValue, Comparable<BencodeString> {

    private final byte[] bytes;

    public BencodeString(byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "bytes cannot be null").clone();
    }

    public static BencodeString of(String str) {
        Objects.requireNonNull(str, "str cannot be null");
        return new BencodeString(str.getBytes(StandardCharsets.UTF_8));
    }

    public static BencodeString of(byte[] bytes) {
        return new BencodeString(bytes);
    }

    public byte[] getBytes() {
        return bytes.clone();
    }

    public int length() {
        return bytes.length;
    }

    public String asString() {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public String asString(Charset charset) {
        return new String(bytes, charset);
    }

    @Override
    public int compareTo(BencodeString other) {
        return Arrays.compareUnsigned(this.bytes, other.bytes);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BencodeString other)) return false;
        return Arrays.equals(this.bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return asString();
    }
}
