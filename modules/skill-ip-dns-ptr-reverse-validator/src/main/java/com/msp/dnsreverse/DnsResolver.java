package com.msp.dnsreverse;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface abstraction for DNS reverse (PTR) and forward (A/AAAA) lookups.
 */
public interface DnsResolver {

    /**
     * Resolves PTR domain names associated with the given IP address.
     *
     * @param ip Target IP address
     * @return List of resolved fully-qualified domain names (or empty list if no PTR record exists)
     */
    List<String> resolvePtr(InetAddress ip);

    /**
     * Resolves IPv4 (A) addresses associated with the given domain name.
     *
     * @param domain Target domain name
     * @return List of IPv4 InetAddress instances
     */
    List<InetAddress> resolveA(String domain);

    /**
     * Resolves IPv6 (AAAA) addresses associated with the given domain name.
     *
     * @param domain Target domain name
     * @return List of IPv6 InetAddress instances
     */
    List<InetAddress> resolveAaaa(String domain);

    /**
     * Resolves all forward IP addresses (both IPv4 and IPv6) for the domain.
     *
     * @param domain Target domain name
     * @return Combined list of resolved IP addresses
     */
    default List<InetAddress> resolveForward(String domain) {
        List<InetAddress> all = new ArrayList<>();
        all.addAll(resolveA(domain));
        all.addAll(resolveAaaa(domain));
        return all;
    }
}
