package com.msp.sessbind;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utility for parsing and normalizing IPv4 and IPv6 subnet prefixes.
 */
public final class IpSubnetHelper {

    private IpSubnetHelper() {}

    /**
     * Computes the normalized network subnet string for an IP address.
     * Default: IPv4 uses /24 prefix, IPv6 uses /64 prefix.
     *
     * @param ipString Raw IP string (IPv4 or IPv6)
     * @return Normalized subnet representation (e.g. "192.168.1.0/24") or raw string if parsing fails
     */
    public static String getSubnet(String ipString) {
        return getSubnet(ipString, 24, 64);
    }

    /**
     * Computes the subnet prefix with custom bit prefix lengths.
     *
     * @param ipString Raw IP address
     * @param v4Prefix Prefix length for IPv4 (e.g. 24)
     * @param v6Prefix Prefix length for IPv6 (e.g. 64)
     * @return Subnet string representation
     */
    public static String getSubnet(String ipString, int v4Prefix, int v6Prefix) {
        if (ipString == null || ipString.isBlank()) {
            return "none";
        }
        try {
            InetAddress addr = InetAddress.getByName(ipString.trim());
            byte[] bytes = addr.getAddress();

            if (bytes.length == 4) {
                // IPv4
                int prefix = Math.clamp(v4Prefix, 0, 32);
                applyMask(bytes, prefix);
                return String.format("%d.%d.%d.%d/%d",
                        bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF, bytes[3] & 0xFF, prefix);
            } else if (bytes.length == 16) {
                // IPv6
                int prefix = Math.clamp(v6Prefix, 0, 128);
                applyMask(bytes, prefix);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 8; i++) {
                    int val = ((bytes[i * 2] & 0xFF) << 8) | (bytes[i * 2 + 1] & 0xFF);
                    sb.append(Integer.toHexString(val));
                    if (i < 7) {
                        sb.append(":");
                    }
                }
                sb.append("/").append(prefix);
                return sb.toString();
            }
        } catch (UnknownHostException ignored) {
            // Fallback for non-standard IP formats
        }
        return ipString.trim();
    }

    private static void applyMask(byte[] bytes, int prefixBits) {
        int fullBytes = prefixBits / 8;
        int remainingBits = prefixBits % 8;

        for (int i = fullBytes + 1; i < bytes.length; i++) {
            bytes[i] = 0;
        }

        if (fullBytes < bytes.length) {
            int mask = 0xFF << (8 - remainingBits);
            bytes[fullBytes] = (byte) (bytes[fullBytes] & mask);
        }
    }
}
