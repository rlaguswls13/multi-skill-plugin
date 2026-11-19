package com.msp.bencode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Convenient facade for Bencode encoding and decoding.
 */
public final class Bencode {

    private Bencode() {}

    public static BencodeValue decode(byte[] bytes) {
        return BencodeParser.parse(bytes);
    }

    public static BencodeDict decodeDict(byte[] bytes) {
        return BencodeParser.parseDict(bytes);
    }

    public static BencodeValue decode(InputStream in) throws IOException {
        return BencodeParser.parse(in);
    }

    public static byte[] encode(BencodeValue value) {
        return BencodeSerializer.serialize(value);
    }

    public static byte[] encode(Object javaObj) {
        return BencodeSerializer.serialize(javaObj);
    }

    public static void encode(BencodeValue value, OutputStream os) throws IOException {
        BencodeSerializer.serialize(value, os);
    }
}
