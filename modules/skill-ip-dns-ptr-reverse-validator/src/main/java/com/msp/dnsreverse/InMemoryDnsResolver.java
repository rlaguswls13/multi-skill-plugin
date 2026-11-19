package com.msp.dnsreverse;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe, in-memory DNS resolver for deterministic testing and local mock resolution.
 */
public class InMemoryDnsResolver implements DnsResolver {

    private final Map<String, List<String>> ptrRecords = new ConcurrentHashMap<>();
    private final Map<String, List<InetAddress>> aRecords = new ConcurrentHashMap<>();
    private final Map<String, List<InetAddress>> aaaaRecords = new ConcurrentHashMap<>();

    /**
     * Registers a PTR record mapping an IP to a domain name.
     */
    public InMemoryDnsResolver addPtrRecord(InetAddress ip, String domain) {
        Objects.requireNonNull(ip, "ip must not be null");
        Objects.requireNonNull(domain, "domain must not be null");
        ptrRecords.computeIfAbsent(ip.getHostAddress(), k -> new CopyOnWriteArrayList<>())
                .add(normalizeDomain(domain));
        return this;
    }

    /**
     * Registers an A (IPv4) forward DNS record.
     */
    public InMemoryDnsResolver addARecord(String domain, InetAddress ip) {
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(ip, "ip must not be null");
        if (!(ip instanceof Inet4Address)) {
            throw new IllegalArgumentException("A record requires an IPv4 address, got: " + ip);
        }
        aRecords.computeIfAbsent(normalizeDomain(domain), k -> new CopyOnWriteArrayList<>())
                .add(ip);
        return this;
    }

    /**
     * Registers an AAAA (IPv6) forward DNS record.
     */
    public InMemoryDnsResolver addAaaaRecord(String domain, InetAddress ip) {
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(ip, "ip must not be null");
        if (!(ip instanceof Inet6Address)) {
            throw new IllegalArgumentException("AAAA record requires an IPv6 address, got: " + ip);
        }
        aaaaRecords.computeIfAbsent(normalizeDomain(domain), k -> new CopyOnWriteArrayList<>())
                .add(ip);
        return this;
    }

    @Override
    public List<String> resolvePtr(InetAddress ip) {
        if (ip == null) return Collections.emptyList();
        List<String> list = ptrRecords.get(ip.getHostAddress());
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    @Override
    public List<InetAddress> resolveA(String domain) {
        if (domain == null) return Collections.emptyList();
        List<InetAddress> list = aRecords.get(normalizeDomain(domain));
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    @Override
    public List<InetAddress> resolveAaaa(String domain) {
        if (domain == null) return Collections.emptyList();
        List<InetAddress> list = aaaaRecords.get(normalizeDomain(domain));
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    public void clear() {
        ptrRecords.clear();
        aRecords.clear();
        aaaaRecords.clear();
    }

    private static String normalizeDomain(String domain) {
        String d = domain.trim().toLowerCase(Locale.ROOT);
        if (d.endsWith(".")) {
            d = d.substring(0, d.length() - 1);
        }
        return d;
    }
}
