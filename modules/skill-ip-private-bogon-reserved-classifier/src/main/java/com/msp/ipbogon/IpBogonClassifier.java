package com.msp.ipbogon;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Objects;

/**
 * Zero-DNS, thread-safe IPv4 and IPv6 special-use/bogon classifier.
 *
 * <p>Automatically unwraps IPv4-mapped IPv6 addresses ({@code ::ffff:0:0/96}, e.g. {@code ::ffff:127.0.0.1}
 * or {@code ::ffff:7f00:1}) so SSRF filters cannot be bypassed via IPv6-mapped IPv4 literals.
 */
public final class IpBogonClassifier {

    private IpBogonClassifier() {
    }

    /**
     * Classifies a numeric IPv4 or IPv6 literal without performing any DNS resolution.
     * Supports bracketed IPv6 ({@code [::1]}) and zone IDs ({@code fe80::1%eth0}).
     *
     * @throws IllegalArgumentException if {@code ipLiteral} is not a valid IPv4 or IPv6 literal
     */
    public static IpClassification classify(String ipLiteral) {
        if (ipLiteral == null || ipLiteral.isBlank()) {
            throw new IllegalArgumentException("IP literal must not be null or blank");
        }
        byte[] bytes = parseIpLiteral(ipLiteral.trim());
        return classify(bytes);
    }

    /**
     * Classifies a resolved {@link InetAddress}.
     */
    public static IpClassification classify(InetAddress address) {
        Objects.requireNonNull(address, "address must not be null");
        return classify(address.getAddress());
    }

    /**
     * Classifies raw network-order IP bytes (length 4 for IPv4, length 16 for IPv6).
     */
    public static IpClassification classify(byte[] rawBytes) {
        Objects.requireNonNull(rawBytes, "rawBytes must not be null");
        if (rawBytes.length == 4) {
            return classifyIpv4(rawBytes, false);
        }
        if (rawBytes.length == 16) {
            if (isIpv4MappedIpv6(rawBytes)) {
                byte[] v4 = Arrays.copyOfRange(rawBytes, 12, 16);
                return classifyIpv4(v4, true);
            }
            return classifyIpv6(rawBytes);
        }
        throw new IllegalArgumentException("IP byte array must have length 4 or 16, got " + rawBytes.length);
    }

    public static boolean isPublic(String ipLiteral) {
        return classify(ipLiteral).isPublic();
    }

    public static boolean isBogon(String ipLiteral) {
        return classify(ipLiteral).isBogon();
    }

