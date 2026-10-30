package com.msp.ipgeo;

import java.util.Objects;

/**
 * IPv4 CIDR 대역 표현 및 비트마스크 매칭 객체.
 */
public final class Cidr {

    private final long networkAddress;
    private final long netmask;
    private final int prefixLength;
    private final String cidrString;

    public Cidr(String cidr) {
        Objects.requireNonNull(cidr, "cidr must not be null");
        this.cidrString = cidr.trim();
        String[] parts = this.cidrString.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid CIDR format (expected IP/prefix): " + cidr);
        }

        long ip = ipToLong(parts[0].trim());
        this.prefixLength = Integer.parseInt(parts[1].trim());
        if (prefixLength < 0 || prefixLength > 32) {
            throw new IllegalArgumentException("Invalid prefix length: " + prefixLength);
        }

        this.netmask = (prefixLength == 0) ? 0L : (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
        this.networkAddress = ip & this.netmask;
    }

    public static Cidr of(String cidr) {
        return new Cidr(cidr);
    }

    public boolean matches(String ipStr) {
        if (ipStr == null || ipStr.isBlank()) return false;
        try {
            long ip = ipToLong(ipStr.trim());
            return (ip & this.netmask) == this.networkAddress;
        } catch (Exception e) {
            return false;
        }
    }

    public static long ipToLong(String ipStr) {
        String[] octets = ipStr.split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ipStr);
        }
        long result = 0;
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(octets[i].trim());
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Octet out of range [0-255]: " + octet);
            }
            result = (result << 8) | octet;
        }
        return result & 0xFFFFFFFFL;
    }

    public String cidrString() {
        return cidrString;
    }

    public int prefixLength() {
        return prefixLength;
    }

    @Override
    public String toString() {
        return cidrString;
    }
}
