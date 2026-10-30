package com.msp.totp;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * Standard RFC 4648 Base32 encoder and decoder implementation using pure Java 21.
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final char[] ENCODE_TABLE = ALPHABET.toCharArray();
    private static final byte[] DECODE_TABLE = new byte[128];

    static {
        Arrays.fill(DECODE_TABLE, (byte) -1);
        for (int i = 0; i < ENCODE_TABLE.length; i++) {
            DECODE_TABLE[ENCODE_TABLE[i]] = (byte) i;
            // lowercase mapping support
            char lower = Character.toLowerCase(ENCODE_TABLE[i]);
            if (lower < DECODE_TABLE.length) {
                DECODE_TABLE[lower] = (byte) i;
            }
        }
    }

    private Base32() {
    }

    /**
     * Encodes byte array into RFC 4648 Base32 string (without '=' padding).
     *
     * @param data byte array to encode
     * @return Base32 encoded string
     */
    public static String encode(byte[] data) {
        return encode(data, false);
    }

    /**
     * Encodes byte array into RFC 4648 Base32 string with optional padding.
     *
     * @param data byte array to encode
     * @param pad  whether to pad with '=' to a multiple of 8 characters
     * @return Base32 encoded string
     */
    public static String encode(byte[] data, boolean pad) {
        Objects.requireNonNull(data, "data must not be null");
        if (data.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >>> (bitsLeft - 5)) & 0x1F;
                sb.append(ENCODE_TABLE[index]);
                bitsLeft -= 5;
            }
        }

        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(ENCODE_TABLE[index]);
        }

        if (pad) {
            while (sb.length() % 8 != 0) {
                sb.append('=');
            }
        }

        return sb.toString();
    }

    /**
     * Decodes an RFC 4648 Base32 string into raw bytes.
     * Whitespace, hyphens, and padding characters ('=') are ignored.
     *
     * @param base32 input Base32 string
     * @return decoded byte array
     * @throws IllegalArgumentException if invalid characters are encountered
     */
    public static byte[] decode(String base32) {
        Objects.requireNonNull(base32, "base32 must not be null");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;

        for (int i = 0; i < base32.length(); i++) {
            char c = base32.charAt(i);
            if (c == '=' || Character.isWhitespace(c) || c == '-') {
                continue;
            }
            if (c >= DECODE_TABLE.length || DECODE_TABLE[c] == -1) {
                throw new IllegalArgumentException("Illegal character in Base32 string: " + c);
            }
            int val = DECODE_TABLE[c];
            buffer = (buffer << 5) | val;
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                int b = (buffer >>> (bitsLeft - 8)) & 0xFF;
                out.write(b);
                bitsLeft -= 8;
            }
        }

        return out.toByteArray();
    }
}