    private static IpClassification classifyIpv4(byte[] b, boolean unwrapped) {
        int ip = ((b[0] & 0xFF) << 24)
                | ((b[1] & 0xFF) << 16)
                | ((b[2] & 0xFF) << 8)
                | (b[3] & 0xFF);
        String formatted = formatIpv4(b);

        // Cloud metadata IMDS (169.254.169.254/32) checked before general link-local
        if (ip == 0xA9FEA9FE) {
            return new IpClassification(formatted, 4, IpCategory.CLOUD_METADATA, unwrapped, "169.254.169.254/32");
        }
        // Broadcast 255.255.255.255/32
        if (ip == 0xFFFFFFFF) {
            return new IpClassification(formatted, 4, IpCategory.BROADCAST, unwrapped, "255.255.255.255/32");
        }
        // Unspecified / "This network" 0.0.0.0/8
        if (matchesV4(ip, 0x00000000, 8)) {
            return new IpClassification(formatted, 4, IpCategory.UNSPECIFIED, unwrapped, "0.0.0.0/8");
        }
        // RFC 1918 Private
        if (matchesV4(ip, 0x0A000000, 8)) {
            return new IpClassification(formatted, 4, IpCategory.PRIVATE_RFC1918, unwrapped, "10.0.0.0/8");
        }
        if (matchesV4(ip, 0xAC100000, 12)) {
            return new IpClassification(formatted, 4, IpCategory.PRIVATE_RFC1918, unwrapped, "172.16.0.0/12");
        }
        if (matchesV4(ip, 0xC0A80000, 16)) {
            return new IpClassification(formatted, 4, IpCategory.PRIVATE_RFC1918, unwrapped, "192.168.0.0/16");
        }
        // RFC 6598 CGNAT 100.64.0.0/10
        if (matchesV4(ip, 0x64400000, 10)) {
            return new IpClassification(formatted, 4, IpCategory.CGNAT, unwrapped, "100.64.0.0/10");
        }
        // Loopback 127.0.0.0/8
        if (matchesV4(ip, 0x7F000000, 8)) {
            return new IpClassification(formatted, 4, IpCategory.LOOPBACK, unwrapped, "127.0.0.0/8");
        }
        // Link-local 169.254.0.0/16
        if (matchesV4(ip, 0xA9FE0000, 16)) {
            return new IpClassification(formatted, 4, IpCategory.LINK_LOCAL, unwrapped, "169.254.0.0/16");
        }
        // IETF Protocol Assignments 192.0.0.0/24
        if (matchesV4(ip, 0xC0000000, 24)) {
            return new IpClassification(formatted, 4, IpCategory.RESERVED_SPECIAL, unwrapped, "192.0.0.0/24");
        }
        // Documentation (TEST-NET-1, TEST-NET-2, TEST-NET-3)
        if (matchesV4(ip, 0xC0000200, 24)) {
            return new IpClassification(formatted, 4, IpCategory.DOCUMENTATION, unwrapped, "192.0.2.0/24");
        }
        if (matchesV4(ip, 0xC6336400, 24)) {
            return new IpClassification(formatted, 4, IpCategory.DOCUMENTATION, unwrapped, "198.51.100.0/24");
        }
        if (matchesV4(ip, 0xCB007100, 24)) {
            return new IpClassification(formatted, 4, IpCategory.DOCUMENTATION, unwrapped, "203.0.113.0/24");
        }
        // Benchmarking 198.18.0.0/15
        if (matchesV4(ip, 0xC6120000, 15)) {
            return new IpClassification(formatted, 4, IpCategory.BENCHMARKING, unwrapped, "198.18.0.0/15");
        }
        // Multicast 224.0.0.0/4
        if (matchesV4(ip, 0xE0000000, 4)) {
            return new IpClassification(formatted, 4, IpCategory.MULTICAST, unwrapped, "224.0.0.0/4");
        }
        // Reserved for future use 240.0.0.0/4
        if (matchesV4(ip, 0xF0000000, 4)) {
            return new IpClassification(formatted, 4, IpCategory.RESERVED_SPECIAL, unwrapped, "240.0.0.0/4");
        }

        return new IpClassification(formatted, 4, IpCategory.PUBLIC_UNICAST, unwrapped, "0.0.0.0/0");
    }

