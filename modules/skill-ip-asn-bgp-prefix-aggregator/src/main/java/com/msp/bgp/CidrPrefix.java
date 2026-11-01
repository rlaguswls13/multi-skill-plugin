package com.msp.bgp;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents an IPv4 or IPv6 CIDR prefix.
 */
public final class CidrPrefix implements Comparable<CidrPrefix> {

    private final IpVersion version;
    private final BigInteger networkAddress;
    private final int prefixLength;
    private final BigInteger broadcastAddress;

    private CidrPrefix(IpVersion version, BigInteger networkAddress, int prefixLength) {
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.prefixLength = prefixLength;
        int bits = version.bitLength();
        if (prefixLength < 0 || prefixLength > bits) {
            throw new IllegalArgumentException("Invalid prefix length for " + version + ": " + prefixLength);
        }

        BigInteger mask = computeMask(bits, prefixLength);
        this.networkAddress = networkAddress.and(mask);

        BigInteger hostMask = BigInteger.ONE.shiftLeft(bits - prefixLength).subtract(BigInteger.ONE);
        this.broadcastAddress = this.networkAddress.or(hostMask);
    }

    public static CidrPrefix of(IpVersion version, BigInteger networkAddress, int prefixLength) {
        return new CidrPrefix(version, networkAddress, prefixLength);
    }

    public static CidrPrefix parse(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            throw new IllegalArgumentException("CIDR string cannot be empty");
        }
        String trimmed = cidr.trim();
        int slashIdx = trimmed.indexOf('/');
        if (slashIdx == -1) {
            // Default to /32 for IPv4, /128 for IPv6
            try {
                InetAddress addr = InetAddress.getByName(trimmed);
                IpVersion ver = addr.getAddress().length == 4 ? IpVersion.IPV4 : IpVersion.IPV6;
                return of(ver, new BigInteger(1, addr.getAddress()), ver.bitLength());
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid IP address format: " + trimmed, e);
            }
        }

        String ipPart = trimmed.substring(0, slashIdx);
        String prefixPart = trimmed.substring(slashIdx + 1);

        int prefixLen;
        try {
            prefixLen = Integer.parseInt(prefixPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid CIDR prefix length: " + prefixPart);
        }

        try {
            InetAddress addr = InetAddress.getByName(ipPart);
            byte[] bytes = addr.getAddress();
            IpVersion ver = bytes.length == 4 ? IpVersion.IPV4 : IpVersion.IPV6;
            return of(ver, new BigInteger(1, bytes), prefixLen);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address: " + ipPart, e);
        }
    }

    public IpVersion getVersion() {
        return version;
    }

    public BigInteger getNetworkAddress() {
        return networkAddress;
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    public BigInteger getBroadcastAddress() {
        return broadcastAddress;
    }

    public boolean contains(CidrPrefix other) {
        if (other == null || this.version != other.version) {
            return false;
        }
        return this.prefixLength <= other.prefixLength
                && this.networkAddress.compareTo(other.networkAddress) <= 0
                && this.broadcastAddress.compareTo(other.broadcastAddress) >= 0;
    }

    public boolean isSibling(CidrPrefix other) {
        if (other == null || this.version != other.version) {
            return false;
        }
        if (this.prefixLength == 0 || this.prefixLength != other.prefixLength) {
            return false;
        }

        int parentBits = prefixLength - 1;
        BigInteger parentMask = computeMask(version.bitLength(), parentBits);
        BigInteger parent1 = this.networkAddress.and(parentMask);
        BigInteger parent2 = other.networkAddress.and(parentMask);

        return parent1.equals(parent2) && !this.networkAddress.equals(other.networkAddress);
    }

    public Optional<CidrPrefix> mergeWithSibling(CidrPrefix other) {
        if (!isSibling(other)) {
            return Optional.empty();
        }
        int parentBits = prefixLength - 1;
        BigInteger parentMask = computeMask(version.bitLength(), parentBits);
        BigInteger parentAddress = this.networkAddress.and(parentMask);
        return Optional.of(new CidrPrefix(this.version, parentAddress, parentBits));
    }

    public String toCidrString() {
        byte[] bytes = toByteArray(networkAddress, version.bitLength() / 8);
        if (version == IpVersion.IPV4) {
            String ip = (bytes[0] & 0xff) + "." +
                    (bytes[1] & 0xff) + "." +
                    (bytes[2] & 0xff) + "." +
                    (bytes[3] & 0xff);
            return ip + "/" + prefixLength;
        } else {
            return formatIpv6(bytes) + "/" + prefixLength;
        }
    }

    private static String formatIpv6(byte[] bytes) {
        int[] words = new int[8];
        for (int i = 0; i < 8; i++) {
            words[i] = ((bytes[i * 2] & 0xff) << 8) | (bytes[i * 2 + 1] & 0xff);
        }

        int bestStart = -1;
        int bestLen = 0;
        int curStart = -1;
        int curLen = 0;

        for (int i = 0; i < 8; i++) {
            if (words[i] == 0) {
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

        if (bestLen < 2) {
            bestStart = -1;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                sb.append("::");
                i += bestLen - 1;
            } else {
                if (!sb.isEmpty() && !sb.toString().endsWith(":")) {
                    sb.append(":");
                }
                sb.append(Integer.toHexString(words[i]));
            }
        }
        return sb.toString();
    }

    private static BigInteger computeMask(int totalBits, int prefixLen) {
        if (prefixLen == 0) {
            return BigInteger.ZERO;
        }
        BigInteger allOnes = BigInteger.ONE.shiftLeft(totalBits).subtract(BigInteger.ONE);
        BigInteger hostBits = BigInteger.ONE.shiftLeft(totalBits - prefixLen).subtract(BigInteger.ONE);
        return allOnes.xor(hostBits);
    }

    private static byte[] toByteArray(BigInteger val, int byteLength) {
        byte[] src = val.toByteArray();
        byte[] dst = new byte[byteLength];
        if (src.length == byteLength) {
            return src;
        } else if (src.length > byteLength) {
            System.arraycopy(src, src.length - byteLength, dst, 0, byteLength);
        } else {
            System.arraycopy(src, 0, dst, byteLength - src.length, src.length);
        }
        return dst;
    }

    @Override
    public int compareTo(CidrPrefix o) {
        if (this.version != o.version) {
            return this.version.compareTo(o.version);
        }
        int cmp = this.networkAddress.compareTo(o.networkAddress);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(this.prefixLength, o.prefixLength);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CidrPrefix that)) return false;
        return prefixLength == that.prefixLength &&
                version == that.version &&
                networkAddress.equals(that.networkAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, networkAddress, prefixLength);
    }

    @Override
    public String toString() {
        return toCidrString();
    }
}
