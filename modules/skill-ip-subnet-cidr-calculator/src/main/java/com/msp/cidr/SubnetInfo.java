package com.msp.cidr;

import java.util.Objects;

/**
 * Detailed IPv4 Subnet calculation metadata.
 *
 * @param cidr             original CIDR representation (e.g. "192.168.1.0/24")
 * @param networkAddress   the network address (e.g. "192.168.1.0")
 * @param broadcastAddress the broadcast address (e.g. "192.168.1.255")
 * @param netmask          the subnet mask in dotted decimal (e.g. "255.255.255.0")
 * @param firstHost        first usable host IP address
 * @param lastHost         last usable host IP address
 * @param totalHosts       total count of IP addresses in the block (2^(32-prefix))
 * @param usableHosts      count of usable host IP addresses
 * @param prefixLength     CIDR prefix bit length (0 to 32)
 */
public record SubnetInfo(
        String cidr,
        String networkAddress,
        String broadcastAddress,
        String netmask,
        String firstHost,
        String lastHost,
        long totalHosts,
        long usableHosts,
        int prefixLength
) {
    public SubnetInfo {
        Objects.requireNonNull(cidr, "cidr cannot be null");
        Objects.requireNonNull(networkAddress, "networkAddress cannot be null");
        Objects.requireNonNull(broadcastAddress, "broadcastAddress cannot be null");
        Objects.requireNonNull(netmask, "netmask cannot be null");
        Objects.requireNonNull(firstHost, "firstHost cannot be null");
        Objects.requireNonNull(lastHost, "lastHost cannot be null");
        if (prefixLength < 0 || prefixLength > 32) {
            throw new IllegalArgumentException("Prefix length must be between 0 and 32, got: " + prefixLength);
        }
        if (totalHosts < 0) {
            throw new IllegalArgumentException("totalHosts must be non-negative");
        }
        if (usableHosts < 0) {
            throw new IllegalArgumentException("usableHosts must be non-negative");
        }
    }
}
