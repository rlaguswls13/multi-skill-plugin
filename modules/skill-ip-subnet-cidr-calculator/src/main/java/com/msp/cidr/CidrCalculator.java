package com.msp.cidr;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * High-performance bitwise IPv4 CIDR calculator and subnet analyzer.
 * Completely zero-dependency and thread-safe.
 */
public final class CidrCalculator {

    private static final Pattern IP_PATTERN = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private CidrCalculator() {
        // Utility class
    }

    /**
     * Parses and calculates subnet details for an IPv4 CIDR string.
     *
     * @param cidr CIDR notation (e.g., "192.168.1.0/24", "10.0.0.0/16", "172.16.0.1/32")
     * @return {@link SubnetInfo} record with calculated subnet parameters
     * @throws IllegalArgumentException if the CIDR format, prefix, or IP is invalid
     */
    public static SubnetInfo calculate(String cidr) {
        Objects.requireNonNull(cidr, "cidr cannot be null");
        String trimmed = cidr.trim();
        int slashIdx = trimmed.indexOf('/');
        if (slashIdx == -1) {
            throw new IllegalArgumentException("Invalid CIDR notation (missing '/'): " + cidr);
        }

        String ipPart = trimmed.substring(0, slashIdx).trim();
        String prefixPart = trimmed.substring(slashIdx + 1).trim();

        int prefix;
        try {
            prefix = Integer.parseInt(prefixPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid CIDR prefix length: " + prefixPart, e);
        }

        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("CIDR prefix must be between 0 and 32, got: " + prefix);
        }

        long ip = ipToLong(ipPart);
        long netmask = prefixToNetmask(prefix);
        long network = ip & netmask;
        long broadcast = network | (~netmask & 0xFFFFFFFFL);

        long totalHosts = prefix == 0 ? (1L << 32) : (1L << (32 - prefix));
        long usableHosts;
        long firstHostLong;
        long lastHostLong;

        if (prefix == 32) {
            usableHosts = 1;
            firstHostLong = network;
            lastHostLong = network;
        } else if (prefix == 31) {
            // RFC 3021 Point-to-Point Links
            usableHosts = 2;
            firstHostLong = network;
            lastHostLong = broadcast;
        } else if (prefix == 0) {
            usableHosts = totalHosts - 2;
            firstHostLong = 1L;
            lastHostLong = 0xFFFFFFFEL;
        } else {
            usableHosts = totalHosts - 2;
            firstHostLong = network + 1;
            lastHostLong = broadcast - 1;
        }

        String canonicalCidr = longToIp(network) + "/" + prefix;
        return new SubnetInfo(
                canonicalCidr,
                longToIp(network),
                longToIp(broadcast),
                longToIp(netmask),
                longToIp(firstHostLong),
                longToIp(lastHostLong),
                totalHosts,
                usableHosts,
                prefix
        );
    }

    /**
     * Checks if the given IP address is contained within the subnet specified by the CIDR.
     *
     * @param cidr      CIDR string
     * @param ipAddress IP address in dotted decimal
     * @return true if the IP belongs to the subnet
     */
    public static boolean contains(String cidr, String ipAddress) {
        Objects.requireNonNull(cidr, "cidr cannot be null");
        Objects.requireNonNull(ipAddress, "ipAddress cannot be null");

        int slashIdx = cidr.indexOf('/');
        if (slashIdx == -1) {
            throw new IllegalArgumentException("Invalid CIDR notation (missing '/'): " + cidr);
        }

        String cidrIpPart = cidr.substring(0, slashIdx).trim();
        int prefix = Integer.parseInt(cidr.substring(slashIdx + 1).trim());
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("CIDR prefix must be between 0 and 32, got: " + prefix);
        }

        long netmask = prefixToNetmask(prefix);
        long cidrNetwork = ipToLong(cidrIpPart) & netmask;
        long targetIp = ipToLong(ipAddress);

        return (targetIp & netmask) == cidrNetwork;
    }

    /**
     * Checks if two CIDR subnets overlap (share one or more common IP addresses).
     *
     * @param cidr1 first CIDR subnet
     * @param cidr2 second CIDR subnet
     * @return true if subnets share at least one IP address
     */
    public static boolean overlaps(String cidr1, String cidr2) {
        SubnetInfo info1 = calculate(cidr1);
        SubnetInfo info2 = calculate(cidr2);

        long net1 = ipToLong(info1.networkAddress());
        long bcast1 = ipToLong(info1.broadcastAddress());
        long net2 = ipToLong(info2.networkAddress());
        long bcast2 = ipToLong(info2.broadcastAddress());

        // Interval overlap: [net1, bcast1] overlaps [net2, bcast2]
        return net1 <= bcast2 && net2 <= bcast1;
    }

    /**
     * Converts a dotted decimal IPv4 string into an unsigned 32-bit integer (stored in a long).
     */
    public static long ipToLong(String ipAddress) {
        Objects.requireNonNull(ipAddress, "ipAddress cannot be null");
        String trimmed = ipAddress.trim();
        var matcher = IP_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid IPv4 address format: " + ipAddress);
        }

        long result = 0;
        for (int i = 1; i <= 4; i++) {
            long octet = Long.parseLong(matcher.group(i));
            if (octet > 255) {
                throw new IllegalArgumentException("Octet out of range (0-255): " + octet + " in " + ipAddress);
            }
            result = (result << 8) | octet;
        }
        return result & 0xFFFFFFFFL;
    }

    /**
     * Converts an unsigned 32-bit integer (long) into dotted-decimal IPv4 representation.
     */
    public static String longToIp(long ip) {
        if (ip < 0 || ip > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("IP value out of 32-bit unsigned bounds: " + ip);
        }
        return String.format("%d.%d.%d.%d",
                (ip >>> 24) & 0xFF,
                (ip >>> 16) & 0xFF,
                (ip >>> 8) & 0xFF,
                ip & 0xFF);
    }

    private static long prefixToNetmask(int prefix) {
        if (prefix == 0) {
            return 0L;
        }
        return (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
    }
}
