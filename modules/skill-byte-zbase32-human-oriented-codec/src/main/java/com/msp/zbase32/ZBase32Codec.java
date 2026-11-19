package com.msp.zbase32;

import java.util.Arrays;
import java.util.Objects;

/**
 * Thread-safe, zero-dependency implementation of Phil Zimmermann's human-oriented
 * {@code z-base-32} encoding ({@code ybndrfg8ejkmcpqxot1uwisza345h769}).
 * <p>
 * Supports both byte-aligned arrays and arbitrary bit-length encoding/decoding,
 * case-insensitive decoding, and strict canonical padding validation.
 */
public final class ZBase32Codec {

    /** Canonical 32-character z-base-32 alphabet (indices 0..31). */
    public static final String ALPHABET = "ybndrfg8ejkmcpqxot1uwisza345h769";

    private static final char[] ENCODE_TABLE = ALPHABET.toCharArray();
    private static final byte[] DECODE_TABLE = new byte[128];

    private static final ZBase32Codec STRICT = new ZBase32Codec(true);
    private static final ZBase32Codec LENIENT = new ZBase32Codec(false);

    static {
        Arrays.fill(DECODE_TABLE, (byte) -1);
        for (int i = 0; i < ENCODE_TABLE.length; i++) {
            char c = ENCODE_TABLE[i];
            DECODE_TABLE[c] = (byte) i;
            if (c >= 'a' && c <= 'z') {
                DECODE_TABLE[c - 'a' + 'A'] = (byte) i;
            }
        }
    }

    private final boolean strict;

    private ZBase32Codec(boolean strict) {
        this.strict = strict;
    }

    /** Returns the strict codec (rejects non-zero trailing padding bits and invalid byte-aligned lengths). */
    public static ZBase32Codec strict() {
        return STRICT;
    }

    /** Returns a lenient codec (ignores non-zero trailing padding bits during decoding). */
    public static ZBase32Codec lenient() {
        return LENIENT;
    }

    public boolean isStrict() {
        return strict;
    }

    /** Encodes a byte array using the z-base-32 alphabet (no padding characters). */
    public static String encodeBytes(byte[] data) {
        return STRICT.encode(data);
    }

    /** Decodes a z-base-32 string into bytes using strict validation. */
    public static byte[] decodeBytes(String encoded) {
        return STRICT.decode(encoded);
    }

    /** Encodes the entire byte array ({@code data.length * 8} bits). */
    public String encode(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        if (data.length == 0) {
            return "";
        }
        return encodeBits(data, Math.multiplyExact(data.length, 8));
    }

    /**
     * Encodes the first {@code bitCount} bits (MSB-first) from {@code data}.
     *
     * @param data     source byte array containing at least {@code ceil(bitCount / 8)} bytes
     * @param bitCount number of bits to encode (>= 0)
     * @return z-base-32 encoded string of length {@code ceil(bitCount / 5)}
     */
    public String encodeBits(byte[] data, int bitCount) {
        Objects.requireNonNull(data, "data must not be null");
        if (bitCount < 0) {
            throw new IllegalArgumentException("bitCount must be >= 0, got: " + bitCount);
        }
        if (bitCount == 0) {
            return "";
        }
        int requiredBytes = (bitCount + 7) >>> 3;
        if (data.length < requiredBytes) {
            throw new IllegalArgumentException(
                    "data length (" + data.length + " bytes) is insufficient for " + bitCount + " bits");
        }

        int charCount = (bitCount + 4) / 5;
        char[] out = new char[charCount];
        for (int g = 0; g < charCount; g++) {
            int val = 0;
            int baseBit = g * 5;
            for (int b = 0; b < 5; b++) {
                val <<= 1;
                int bitIdx = baseBit + b;
                if (bitIdx < bitCount) {
                    int byteVal = data[bitIdx >>> 3] & 0xFF;
                    int bit = (byteVal >>> (7 - (bitIdx & 7))) & 1;
                    val |= bit;
                }
            }
            out[g] = ENCODE_TABLE[val];
        }
        return new String(out);
    }

    /**
     * Decodes a byte-aligned z-base-32 string (case-insensitive).
     */
    public byte[] decode(String encoded) {
        Objects.requireNonNull(encoded, "encoded must not be null");
        int len = encoded.length();
        if (len == 0) {
            return new byte[0];
        }
        int mod = len & 7;
        if (mod == 1 || mod == 3 || mod == 6) {
            throw new ZBase32FormatException("Invalid z-base-32 byte-aligned length: " + len);
        }
        int byteCount = (len * 5) >>> 3;
        return decodeBits(encoded, byteCount * 8);
    }

    /**
     * Decodes a z-base-32 string representing exactly {@code bitCount} bits (MSB-first).
     * Unused trailing bits in the final output byte are zero-filled.
     */
    public byte[] decodeBits(String encoded, int bitCount) {
        Objects.requireNonNull(encoded, "encoded must not be null");
        if (bitCount < 0) {
            throw new IllegalArgumentException("bitCount must be >= 0, got: " + bitCount);
        }
        int expectedChars = (bitCount == 0) ? 0 : (bitCount + 4) / 5;
        if (encoded.length() != expectedChars) {
            throw new ZBase32FormatException(
                    "Expected " + expectedChars + " z-base-32 characters for " + bitCount
                            + " bits, got " + encoded.length());
        }
        if (bitCount == 0) {
            return new byte[0];
        }

        byte[] out = new byte[(bitCount + 7) >>> 3];
        for (int i = 0; i < expectedChars; i++) {
            char ch = encoded.charAt(i);
            int val = (ch < 128) ? DECODE_TABLE[ch] : -1;
            if (val < 0) {
                throw new ZBase32FormatException("Invalid z-base-32 character '" + ch + "' at index " + i);
            }
            int baseBit = i * 5;
            for (int b = 0; b < 5; b++) {
                int bit = (val >>> (4 - b)) & 1;
                int bitIdx = baseBit + b;
                if (bitIdx < bitCount) {
                    if (bit != 0) {
                        out[bitIdx >>> 3] |= (byte) (1 << (7 - (bitIdx & 7)));
                    }
                } else if (strict && bit != 0) {
                    throw new ZBase32FormatException(
                            "Non-zero trailing padding bit at character index " + i);
                }
            }
        }
        return out;
    }

    /**
     * Returns {@code true} if {@code encoded} is a valid byte-aligned z-base-32 string under this codec's rules.
     */
    public boolean isValid(String encoded) {
        if (encoded == null) {
            return false;
        }
        try {
            decode(encoded);
            return true;
        } catch (ZBase32FormatException ex) {
            return false;
        }
    }
}
