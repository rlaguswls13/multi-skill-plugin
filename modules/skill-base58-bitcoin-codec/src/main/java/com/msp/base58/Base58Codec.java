package com.msp.base58;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Standard Bitcoin and IPFS Base58 and Base58Check codec.
 * <p>
 * Base58 Alphabet: {@code 123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz}
 * (excluding 0, O, I, l to avoid visual ambiguity).
 * <p>
 * Thread-safe, zero-dependency, pure Java 21 implementation.
 */
public final class Base58Codec {

    public static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final int[] INDEXES = new int[128];

    static {
        Arrays.fill(INDEXES, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            INDEXES[ALPHABET.charAt(i)] = i;
        }
    }

    private Base58Codec() {
        // Utility class
    }

    /**
     * Encodes byte array into a Base58 string.
     * Leading zero bytes (0x00) are preserved as leading '1' characters.
     *
     * @param input byte array to encode
     * @return Base58 encoded string
     * @throws IllegalArgumentException if input is null
     */
    public static String encode(byte[] input) {
        if (input == null) {
            throw new IllegalArgumentException("Input bytes cannot be null");
        }
        if (input.length == 0) {
            return "";
        }

        // Count leading zeros
        int zeroCount = 0;
        while (zeroCount < input.length && input[zeroCount] == 0) {
            zeroCount++;
        }

        // Copy input to prevent mutation
        byte[] temp = Arrays.copyOf(input, input.length);
        char[] encoded = new char[input.length * 2];
        int outputStart = encoded.length;

        int inputStart = zeroCount;
        while (inputStart < temp.length) {
            int remainder = 0;
            for (int i = inputStart; i < temp.length; i++) {
                int digit = (temp[i] & 0xFF) + remainder * 256;
                temp[i] = (byte) (digit / 58);
                remainder = digit % 58;
            }
            if (temp[inputStart] == 0) {
                inputStart++;
            }
            encoded[--outputStart] = ALPHABET.charAt(remainder);
        }

        // Prepend '1' for each leading zero byte
        while (--zeroCount >= 0) {
            encoded[--outputStart] = '1';
        }

        return new String(encoded, outputStart, encoded.length - outputStart);
    }

    /**
     * Decodes Base58 string into byte array.
     * Leading '1' characters are decoded as leading zero bytes (0x00).
     *
     * @param input Base58 encoded string
     * @return decoded byte array
     * @throws IllegalArgumentException if input is null or contains invalid Base58 characters
     */
    public static byte[] decode(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input string cannot be null");
        }
        if (input.isEmpty()) {
            return new byte[0];
        }

        // Count leading '1's
        int zeroCount = 0;
        while (zeroCount < input.length() && input.charAt(zeroCount) == '1') {
            zeroCount++;
        }

        byte[] decoded = new byte[input.length()];
        int outputStart = decoded.length;

        for (int i = zeroCount; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 128 || INDEXES[c] == -1) {
                throw new IllegalArgumentException("Invalid Base58 character: '" + c + "' at index " + i);
            }
            int carry = INDEXES[c];
            for (int j = decoded.length - 1; j >= outputStart; j--) {
                int digit = (decoded[j] & 0xFF) * 58 + carry;
                decoded[j] = (byte) digit;
                carry = digit >>> 8;
            }
            while (carry > 0) {
                decoded[--outputStart] = (byte) carry;
                carry >>>= 8;
            }
        }

        // Ignore leading zero padding added during decoding
        while (outputStart < decoded.length && decoded[outputStart] == 0) {
            outputStart++;
        }

        byte[] result = new byte[zeroCount + (decoded.length - outputStart)];
        System.arraycopy(decoded, outputStart, result, zeroCount, decoded.length - outputStart);
        return result;
    }

    /**
     * Encodes version and payload with 4-byte double SHA-256 checksum into Base58Check string.
     * Format: [1-byte version] + [payload] + [4-byte checksum]
     *
     * @param version 1-byte version (0-255)
     * @param payload payload byte array
     * @return Base58Check encoded string
     */
    public static String encodeChecked(int version, byte[] payload) {
        if (version < 0 || version > 255) {
            throw new IllegalArgumentException("Version must be 0-255: " + version);
        }
        Objects.requireNonNull(payload, "Payload cannot be null");

        byte[] data = new byte[1 + payload.length];
        data[0] = (byte) version;
        System.arraycopy(payload, 0, data, 1, payload.length);
        return encodeChecked(data);
    }

    /**
     * Encodes byte array with 4-byte double SHA-256 checksum into Base58Check string.
     * Format: [data] + [4-byte checksum]
     *
     * @param data byte array to encode
     * @return Base58Check encoded string
     */
    public static String encodeChecked(byte[] data) {
        Objects.requireNonNull(data, "Data cannot be null");
        byte[] checksum = doubleSha256(data);
        byte[] result = new byte[data.length + 4];
        System.arraycopy(data, 0, result, 0, data.length);
        System.arraycopy(checksum, 0, result, data.length, 4);
        return encode(result);
    }

    /**
     * Decodes Base58Check string, verifies the 4-byte checksum, and returns the payload (excluding version byte).
     *
     * @param input Base58Check encoded string
     * @return extracted payload bytes
     * @throws IllegalArgumentException if checksum fails, or length is less than 5 bytes
     */
    public static byte[] decodeChecked(String input) {
        return decodeCheckedWithVersion(input).payload();
    }

    /**
     * Decodes Base58Check string, verifies the 4-byte checksum, and returns both version and payload.
     *
     * @param input Base58Check encoded string
     * @return Base58CheckPayload record containing version and payload
     * @throws IllegalArgumentException if checksum fails or string is invalid
     */
    public static Base58CheckPayload decodeCheckedWithVersion(String input) {
        byte[] raw = decodeRawChecked(input);
        if (raw.length < 1) {
            throw new IllegalArgumentException("Base58Check data contains no version byte");
        }
        int version = raw[0] & 0xFF;
        byte[] payload = Arrays.copyOfRange(raw, 1, raw.length);
        return new Base58CheckPayload(version, payload);
    }

    /**
     * Decodes Base58Check string, verifies the 4-byte checksum, and returns all data preceding checksum.
     *
     * @param input Base58Check encoded string
     * @return raw data bytes before the 4-byte checksum
     * @throws IllegalArgumentException if checksum fails or length is less than 4 bytes
     */
    public static byte[] decodeRawChecked(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input string cannot be null");
        }
        byte[] raw = decode(input);
        if (raw.length < 4) {
            throw new IllegalArgumentException("Base58Check input too short: length is " + raw.length);
        }
        int dataLength = raw.length - 4;
        byte[] data = Arrays.copyOfRange(raw, 0, dataLength);
        byte[] actualChecksum = Arrays.copyOfRange(raw, dataLength, raw.length);
        byte[] expectedHash = doubleSha256(data);
        byte[] expectedChecksum = Arrays.copyOfRange(expectedHash, 0, 4);

        if (!MessageDigest.isEqual(actualChecksum, expectedChecksum)) {
            throw new IllegalArgumentException("Invalid Base58Check checksum");
        }
        return data;
    }

    /**
     * Computes double SHA-256 hash: {@code SHA-256(SHA-256(data))}.
     */
    public static byte[] doubleSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] first = digest.digest(data);
            return digest.digest(first);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available in JVM", e);
        }
    }
}
