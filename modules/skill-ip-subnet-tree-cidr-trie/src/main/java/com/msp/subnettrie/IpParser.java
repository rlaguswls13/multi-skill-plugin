package com.msp.subnettrie;

import java.util.Arrays;

/**
 * Robust IPv4 and IPv6 address parser converting string representations
 * into canonical byte arrays without any network or DNS lookups.
 */
public final class IpParser {

    private IpParser() {
    }

    public record ParsedIp(byte[] bytes, boolean isIpv6) {
        public ParsedIp {
            if (isIpv6 && bytes.length != 16) {
                throw new IllegalArgumentException("IPv6 address must be 16 bytes");
            }
            if (!isIpv6 && bytes.length != 4) {
                throw new IllegalArgumentException("IPv4 address must be 4 bytes");
            }
        }

        public int getBit(int index) {
            int byteIndex = index / 8;
            int bitOffset = 7 - (index % 8);
            return (bytes[byteIndex] >>> bitOffset) & 1;
        }
    }

    public static ParsedIp parseIp(String ipStr) {
        if (ipStr == null || ipStr.isBlank()) {
            throw new IllegalArgumentException("IP address string must not be null or blank");
        }
        String clean = ipStr.trim();
        if (clean.contains(":")) {
            return new ParsedIp(parseIpv6(clean), true);
        } else if (clean.contains(".")) {
            return new ParsedIp(parseIpv4(clean), false);
        }
        throw new IllegalArgumentException("Invalid IP address format: " + ipStr);
    }

    public static byte[] parseIpv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + s);
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            try {
                int val = Integer.parseInt(parts[i]);
                if (val < 0 || val > 255) {
                    throw new IllegalArgumentException("IPv4 octet out of range [0-255]: " + parts[i]);
                }
                bytes[i] = (byte) val;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid IPv4 octet: " + parts[i], e);
            }
        }
        return bytes;
    }

    public static byte[] parseIpv6(String s) {
        // Strip optional zone index (e.g. fe80::1%eth0)
        int zoneIdx = s.indexOf('%');
        if (zoneIdx >= 0) {
            s = s.substring(0, zoneIdx);
        }

        // Check for embedded IPv4 (e.g. ::ffff:192.168.1.1)
        int lastColon = s.lastIndexOf(':');
        byte[] embeddedIpv4 = null;
        if (lastColon >= 0 && s.indexOf('.', lastColon) > 0) {
            String ipv4Part = s.substring(lastColon + 1);
            embeddedIpv4 = parseIpv4(ipv4Part);
            s = s.substring(0, lastColon + 1) + "0:0"; // placeholder
        }

        String[] parts = s.split("::", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException("Multiple '::' occurrences in IPv6 address: " + s);
        }

        short[] words = new short[8];

        if (parts.length == 1) {
            // No compression
            String[] tokens = parts[0].split(":", -1);
            if (tokens.length != 8) {
                throw new IllegalArgumentException("Invalid IPv6 address (expected 8 groups): " + s);
            }
            for (int i = 0; i < 8; i++) {
                words[i] = parseHexWord(tokens[i]);
            }
        } else {
            // Compressed with '::'
            String leftPart = parts[0];
            String rightPart = parts[1];

            String[] leftTokens = leftPart.isEmpty() ? new String[0] : leftPart.split(":", -1);
            String[] rightTokens = rightPart.isEmpty() ? new String[0] : rightPart.split(":", -1);

            if (leftTokens.length + rightTokens.length > 7) {
                throw new IllegalArgumentException("Too many groups in compressed IPv6 address: " + s);
            }

            int idx = 0;
            for (String token : leftTokens) {
                words[idx++] = parseHexWord(token);
            }
            idx = 8 - rightTokens.length;
            for (String token : rightTokens) {
                words[idx++] = parseHexWord(token);
            }
        }

        if (embeddedIpv4 != null) {
            words[6] = (short) (((embeddedIpv4[0] & 0xFF) << 8) | (embeddedIpv4[1] & 0xFF));
            words[7] = (short) (((embeddedIpv4[2] & 0xFF) << 8) | (embeddedIpv4[3] & 0xFF));
        }

        byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            bytes[i * 2] = (byte) ((words[i] >>> 8) & 0xFF);
            bytes[i * 2 + 1] = (byte) (words[i] & 0xFF);
        }
        return bytes;
    }

    private static short parseHexWord(String token) {
        if (token.isEmpty() || token.length() > 4) {
            throw new IllegalArgumentException("Invalid IPv6 hex word: '" + token + "'");
        }
        int val = Integer.parseInt(token, 16);
        return (short) val;
    }
}
