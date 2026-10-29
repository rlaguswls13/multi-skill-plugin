package com.msp.codec;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Pure Java Base32 encoder and decoder supporting RFC 4648 and Crockford specifications.
 */
public final class Base32 {

    private static final char[] RFC4648_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final byte[] RFC4648_DECODE = new byte[128];

    private static final char[] CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final byte[] CROCKFORD_DECODE = new byte[128];

    static {
        Arrays.fill(RFC4648_DECODE, (byte) -1);
        for (int i = 0; i < RFC4648_ALPHABET.length; i++) {
            RFC4648_DECODE[RFC4648_ALPHABET[i]] = (byte) i;
            if (RFC4648_ALPHABET[i] >= 'A' && RFC4648_ALPHABET[i] <= 'Z') {
                RFC4648_DECODE[Character.toLowerCase(RFC4648_ALPHABET[i])] = (byte) i;
            }
        }

        Arrays.fill(CROCKFORD_DECODE, (byte) -1);
        for (int i = 0; i < CROCKFORD_ALPHABET.length; i++) {
            char c = CROCKFORD_ALPHABET[i];
            CROCKFORD_DECODE[c] = (byte) i;
            if (c >= 'A' && c <= 'Z') {
                CROCKFORD_DECODE[Character.toLowerCase(c)] = (byte) i;
            }
        }
        // Crockford aliases: O, o -> 0; I, i, L, l -> 1
        CROCKFORD_DECODE['O'] = 0;
        CROCKFORD_DECODE['o'] = 0;
        CROCKFORD_DECODE['I'] = 1;
        CROCKFORD_DECODE['i'] = 1;
        CROCKFORD_DECODE['L'] = 1;
        CROCKFORD_DECODE['l'] = 1;
    }

    private Base32() {}

    /**
     * Encodes byte array into RFC 4648 Base32 string with padding.
     */
    public static String encodeRfc4648(byte[] data) {
        return encodeRfc4648(data, true);
    }

    /**
     * Encodes byte array into RFC 4648 Base32 string with optional padding.
     */
    public static String encodeRfc4648(byte[] data, boolean withPadding) {
        if (data == null || data.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(((data.length + 4) / 5) * 8);
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >>> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                sb.append(RFC4648_ALPHABET[index]);
            }
        }

        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(RFC4648_ALPHABET[index]);
        }

        if (withPadding) {
            while (sb.length() % 8 != 0) {
                sb.append('=');
            }
        }
        return sb.toString();
    }

    /**
     * Decodes RFC 4648 Base32 string (case-insensitive, padding optional).
     */
    public static byte[] decodeRfc4648(String str) {
        if (str == null || str.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '=') {
                break; // Padding begins
            }
            if (c > 127 || RFC4648_DECODE[c] == -1) {
                throw new CodecException("Invalid RFC 4648 Base32 character: '" + c + "' at position " + i);
            }

            buffer = (buffer << 5) | RFC4648_DECODE[c];
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                out.write((buffer >>> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    /**
     * Encodes byte array into Crockford Base32 string (no padding).
     */
    public static String encodeCrockford(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(((data.length + 4) / 5) * 8);
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >>> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                sb.append(CROCKFORD_ALPHABET[index]);
            }
        }

        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(CROCKFORD_ALPHABET[index]);
        }
        return sb.toString();
    }

    /**
     * Decodes Crockford Base32 string (case-insensitive, hyphens ignored, aliases I/L/O mapped).
     */
    public static byte[] decodeCrockford(String str) {
        if (str == null || str.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '-') {
                continue; // Crockford allows arbitrary hyphens
            }
            if (c > 127 || CROCKFORD_DECODE[c] == -1) {
                throw new CodecException("Invalid Crockford Base32 character: '" + c + "' at position " + i);
            }

            buffer = (buffer << 5) | CROCKFORD_DECODE[c];
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                out.write((buffer >>> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}
