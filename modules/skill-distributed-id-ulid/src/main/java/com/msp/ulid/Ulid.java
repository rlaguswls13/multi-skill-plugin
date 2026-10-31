package com.msp.ulid;

import java.io.Serial;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Universally Unique Lexicographically Sortable Identifier (ULID).
 * <p>
 * Specifications:
 * <ul>
 *   <li>128-bit compatibility with UUID</li>
 *   <li>1.21e+24 unique ULIDs per millisecond</li>
 *   <li>Lexicographically sortable!</li>
 *   <li>Canonically encoded as a 26 character string, as opposed to the 36 character UUID</li>
 *   <li>Uses Crockford's Base32 for better efficiency and readability (5 bits per character)</li>
 *   <li>Case-insensitive with decoding ambiguity tolerance ('I', 'L' -> 1, 'O' -> 0)</li>
 *   <li>No special characters (URL safe)</li>
 * </ul>
 * </p>
 */
public final class Ulid implements Comparable<Ulid>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int STRING_LENGTH = 26;
    public static final int BYTE_LENGTH = 16;
    public static final long MAX_TIMESTAMP = 0x0000FFFFFFFFFFFFL; // 2^48 - 1 (Year 10889)

    private static final char[] ENCODING_CHARS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final byte[] DECODING_TABLE = new byte[128];

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    static {
        Arrays.fill(DECODING_TABLE, (byte) -1);
        for (int i = 0; i < ENCODING_CHARS.length; i++) {
            char c = ENCODING_CHARS[i];
            DECODING_TABLE[c] = (byte) i;
            DECODING_TABLE[Character.toLowerCase(c)] = (byte) i;
        }
        // Ambiguous character mappings per Crockford Base32
        DECODING_TABLE['i'] = 1;
        DECODING_TABLE['I'] = 1;
        DECODING_TABLE['l'] = 1;
        DECODING_TABLE['L'] = 1;
        DECODING_TABLE['o'] = 0;
        DECODING_TABLE['O'] = 0;
    }

    private final long mostSignificantBits;
    private final long leastSignificantBits;

    public Ulid(long mostSignificantBits, long leastSignificantBits) {
        this.mostSignificantBits = mostSignificantBits;
        this.leastSignificantBits = leastSignificantBits;
    }

    /**
     * Generates a new random ULID using current epoch milliseconds and {@link SecureRandom}.
     */
    public static Ulid random() {
        return fromTimestamp(System.currentTimeMillis());
    }

    /**
     * Generates a new ULID with the specified timestamp and cryptographically secure random entropy.
     *
     * @param timestampMs unix epoch milliseconds (0 to 2^48 - 1)
     */
    public static Ulid fromTimestamp(long timestampMs) {
        byte[] entropy = new byte[10];
        SECURE_RANDOM.nextBytes(entropy);
        return of(timestampMs, entropy);
    }

    /**
     * Creates a ULID from explicit timestamp and 10-byte (80-bit) entropy array.
     */
    public static Ulid of(long timestampMs, byte[] entropy) {
        if (timestampMs < 0 || timestampMs > MAX_TIMESTAMP) {
            throw new IllegalArgumentException("ULID timestamp must be between 0 and 2^48 - 1: " + timestampMs);
        }
        if (entropy == null) {
            throw new IllegalArgumentException("entropy must not be null");
        }
        if (entropy.length != 10) {
            throw new IllegalArgumentException("ULID entropy must be exactly 10 bytes, got: " + entropy.length);
        }

        long msb = (timestampMs << 16)
            | (((long) entropy[0] & 0xFF) << 8)
            | ((long) entropy[1] & 0xFF);

        long lsb = (((long) entropy[2] & 0xFF) << 56)
            | (((long) entropy[3] & 0xFF) << 48)
            | (((long) entropy[4] & 0xFF) << 40)
            | (((long) entropy[5] & 0xFF) << 32)
            | (((long) entropy[6] & 0xFF) << 24)
            | (((long) entropy[7] & 0xFF) << 16)
            | (((long) entropy[8] & 0xFF) << 8)
            | ((long) entropy[9] & 0xFF);

        return new Ulid(msb, lsb);
    }

    public static Ulid of(long mostSignificantBits, long leastSignificantBits) {
        return new Ulid(mostSignificantBits, leastSignificantBits);
    }

    /**
     * Parses a 26-character Crockford Base32 encoded ULID string.
     */
    public static Ulid fromString(String ulidString) {
        if (ulidString == null) {
            throw new IllegalArgumentException("ulidString must not be null");
        }
        if (ulidString.length() != STRING_LENGTH) {
            throw new IllegalArgumentException("ULID string must be exactly 26 characters: " + ulidString);
        }

        long[] values = new long[STRING_LENGTH];
        for (int i = 0; i < STRING_LENGTH; i++) {
            char c = ulidString.charAt(i);
            if (c >= 128) {
                throw new IllegalArgumentException("Invalid Crockford Base32 character: " + c);
            }
            int val = DECODING_TABLE[c];
            if (val == -1) {
                throw new IllegalArgumentException("Invalid Crockford Base32 character: " + c);
            }
            values[i] = val;
        }

        // The first character can only carry up to 3 bits (values 0..7) to fit in 128 bits
        if (values[0] > 7) {
            throw new IllegalArgumentException("ULID string value exceeds 128-bit maximum: " + ulidString);
        }

        long msb = (values[0] << 61)
            | (values[1] << 56)
            | (values[2] << 51)
            | (values[3] << 46)
            | (values[4] << 41)
            | (values[5] << 36)
            | (values[6] << 31)
            | (values[7] << 26)
            | (values[8] << 21)
            | (values[9] << 16)
            | (values[10] << 11)
            | (values[11] << 6)
            | (values[12] << 1)
            | (values[13] >>> 4);

        long lsb = (values[13] << 60)
            | (values[14] << 55)
            | (values[15] << 50)
            | (values[16] << 45)
            | (values[17] << 40)
            | (values[18] << 35)
            | (values[19] << 30)
            | (values[20] << 25)
            | (values[21] << 20)
            | (values[22] << 15)
            | (values[23] << 10)
            | (values[24] << 5)
            | values[25];

        return new Ulid(msb, lsb);
    }

    /**
     * Parses a 16-byte array into a ULID.
     */
    public static Ulid fromBytes(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        if (bytes.length != BYTE_LENGTH) {
            throw new IllegalArgumentException("ULID bytes must be exactly 16 bytes: " + bytes.length);
        }

        long msb = (((long) bytes[0] & 0xFF) << 56)
            | (((long) bytes[1] & 0xFF) << 48)
            | (((long) bytes[2] & 0xFF) << 40)
            | (((long) bytes[3] & 0xFF) << 32)
            | (((long) bytes[4] & 0xFF) << 24)
            | (((long) bytes[5] & 0xFF) << 16)
            | (((long) bytes[6] & 0xFF) << 8)
            | ((long) bytes[7] & 0xFF);

        long lsb = (((long) bytes[8] & 0xFF) << 56)
            | (((long) bytes[9] & 0xFF) << 48)
            | (((long) bytes[10] & 0xFF) << 40)
            | (((long) bytes[11] & 0xFF) << 32)
            | (((long) bytes[12] & 0xFF) << 24)
            | (((long) bytes[13] & 0xFF) << 16)
            | (((long) bytes[14] & 0xFF) << 8)
            | ((long) bytes[15] & 0xFF);

        return new Ulid(msb, lsb);
    }

    public static Ulid fromUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        return new Ulid(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }

    /**
     * Extracts the 48-bit timestamp (millisecond epoch).
     */
    public long getTimestamp() {
        return mostSignificantBits >>> 16;
    }

    public long getMostSignificantBits() {
        return mostSignificantBits;
    }

    public long getLeastSignificantBits() {
        return leastSignificantBits;
    }

    /**
     * Converts to 16-byte array representation.
     */
    public byte[] toBytes() {
        byte[] bytes = new byte[BYTE_LENGTH];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (mostSignificantBits >>> (56 - i * 8));
        }
        for (int i = 0; i < 8; i++) {
            bytes[8 + i] = (byte) (leastSignificantBits >>> (56 - i * 8));
        }
        return bytes;
    }

    /**
     * Converts to standard 128-bit {@link UUID}.
     */
    public UUID toUuid() {
        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    /**
     * Returns canonical 26-character Crockford Base32 string representation.
     */
    @Override
    public String toString() {
        char[] chars = new char[STRING_LENGTH];
        chars[0] = ENCODING_CHARS[(int) ((mostSignificantBits >>> 61) & 0x07)];
        chars[1] = ENCODING_CHARS[(int) ((mostSignificantBits >>> 56) & 0x1F)];
        chars[2] = ENCODING_CHARS[(int) ((mostSignificantBits >>> 51) & 0x1F)];
        chars[3] = ENCODING_CHARS[(int) ((mostSignificantBits >>> 46) & 0x1F)];
        chars[4] = ENCODING_CHARS[(int) ((mostSignificantBits >>> 41) & 0x1F)];
        chars[5] = ENCODING_CHARS[(int) ((mostSignificantBits >>> 36) & 0x1F)];
        chars[6] = ENCODING_CHARS[(int) ((mostSignificantBits >>> 31) & 0x1F)];
        chars[7] = ENCODING_CHARS[(int) ((mostSignificantBits >>> 26) & 0x1F)];
        chars[8] = ENCODING_CHARS[(int) ((mostSignificantBits >>> 21) & 0x1F)];
        chars[9] = ENCODING_CHARS[(int) ((mostSignificantBits >>> 16) & 0x1F)];
        chars[10] = ENCODING_CHARS[(int) ((mostSignificantBits >>> 11) & 0x1F)];
        chars[11] = ENCODING_CHARS[(int) ((mostSignificantBits >>> 6) & 0x1F)];
        chars[12] = ENCODING_CHARS[(int) ((mostSignificantBits >>> 1) & 0x1F)];
        chars[13] = ENCODING_CHARS[(int) (((mostSignificantBits << 4) | (leastSignificantBits >>> 60)) & 0x1F)];
        chars[14] = ENCODING_CHARS[(int) ((leastSignificantBits >>> 55) & 0x1F)];
        chars[15] = ENCODING_CHARS[(int) ((leastSignificantBits >>> 50) & 0x1F)];
        chars[16] = ENCODING_CHARS[(int) ((leastSignificantBits >>> 45) & 0x1F)];
        chars[17] = ENCODING_CHARS[(int) ((leastSignificantBits >>> 40) & 0x1F)];
        chars[18] = ENCODING_CHARS[(int) ((leastSignificantBits >>> 35) & 0x1F)];
        chars[19] = ENCODING_CHARS[(int) ((leastSignificantBits >>> 30) & 0x1F)];
        chars[20] = ENCODING_CHARS[(int) ((leastSignificantBits >>> 25) & 0x1F)];
        chars[21] = ENCODING_CHARS[(int) ((leastSignificantBits >>> 20) & 0x1F)];
        chars[22] = ENCODING_CHARS[(int) ((leastSignificantBits >>> 15) & 0x1F)];
        chars[23] = ENCODING_CHARS[(int) ((leastSignificantBits >>> 10) & 0x1F)];
        chars[24] = ENCODING_CHARS[(int) ((leastSignificantBits >>> 5) & 0x1F)];
        chars[25] = ENCODING_CHARS[(int) (leastSignificantBits & 0x1F)];
        return new String(chars);
    }

    @Override
    public int compareTo(Ulid other) {
        if (this == other) return 0;
        int cmp = Long.compareUnsigned(this.mostSignificantBits, other.mostSignificantBits);
        if (cmp != 0) return cmp;
        return Long.compareUnsigned(this.leastSignificantBits, other.leastSignificantBits);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ulid ulid)) return false;
        return mostSignificantBits == ulid.mostSignificantBits
            && leastSignificantBits == ulid.leastSignificantBits;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mostSignificantBits, leastSignificantBits);
    }
}
