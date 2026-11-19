package com.msp.paseto;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

/**
 * PASETO Pre-Authentication Encoding (PAE) encoder.
 *
 * <p>Encodes a sequence of byte arrays as:
 * {@code LE64(count) || LE64(len(piece_0)) || piece_0 || ... || LE64(len(piece_{n-1})) || piece_{n-1}}
 * preventing canonicalization and boundary-ambiguity attacks.
 */
public final class PaeEncoder {

    private PaeEncoder() {
    }

    /**
     * Encodes the given byte array pieces using PASETO PAE.
     */
    public static byte[] encode(byte[]... pieces) {
        Objects.requireNonNull(pieces, "pieces must not be null");
        int totalSize = 8;
        for (byte[] piece : pieces) {
            Objects.requireNonNull(piece, "PAE piece must not be null");
            totalSize += 8 + piece.length;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(totalSize);
        writeLe64(out, pieces.length);
        for (byte[] piece : pieces) {
            writeLe64(out, piece.length);
            out.writeBytes(piece);
        }
        return out.toByteArray();
    }

    private static void writeLe64(ByteArrayOutputStream out, long value) {
        long v = value & 0x7FFFFFFFFFFFFFFFL; // MSB cleared per PASETO spec
        for (int i = 0; i < 8; i++) {
            out.write((int) (v & 0xFFL));
            v >>>= 8;
        }
    }
}
