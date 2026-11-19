package com.msp.tlvcodec;

import java.util.Objects;

/**
 * Utility for hexadecimal encoding/decoding and indented BER-TLV tree hex dump formatting.
 */
public final class TlvHexFormatter {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private TlvHexFormatter() {
    }

    /**
     * Converts a byte array into an uppercase hexadecimal string.
     */
    public static String toHex(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    /**
     * Parses a hexadecimal string (ignoring whitespace) into a byte array.
     */
    public static byte[] fromHex(String hex) {
        Objects.requireNonNull(hex, "hex must not be null");
        StringBuilder cleaned = new StringBuilder(hex.length());
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (!Character.isWhitespace(c)) {
                cleaned.append(c);
            }
        }
        if ((cleaned.length() & 1) != 0) {
            throw new TlvCodecException("Hex string must have an even number of characters: " + hex);
        }
        byte[] out = new byte[cleaned.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(cleaned.charAt(i * 2), 16);
            int lo = Character.digit(cleaned.charAt(i * 2 + 1), 16);
            if (hi == -1 || lo == -1) {
                throw new TlvCodecException("Invalid hex character in: " + hex);
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /**
     * Formats a {@link TlvNode} hierarchy as an indented human-readable hex dump.
     */
    public static String formatDump(TlvNode root) {
        Objects.requireNonNull(root, "root must not be null");
        StringBuilder sb = new StringBuilder();
        root.accept(new TlvVisitor() {
            @Override
            public void visitPrimitive(PrimitiveTlv node, int depth) {
                indent(sb, depth);
                sb.append(node.tag().toHex())
                        .append(" [").append(node.tag().tagClass().name()).append(",P] ")
                        .append("len=").append(node.length())
                        .append(" val=").append(node.valueHex())
                        .append('\n');
            }

            @Override
            public void enterConstructed(ConstructedTlv node, int depth) {
                indent(sb, depth);
                sb.append(node.tag().toHex())
                        .append(" [").append(node.tag().tagClass().name()).append(",C] ")
                        .append("children=").append(node.children().size())
                        .append('\n');
            }
        });
        return sb.toString();
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
    }
}
