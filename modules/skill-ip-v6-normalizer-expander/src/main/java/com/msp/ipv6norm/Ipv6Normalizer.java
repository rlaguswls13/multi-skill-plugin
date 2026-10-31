package com.msp.ipv6norm;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure Java 21 IPv6 address normalizer, full expander, and RFC 5952 standard compressor.
 * <p>
 * Supports:
 * <ul>
 *     <li>Complete 39-character 8-block zero-padded expansion (e.g. {@code 2001:0db8:0000:0000:0000:ff00:0042:8329})</li>
 *     <li>RFC 5952 canonical representation compression (longest zero sequence, single zero uncompressed, lowercase)</li>
 *     <li>IPv4-mapped IPv6 address detection ({@code ::ffff:x.x.x.x})</li>
 *     <li>Extraction of embedded IPv4 address string</li>
 *     <li>Strict validation without third-party network libraries</li>
 * </ul>
 */
public final class Ipv6Normalizer {

    private Ipv6Normalizer() {
    }

    /**
     * Checks whether the provided string is a valid IPv6 address.
     *
     * @param ipv6 string to test
     * @return {@code true} if valid IPv6, {@code false} otherwise
     */
    public static boolean isValid(String ipv6) {
        if (ipv6 == null) {
            return false;
        }
        try {
            parseBlocks(ipv6);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Fully expands an IPv6 address into its canonical 39-character 8-block lowercase representation.
     * (e.g. {@code 2001:db8::1} -> {@code 2001:0db8:0000:0000:0000:0000:0000:0001}).
     *
     * @param ipv6 IPv6 address string
     * @return 39-character lowercase expanded IPv6 string
     * @throws IllegalArgumentException if IPv6 address is invalid
     */
    public static String expand(String ipv6) {
        int[] blocks = parseBlocks(ipv6);
        return String.format("%04x:%04x:%04x:%04x:%04x:%04x:%04x:%04x",
                blocks[0], blocks[1], blocks[2], blocks[3],
                blocks[4], blocks[5], blocks[6], blocks[7]);
    }

    /**
     * Compresses an IPv6 address according to RFC 5952 standard representation rules:
     * <ul>
     *     <li>Leading zeros in each 16-bit block are suppressed</li>
     *     <li>The longest run of consecutive 16-bit zero blocks is shortened with {@code ::}</li>
     *     <li>A single zero block is NOT compressed with {@code ::}</li>
     *     <li>In case of a tie between multiple longest runs of equal length, the first one is shortened</li>
     *     <li>Characters a-f are formatted in lowercase</li>
     * </ul>
     *
     * @param ipv6 IPv6 address string
     * @return canonical compressed RFC 5952 string
     * @throws IllegalArgumentException if IPv6 address is invalid
     */
    public static String compress(String ipv6) {
        int[] blocks = parseBlocks(ipv6);

        // Find longest sequence of zero blocks
        int bestStart = -1;
        int bestLen = 0;
        int curStart = -1;
        int curLen = 0;

        for (int i = 0; i < 8; i++) {
            if (blocks[i] == 0) {
                if (curStart == -1) {
                    curStart = i;
                    curLen = 1;
                } else {
                    curLen++;
                }
                if (curLen > bestLen) {
                    bestStart = curStart;
                    bestLen = curLen;
                }
            } else {
                curStart = -1;
                curLen = 0;
            }
        }

        // RFC 5952 Section 4.2.2: The symbol '::' MUST NOT be used to shorten just one 16-bit 0 field
        if (bestLen < 2) {
            StringBuilder sb = new StringBuilder(39);
            for (int i = 0; i < 8; i++) {
                if (i > 0) {
                    sb.append(':');
                }
                sb.append(Integer.toHexString(blocks[i]));
            }
            return sb.toString();
        }

        // Entire address is all zeros (::)
        if (bestLen == 8) {
            return "::";
        }

        StringBuilder sb = new StringBuilder(39);
        for (int i = 0; i < 8; ) {
            if (i == bestStart) {
                sb.append("::");
                i += bestLen;
            } else {
                if (i > 0 && sb.charAt(sb.length() - 1) != ':') {
                    sb.append(':');
                }
                sb.append(Integer.toHexString(blocks[i]));
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * Checks if the IPv6 address is an IPv4-mapped address ({@code ::ffff:x.x.x.x} or
     * {@code 0000:0000:0000:0000:0000:ffff:xxxx:xxxx}).
     *
     * @param ipv6 IPv6 address string
     * @return {@code true} if IPv4-mapped, {@code false} otherwise
     */
    public static boolean isIpv4Mapped(String ipv6) {
        if (!isValid(ipv6)) {
            return false;
        }
        int[] blocks = parseBlocks(ipv6);
        return blocks[0] == 0 && blocks[1] == 0 && blocks[2] == 0
                && blocks[3] == 0 && blocks[4] == 0 && blocks[5] == 0xFFFF;
    }

    /**
     * Extracts the embedded IPv4 address in dot-decimal notation (e.g. "192.168.1.1").
     *
     * @param ipv6 IPv6 address string
     * @return IPv4 dot-decimal string
     * @throws IllegalArgumentException if the address is not an IPv4-mapped IPv6 address
     */
    public static String extractIpv4(String ipv6) {
        if (!isIpv4Mapped(ipv6)) {
            throw new IllegalArgumentException("Not an IPv4-mapped IPv6 address: " + ipv6);
        }
        int[] blocks = parseBlocks(ipv6);
        int b6 = blocks[6];
        int b7 = blocks[7];
        return ((b6 >>> 8) & 0xFF) + "." + (b6 & 0xFF) + "." + ((b7 >>> 8) & 0xFF) + "." + (b7 & 0xFF);
    }

    /**
     * Finds the embedded IPv4 address if present as IPv4-mapped.
     *
     * @param ipv6 IPv6 address string
     * @return Optional containing IPv4 string, or empty if not IPv4-mapped
     */
    public static Optional<String> findIpv4(String ipv6) {
        if (!isIpv4Mapped(ipv6)) {
            return Optional.empty();
        }
        return Optional.of(extractIpv4(ipv6));
    }

    /**
     * Parses the IPv6 string into 8 16-bit integer blocks.
     */
    private static int[] parseBlocks(String ipv6) {
        if (ipv6 == null) {
            throw new IllegalArgumentException("IPv6 address cannot be null");
        }
        String s = ipv6.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("IPv6 address cannot be empty");
        }

        // Strip enclosing square brackets if present
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1).trim();
        }

        // Strip zone index (e.g. %eth0 or %1)
        int percentIdx = s.indexOf('%');
        if (percentIdx != -1) {
            s = s.substring(0, percentIdx).trim();
        }

        // Check for embedded IPv4 at the end (e.g. ::ffff:192.0.2.1)
        int lastColon = s.lastIndexOf(':');
        if (lastColon == -1) {
            throw new IllegalArgumentException("Invalid IPv6 address (no colons): " + ipv6);
        }

        String suffix = s.substring(lastColon + 1);
        if (suffix.contains(".")) {
            // Embedded IPv4 address
            int[] v4Blocks = parseIpv4ToBlocks(suffix);
            String prefix = s.substring(0, lastColon);
            if (prefix.endsWith(":")) {
                // Was "::192.0.2.1"
                s = prefix + Integer.toHexString(v4Blocks[0]) + ":" + Integer.toHexString(v4Blocks[1]);
            } else {
                s = prefix + ":" + Integer.toHexString(v4Blocks[0]) + ":" + Integer.toHexString(v4Blocks[1]);
            }
        }

        // Parse regular IPv6
        int doubleColonIdx = s.indexOf("::");
        if (doubleColonIdx != -1) {
            // Cannot have multiple "::"
            if (s.indexOf("::", doubleColonIdx + 2) != -1) {
                throw new IllegalArgumentException("Multiple '::' detected in IPv6: " + ipv6);
            }

            String left = s.substring(0, doubleColonIdx);
            String right = s.substring(doubleColonIdx + 2);

            String[] leftParts = left.isEmpty() ? new String[0] : left.split(":", -1);
            String[] rightParts = right.isEmpty() ? new String[0] : right.split(":", -1);

            int totalParts = leftParts.length + rightParts.length;
            if (totalParts > 7) {
                throw new IllegalArgumentException("Too many blocks in IPv6 with '::': " + ipv6);
            }

            int[] blocks = new int[8];
            for (int i = 0; i < leftParts.length; i++) {
                blocks[i] = parseHexBlock(leftParts[i]);
            }

            int fillCount = 8 - totalParts;
            for (int i = 0; i < rightParts.length; i++) {
                blocks[leftParts.length + fillCount + i] = parseHexBlock(rightParts[i]);
            }
            return blocks;
        } else {
            String[] parts = s.split(":", -1);
            if (parts.length != 8) {
                throw new IllegalArgumentException("IPv6 address without '::' must have exactly 8 blocks: " + ipv6);
            }
            int[] blocks = new int[8];
            for (int i = 0; i < 8; i++) {
                blocks[i] = parseHexBlock(parts[i]);
            }
            return blocks;
        }
    }

    private static int parseHexBlock(String part) {
        if (part == null || part.isEmpty() || part.length() > 4) {
            throw new IllegalArgumentException("Invalid IPv6 block length: '" + part + "'");
        }
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!isHex) {
                throw new IllegalArgumentException("Non-hex character in IPv6 block: '" + part + "'");
            }
        }
        return Integer.parseInt(part, 16);
    }

    private static int[] parseIpv4ToBlocks(String ipv4) {
        String[] octets = ipv4.split("\\.", -1);
        if (octets.length != 4) {
            throw new IllegalArgumentException("Invalid embedded IPv4 address: " + ipv4);
        }
        int[] vals = new int[4];
        for (int i = 0; i < 4; i++) {
            String o = octets[i];
            if (o.isEmpty() || (o.length() > 1 && o.startsWith("0"))) {
                throw new IllegalArgumentException("Invalid leading zero or empty IPv4 octet: " + o);
            }
            for (int j = 0; j < o.length(); j++) {
                if (!Character.isDigit(o.charAt(j))) {
                    throw new IllegalArgumentException("Non-digit in IPv4 octet: " + o);
                }
            }
            int val = Integer.parseInt(o);
            if (val < 0 || val > 255) {
                throw new IllegalArgumentException("IPv4 octet out of range (0-255): " + val);
            }
            vals[i] = val;
        }

        int b1 = (vals[0] << 8) | vals[1];
        int b2 = (vals[2] << 8) | vals[3];
        return new int[]{b1, b2};
    }
}
