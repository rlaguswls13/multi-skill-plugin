package com.msp.base62;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * High-performance bidirectional Base62 codec using standard alphanumeric alphabet (0-9, a-z, A-Z).
 *
 * <p>Designed for URL Shorteners, nano IDs, BigInteger arithmetic, raw byte arrays,
 * and 22-character fixed-length UUID representations.</p>
 */
public final class Base62Codec {

    private static final String ALPHABET_STR = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final char[] ALPHABET = ALPHABET_STR.toCharArray();
    private static final int BASE = 62;
    private static final BigInteger BASE_BI = BigInteger.valueOf(BASE);

    private static final int[] REVERSE_LOOKUP = new int[128];

    static {
        Arrays.fill(REVERSE_LOOKUP, -1);
        for (int i = 0; i < ALPHABET.length; i++) {
            REVERSE_LOOKUP[ALPHABET[i]] = i;
        }
    }

    private Base62Codec() {}

    private static int charToValue(char ch) {
        if (ch >= 128 || REVERSE_LOOKUP[ch] == -1) {
            throw new IllegalArgumentException("Invalid Base62 character: '" + ch + "' (ASCII " + (int) ch + ")");
        }
        return REVERSE_LOOKUP[ch];
    }

    // --- Primitive long methods ---

    /**
     * Encodes a non-negative long value to Base62.
     */
    public static String encode(long number) {
        if (number < 0) {
            throw new IllegalArgumentException("Number must be non-negative: " + number);
        }
        if (number == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        long n = number;
        while (n > 0) {
            int rem = (int) (n % BASE);
            sb.append(ALPHABET[rem]);
            n /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string back to a primitive non-negative long.
     */
    public static long decodeToLong(String base62) {
        Objects.requireNonNull(base62, "Input string must not be null");
        if (base62.isEmpty()) {
            throw new IllegalArgumentException("Input string must not be empty");
        }
        long result = 0;
        long maxSafe = Long.MAX_VALUE / BASE;

        for (int i = 0; i < base62.length(); i++) {
            int digit = charToValue(base62.charAt(i));
            if (result > maxSafe) {
                throw new ArithmeticException("Base62 value exceeds Long.MAX_VALUE: " + base62);
            }
            long next = result * BASE + digit;
            if (next < 0) {
                throw new ArithmeticException("Base62 value overflowed long: " + base62);
            }
            result = next;
        }
        return result;
    }

    // --- BigInteger methods ---

    /**
     * Encodes an arbitrary-precision non-negative BigInteger to Base62.
     */
    public static String encode(BigInteger number) {
        Objects.requireNonNull(number, "BigInteger must not be null");
        if (number.signum() < 0) {
            throw new IllegalArgumentException("BigInteger must be non-negative: " + number);
        }
        if (number.equals(BigInteger.ZERO)) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        BigInteger current = number;
        while (current.signum() > 0) {
            BigInteger[] divRem = current.divideAndRemainder(BASE_BI);
            sb.append(ALPHABET[divRem[1].intValue()]);
            current = divRem[0];
        }
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string to a BigInteger.
     */
    public static BigInteger decodeToBigInteger(String base62) {
        Objects.requireNonNull(base62, "Input string must not be null");
        if (base62.isEmpty()) {
            throw new IllegalArgumentException("Input string must not be empty");
        }
        BigInteger result = BigInteger.ZERO;
        for (int i = 0; i < base62.length(); i++) {
            int digit = charToValue(base62.charAt(i));
            result = result.multiply(BASE_BI).add(BigInteger.valueOf(digit));
        }
        return result;
    }

    // --- Byte array methods ---

    /**
     * Encodes a raw byte array to Base62, preserving leading zero bytes as '0' characters.
     */
    public static String encodeBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "Byte array must not be null");
        if (bytes.length == 0) {
            return "";
        }
        // Count leading zero bytes
        int zeroCount = 0;
        while (zeroCount < bytes.length && bytes[zeroCount] == 0) {
            zeroCount++;
        }
        if (zeroCount == bytes.length) {
            return "0".repeat(zeroCount);
        }

        BigInteger bi = new BigInteger(1, bytes);
        String encoded = encode(bi);
        if (zeroCount > 0) {
            return "0".repeat(zeroCount) + encoded;
        }
        return encoded;
    }

    /**
     * Decodes a Base62 string back to a raw byte array, preserving leading zero bytes.
     */
    public static byte[] decodeBytes(String base62) {
        Objects.requireNonNull(base62, "Input string must not be null");
        if (base62.isEmpty()) {
            return new byte[0];
        }
        // Count leading '0' characters
        int zeroCount = 0;
        while (zeroCount < base62.length() && base62.charAt(zeroCount) == '0') {
            zeroCount++;
        }
        if (zeroCount == base62.length()) {
            return new byte[zeroCount];
        }

        BigInteger bi = decodeToBigInteger(base62);
        byte[] biBytes = bi.toByteArray();

        // Strip leading sign byte if present
        int startOffset = 0;
        if (biBytes.length > 1 && biBytes[0] == 0) {
            startOffset = 1;
        }
        int effectiveBiLen = biBytes.length - startOffset;

        byte[] result = new byte[zeroCount + effectiveBiLen];
        System.arraycopy(biBytes, startOffset, result, zeroCount, effectiveBiLen);
        return result;
    }

    // --- UUID methods ---

    /**
     * Encodes a standard 128-bit UUID into a fixed 22-character Base62 string.
     */
    public static String encodeUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID must not be null");
        byte[] bytes = new byte[16];
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());

        BigInteger bi = new BigInteger(1, bytes);
        String encoded = encode(bi);
        if (encoded.length() < 22) {
            return "0".repeat(22 - encoded.length()) + encoded;
        }
        return encoded;
    }

    /**
     * Decodes a 22-character Base62 string back to a standard 128-bit UUID.
     */
    public static UUID decodeUuid(String base62) {
        Objects.requireNonNull(base62, "Base62 string must not be null");
        if (base62.length() > 22) {
            throw new IllegalArgumentException("Base62 UUID string cannot exceed 22 characters: " + base62.length());
        }
        BigInteger bi = decodeToBigInteger(base62);
        byte[] biBytes = bi.toByteArray();

        byte[] bytes16 = new byte[16];
        int startOffset = (biBytes.length > 1 && biBytes[0] == 0) ? 1 : 0;
        int effectiveLen = biBytes.length - startOffset;
        if (effectiveLen > 16) {
            throw new IllegalArgumentException("Base62 value exceeds 128-bit UUID capacity");
        }

        System.arraycopy(biBytes, startOffset, bytes16, 16 - effectiveLen, effectiveLen);
        ByteBuffer bb = ByteBuffer.wrap(bytes16);
        return new UUID(bb.getLong(), bb.getLong());
    }
}
