package com.msp.subnettrie;

import java.util.Objects;

/**
 * Parsed representation of an IPv4 or IPv6 CIDR block prefix.
 */
public final class CidrPrefix {

    private final IpParser.ParsedIp ip;
    private final int prefixLength;
    private final String original;

    private CidrPrefix(IpParser.ParsedIp ip, int prefixLength, String original) {
        this.ip = ip;
        this.prefixLength = prefixLength;
        this.original = original;
    }

    public static CidrPrefix parse(String cidr) {
        Objects.requireNonNull(cidr, "cidr string must not be null");
        String clean = cidr.trim();
        int slash = clean.indexOf('/');
        String ipPart = (slash >= 0) ? clean.substring(0, slash) : clean;
        IpParser.ParsedIp parsed = IpParser.parseIp(ipPart);

        int maxLen = parsed.isIpv6() ? 128 : 32;
        int len;
        if (slash >= 0) {
            String lenStr = clean.substring(slash + 1).trim();
            try {
                len = Integer.parseInt(lenStr);
                if (len < 0 || len > maxLen) {
                    throw new IllegalArgumentException("CIDR prefix length out of range [0-" + maxLen + "]: " + len);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid CIDR prefix length: " + lenStr, e);
            }
        } else {
            len = maxLen; // Default to host mask (/32 or /128)
        }

        return new CidrPrefix(parsed, len, clean);
    }

    public int getBit(int index) {
        if (index < 0 || index >= prefixLength) {
            throw novelIndexOutOfBounds(index);
        }
        return ip.getBit(index);
    }

    private IndexOutOfBoundsException novelIndexOutOfBounds(int index) {
        return new IndexOutOfBoundsException("Bit index " + index + " exceeds prefix length " + prefixLength);
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    public boolean isIpv6() {
        return ip.isIpv6();
    }

    public int getMaxPrefixLength() {
        return isIpv6() ? 128 : 32;
    }

    public String getOriginal() {
        return original;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CidrPrefix that)) return false;
        if (prefixLength != that.prefixLength || isIpv6() != that.isIpv6()) return false;
        for (int i = 0; i < prefixLength; i++) {
            if (this.getBit(i) != that.getBit(i)) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = 1;
        h = 31 * h + prefixLength;
        h = 31 * h + (isIpv6() ? 1 : 0);
        for (int i = 0; i < prefixLength; i++) {
            h = 31 * h + getBit(i);
        }
        return h;
    }

    @Override
    public String toString() {
        return original;
    }
}
