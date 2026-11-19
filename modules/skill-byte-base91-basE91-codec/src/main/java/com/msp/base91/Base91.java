package com.msp.base91;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Standard basE91 Binary-to-ASCII bidirectional codec.
 * Encodes arbitrary binary data into 91 printable ASCII characters,
 * achieving ~1.23x expansion compared to Base64's ~1.33x.
 */
public final class Base91 {

    public static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
            "abcdefghijklmnopqrstuvwxyz" +
            "0123456789" +
            "!#$%&()*+,./:;<=>?@[]^_`{|}~\"";

    public static final byte[] ENCODING_TABLE = ALPHABET.getBytes(StandardCharsets.US_ASCII);

    private static final int[] DECODING_TABLE = new int[256];

    static {
        Arrays.fill(DECODING_TABLE, -1);
        for (int i = 0; i < ENCODING_TABLE.length; i++) {
            DECODING_TABLE[ENCODING_TABLE[i] & 0xFF] = i;
        }
    }

    private Base91() {}

    /**
     * Encodes a byte array into a basE91 ASCII string.
     *
     * @param data binary input data
     * @return basE91 encoded string
     */
    public static String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        return new String(encodeToBytes(data), StandardCharsets.US_ASCII);
    }

    /**
     * Encodes a byte array into basE91 ASCII bytes.
     */
    public static byte[] encodeToBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.ceil(data.length * 1.25) + 4);
        int queue = 0;
        int nbits = 0;

        for (byte b : data) {
            queue |= (b & 0xFF) << nbits;
            nbits += 8;
            if (nbits > 13) {
                int value = queue & 8191; // 13 bits (0x1FFF)
                if (value > 88) {
                    queue >>>= 13;
                    nbits -= 13;
                } else {
                    value = queue & 16383; // 14 bits (0x3FFF)
                    queue >>>= 14;
                    nbits -= 14;
                }
                out.write(ENCODING_TABLE[value % 91]);
                out.write(ENCODING_TABLE[value / 91]);
            }
        }

        if (nbits > 0) {
            out.write(ENCODING_TABLE[queue % 91]);
            if (nbits > 7 || queue > 90) {
                out.write(ENCODING_TABLE[queue / 91]);
            }
        }

        return out.toByteArray();
    }

    /**
     * Decodes a basE91 string into original binary data.
     *
     * @param encoded basE91 string
     * @return decoded byte array
     */
    public static byte[] decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return new byte[0];
        }
        return decode(encoded.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Decodes basE91 ASCII bytes into original binary data.
     */
    public static byte[] decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0) {
            return new byte[0];
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream((int) (encoded.length * 0.85) + 4);
        int queue = 0;
        int nbits = 0;
        int value = -1;

        for (int i = 0; i < encoded.length; i++) {
            int b = encoded[i] & 0xFF;
            if (b >= DECODING_TABLE.length || DECODING_TABLE[b] == -1) {
                // Ignore whitespace characters (CR, LF, space, tab)
                if (b == ' ' || b == '\t' || b == '\r' || b == '\n') {
                    continue;
                }
                throw new IllegalArgumentException(String.format("Invalid basE91 character '0x%02X' (%c) at offset %d",
                        b, (char) b, i));
            }

            int tableIndex = DECODING_TABLE[b];
            if (value == -1) {
                value = tableIndex;
            } else {
                value += tableIndex * 91;
                queue |= value << nbits;
                nbits += (value & 8191) > 88 ? 13 : 14;
                do {
                    out.write((byte) queue);
                    queue >>>= 8;
                    nbits -= 8;
                } while (nbits > 7);
                value = -1;
            }
        }

        if (value != -1) {
            out.write((byte) (queue | (value << nbits)));
        }

        return out.toByteArray();
    }

    /**
     * Wraps an {@link InputStream} with a basE91 decoding stream.
     */
    public static InputStream wrap(InputStream in) {
        return new Base91InputStream(in);
    }

    /**
     * Wraps an {@link OutputStream} with a basE91 encoding stream.
     */
    public static OutputStream wrap(OutputStream out) {
        return new Base91OutputStream(out);
    }
}
