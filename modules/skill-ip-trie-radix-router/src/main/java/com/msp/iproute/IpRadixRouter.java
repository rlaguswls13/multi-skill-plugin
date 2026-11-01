package com.msp.iproute;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-speed, thread-safe Longest Prefix Match (LPM) routing table lookup engine for IPv4 and IPv6 CIDR blocks.
 * <p>
 * Uses a pure Java binary radix trie branching bit-by-bit (0 and 1) along the address prefix.
 *
 * @param <T> route target type (e.g. gateway, interface name, next-hop cluster, handler)
 */
public class IpRadixRouter<T> {

    private static final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final Pattern IPV6_PATTERN = Pattern.compile("^[0-9a-fA-F:]+$");

    private static final class Node<T> {
        Node<T> zero;
        Node<T> one;
        T value;
        boolean hasValue;
    }

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    private Node<T> ipv4Root = new Node<>();
    private Node<T> ipv6Root = new Node<>();
    private int routeCount = 0;

    public IpRadixRouter() {
    }

    /**
     * Adds or updates a route for the specified CIDR block (e.g. "192.168.1.0/24", "10.0.0.0/8", "0.0.0.0/0", "2001:db8::/32", "::/0").
     *
     * @param cidr        the CIDR block notation
     * @param routeTarget the route target object to associate
     * @throws NullPointerException     if routeTarget is null
     * @throws IllegalArgumentException if CIDR format or prefix length is invalid
     */
    public void addRoute(String cidr, T routeTarget) {
        Objects.requireNonNull(routeTarget, "routeTarget cannot be null");
        ParsedCidr parsed = parseCidr(cidr);

        writeLock.lock();
        try {
            Node<T> current = parsed.isIpv6 ? ipv6Root : ipv4Root;
            for (int i = 0; i < parsed.prefixLength; i++) {
                int bit = getBit(parsed.bytes, i);
                if (bit == 0) {
                    if (current.zero == null) {
                        current.zero = new Node<>();
                    }
                    current = current.zero;
                } else {
                    if (current.one == null) {
                        current.one = new Node<>();
                    }
                    current = current.one;
                }
            }

            if (!current.hasValue) {
                current.hasValue = true;
                routeCount++;
            }
            current.value = routeTarget;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Finds the most specific route matching the given IP address using Longest Prefix Match (LPM).
     *
     * @param ipAddress IP address string (IPv4 or IPv6 literal)
     * @return Optional containing the matching route target, or empty if no route matches
     * @throws IllegalArgumentException if ipAddress is null, blank, or malformed
     */
    public Optional<T> lookup(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("IP address cannot be null or blank");
        }
        ParsedIp parsed = parseIp(ipAddress.trim());

        readLock.lock();
        try {
            Node<T> current = parsed.isIpv6 ? ipv6Root : ipv4Root;
            T bestMatch = current.hasValue ? current.value : null;

            int totalBits = parsed.bytes.length * 8;
            for (int i = 0; i < totalBits; i++) {
                int bit = getBit(parsed.bytes, i);
                current = (bit == 0) ? current.zero : current.one;
                if (current == null) {
                    break;
                }
                if (current.hasValue) {
                    bestMatch = current.value;
                }
            }
            return Optional.ofNullable(bestMatch);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Removes the route associated with the exact CIDR block.
     *
     * @param cidr the CIDR block to remove
     * @return true if the route existed and was removed, false otherwise
     * @throws IllegalArgumentException if CIDR format is invalid
     */
    public boolean removeRoute(String cidr) {
        ParsedCidr parsed = parseCidr(cidr);

        writeLock.lock();
        try {
            Node<T> current = parsed.isIpv6 ? ipv6Root : ipv4Root;
            for (int i = 0; i < parsed.prefixLength; i++) {
                int bit = getBit(parsed.bytes, i);
                current = (bit == 0) ? current.zero : current.one;
                if (current == null) {
                    return false;
                }
            }

            if (current.hasValue) {
                current.hasValue = false;
                current.value = null;
                routeCount--;
                return true;
            }
            return false;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns the total number of registered CIDR routes.
     *
     * @return count of routes
     */
    public int routeCount() {
        readLock.lock();
        try {
            return routeCount;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Removes all registered routes.
     */
    public void clear() {
        writeLock.lock();
        try {
            ipv4Root = new Node<>();
            ipv6Root = new Node<>();
            routeCount = 0;
        } finally {
            writeLock.unlock();
        }
    }

    private static int getBit(byte[] bytes, int bitIndex) {
        int byteIdx = bitIndex / 8;
        int bitInByte = bitIndex % 8;
        return (bytes[byteIdx] >>> (7 - bitInByte)) & 1;
    }

    private record ParsedIp(byte[] bytes, boolean isIpv6) {
    }

    private record ParsedCidr(byte[] bytes, int prefixLength, boolean isIpv6) {
    }

    private static ParsedIp parseIp(String ip) {
        Matcher v4Matcher = IPV4_PATTERN.matcher(ip);
        if (v4Matcher.matches()) {
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                int octet = Integer.parseInt(v4Matcher.group(i + 1));
                if (octet > 255) {
                    throw new IllegalArgumentException("IPv4 octet exceeds 255: " + ip);
                }
                bytes[i] = (byte) octet;
            }
            return new ParsedIp(bytes, false);
        }

        if (ip.contains(":") && IPV6_PATTERN.matcher(ip).matches()) {
            try {
                InetAddress addr = InetAddress.getByName(ip);
                byte[] raw = addr.getAddress();
                if (raw.length == 16) {
                    return new ParsedIp(raw, true);
                }
            } catch (UnknownHostException ignored) {
                // Handled below
            }
        }

        throw new IllegalArgumentException("Invalid IP address literal: " + ip);
    }

    private static ParsedCidr parseCidr(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            throw new IllegalArgumentException("CIDR block cannot be null or blank");
        }

        String trimmed = cidr.trim();
        String[] parts = trimmed.split("/", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException("Invalid CIDR format (multiple '/' found): " + cidr);
        }

        String ipPart = parts[0].trim();
        ParsedIp ip = parseIp(ipPart);

        int maxPrefix = ip.isIpv6 ? 128 : 32;
        int prefixLength;
        if (parts.length == 2) {
            try {
                prefixLength = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid CIDR prefix length: " + parts[1], e);
            }
            if (prefixLength < 0 || prefixLength > maxPrefix) {
                throw new IllegalArgumentException("CIDR prefix length out of range [0.." + maxPrefix + "]: " + prefixLength);
            }
        } else {
            prefixLength = maxPrefix;
        }

        return new ParsedCidr(ip.bytes, prefixLength, ip.isIpv6);
    }
}
