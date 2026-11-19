package com.msp.bencode;

/**
 * Representation of a Bencode integer: i<number>e.
 */
public record BencodeInteger(long value) implements BencodeValue {

    public static BencodeInteger of(long value) {
        return new BencodeInteger(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