    private static IpClassification classifyIpv6(byte[] b) {
        String formatted = formatIpv6(b);

        // Unspecified ::/128
        if (isAllZero(b, 0, 16)) {
            return new IpClassification(formatted, 6, IpCategory.UNSPECIFIED, false, "::/128");
        }
        // Loopback ::1/128
        if (isAllZero(b, 0, 15) && b[15] == 1) {
            return new IpClassification(formatted, 6, IpCategory.LOOPBACK, false, "::1/128");
        }
        // AWS IPv6 IMDS fd00:ec2::254/128 (checked before ULA fc00::/7)
        if ((b[0] & 0xFF) == 0xFD && b[1] == 0x00 && (b[2] & 0xFF) == 0x0E && (b[3] & 0xFF) == 0xC2
                && isAllZero(b, 4, 14) && (b[14] & 0xFF) == 0x02 && (b[15] & 0xFF) == 0x54) {
            return new IpClassification(formatted, 6, IpCategory.CLOUD_METADATA, false, "fd00:ec2::254/128");
        }
        // Unique Local Address (ULA) fc00::/7
        if ((b[0] & 0xFE) == 0xFC) {
            return new IpClassification(formatted, 6, IpCategory.UNIQUE_LOCAL_IPV6, false, "fc00::/7");
        }
        // Link-local fe80::/10
        if ((b[0] & 0xFF) == 0xFE && (b[1] & 0xC0) == 0x80) {
            return new IpClassification(formatted, 6, IpCategory.LINK_LOCAL, false, "fe80::/10");
        }
        // Deprecated site-local fec0::/10
        if ((b[0] & 0xFF) == 0xFE && (b[1] & 0xC0) == 0xC0) {
            return new IpClassification(formatted, 6, IpCategory.RESERVED_SPECIAL, false, "fec0::/10");
        }
        // Multicast ff00::/8
        if ((b[0] & 0xFF) == 0xFF) {
            return new IpClassification(formatted, 6, IpCategory.MULTICAST, false, "ff00::/8");
        }
        // Documentation 2001:db8::/32
        if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x01 && (b[2] & 0xFF) == 0x0D && (b[3] & 0xFF) == 0xB8) {
            return new IpClassification(formatted, 6, IpCategory.DOCUMENTATION, false, "2001:db8::/32");
        }
        // Benchmarking 2001:2::/48
        if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x01 && b[2] == 0x00 && b[3] == 0x02 && b[4] == 0x00 && b[5] == 0x00) {
            return new IpClassification(formatted, 6, IpCategory.BENCHMARKING, false, "2001:2::/48");
        }
        // Discard-Only 100::/64
        if ((b[0] & 0xFF) == 0x01 && b[1] == 0x00 && isAllZero(b, 2, 8)) {
            return new IpClassification(formatted, 6, IpCategory.RESERVED_SPECIAL, false, "100::/64");
        }
        // NAT64 well-known prefix 64:ff9b::/96
        if (b[0] == 0x00 && (b[1] & 0xFF) == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B && isAllZero(b, 4, 12)) {
            return new IpClassification(formatted, 6, IpCategory.RESERVED_SPECIAL, false, "64:ff9b::/96");
        }
        // Teredo 2001::/32
        if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x01 && b[2] == 0x00 && b[3] == 0x00) {
            return new IpClassification(formatted, 6, IpCategory.RESERVED_SPECIAL, false, "2001::/32");
        }
        // 6to4 2002::/16
        if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x02) {
            return new IpClassification(formatted, 6, IpCategory.RESERVED_SPECIAL, false, "2002::/16");
        }
        // Global unicast 2000::/3
        if ((b[0] & 0xE0) == 0x20) {
            return new IpClassification(formatted, 6, IpCategory.PUBLIC_UNICAST, false, "2000::/3");
        }

        return new IpClassification(formatted, 6, IpCategory.RESERVED_SPECIAL, false, "::/0");
    }

    static boolean isIpv4MappedIpv6(byte[] b) {
        return b.length == 16
                && isAllZero(b, 0, 10)
                && (b[10] & 0xFF) == 0xFF
                && (b[11] & 0xFF) == 0xFF;
    }

    private static boolean matchesV4(int ip, int network, int prefixLen) {
        int mask = prefixLen == 0 ? 0 : (0xFFFFFFFF << (32 - prefixLen));
        return (ip & mask) == (network & mask);
    }

    private static boolean isAllZero(byte[] b, int from, int to) {
        for (int i = from; i < to; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pure numeric IPv4/IPv6 literal parser (never triggers DNS).
     */
    static byte[] parseIpLiteral(String literal) {
        String s = literal;
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        int zoneIdx = s.indexOf('%');
        if (zoneIdx >= 0) {
            s = s.substring(0, zoneIdx);
        }
        if (s.indexOf(':') >= 0) {
            return parseIpv6(s);
        }
        return parseIpv4(s);
    }

    private static byte[] parseIpv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 literal: " + s);
        }
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                throw new IllegalArgumentException("Invalid IPv4 octet: " + s);
            }
            if (part.length() > 1 && part.charAt(0) == '0') {
                throw new IllegalArgumentException("Leading zeros not allowed in IPv4 literal: " + s);
            }
            int val = 0;
            for (int j = 0; j < part.length(); j++) {
                char c = part.charAt(j);
                if (c < '0' || c > '9') {
                    throw new IllegalArgumentException("Invalid IPv4 literal: " + s);
                }
                val = val * 10 + (c - '0');
            }
            if (val > 255) {
                throw new IllegalArgumentException("IPv4 octet out of range: " + s);
            }
            out[i] = (byte) val;
        }
        return out;
    }

    private static byte[] parseIpv6(String s) {
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Empty IPv6 literal");
        }
        // Handle embedded IPv4 tail (e.g. ::ffff:127.0.0.1)
        int lastColon = s.lastIndexOf(':');
        byte[] embeddedV4 = null;
        String v6Part = s;
        if (s.indexOf('.') >= 0) {
            if (lastColon < 0) {
                throw new IllegalArgumentException("Invalid IPv6 literal: " + s);
            }
            embeddedV4 = parseIpv4(s.substring(lastColon + 1));
            v6Part = s.substring(0, lastColon);
            if (v6Part.endsWith(":")) {
                // Keep trailing "::" marker intact
                v6Part = v6Part + ":";
                if (v6Part.endsWith(":::")) {
                    v6Part = v6Part.substring(0, v6Part.length() - 1);
                }
            }
        }

        int targetGroups = embeddedV4 != null ? 6 : 8;
        int doubleColon = v6Part.indexOf("::");
        if (doubleColon >= 0 && v6Part.indexOf("::", doubleColon + 2) >= 0) {
            throw new IllegalArgumentException("Multiple '::' in IPv6 literal: " + s);
        }

        int[] groups = new int[targetGroups];
        if (doubleColon < 0) {
            String[] parts = v6Part.split(":", -1);
            if (parts.length != targetGroups) {
                throw new IllegalArgumentException("Invalid IPv6 group count: " + s);
            }
            for (int i = 0; i < targetGroups; i++) {
                groups[i] = parseHexGroup(parts[i], s);
            }
        } else {
            String leftStr = v6Part.substring(0, doubleColon);
            String rightStr = v6Part.substring(doubleColon + 2);
            String[] left = leftStr.isEmpty() ? new String[0] : leftStr.split(":", -1);
            String[] right = rightStr.isEmpty() ? new String[0] : rightStr.split(":", -1);
            if (left.length + right.length > targetGroups) {
                throw new IllegalArgumentException("Too many groups in IPv6 literal: " + s);
            }
            for (int i = 0; i < left.length; i++) {
                groups[i] = parseHexGroup(left[i], s);
            }
            int rightStart = targetGroups - right.length;
            for (int i = 0; i < right.length; i++) {
                groups[rightStart + i] = parseHexGroup(right[i], s);
            }
        }

        byte[] out = new byte[16];
        for (int i = 0; i < targetGroups; i++) {
            out[i * 2] = (byte) ((groups[i] >>> 8) & 0xFF);
            out[i * 2 + 1] = (byte) (groups[i] & 0xFF);
        }
        if (embeddedV4 != null) {
            System.arraycopy(embeddedV4, 0, out, 12, 4);
        }
        return out;
    }

    private static int parseHexGroup(String group, String full) {
        if (group.isEmpty() || group.length() > 4) {
            throw new IllegalArgumentException("Invalid IPv6 hex group '" + group + "' in " + full);
        }
        int val = 0;
        for (int i = 0; i < group.length(); i++) {
            int digit = Character.digit(group.charAt(i), 16);
            if (digit < 0) {
                throw new IllegalArgumentException("Invalid IPv6 hex digit in " + full);
            }
            val = (val << 4) | digit;
        }
        return val;
    }

    private static String formatIpv4(byte[] b) {
        return (b[0] & 0xFF) + "." + (b[1] & 0xFF) + "." + (b[2] & 0xFF) + "." + (b[3] & 0xFF);
    }

    private static String formatIpv6(byte[] b) {
        int[] g = new int[8];
        for (int i = 0; i < 8; i++) {
            g[i] = ((b[i * 2] & 0xFF) << 8) | (b[i * 2 + 1] & 0xFF);
        }
        // RFC 5952 canonical zero compression (longest run of >= 2 zeros)
        int bestStart = -1;
        int bestLen = 1;
        int curStart = -1;
        int curLen = 0;
        for (int i = 0; i < 8; i++) {
            if (g[i] == 0) {
                if (curStart < 0) {
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
        StringBuilder sb = new StringBuilder(39);
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                sb.append("::");
                i += bestLen - 1;
            } else {
                if (i > 0 && !(bestStart >= 0 && i == bestStart + bestLen)) {
                    sb.append(':');
                }
                sb.append(Integer.toHexString(g[i]));
            }
        }
        return sb.toString();
    }
}
