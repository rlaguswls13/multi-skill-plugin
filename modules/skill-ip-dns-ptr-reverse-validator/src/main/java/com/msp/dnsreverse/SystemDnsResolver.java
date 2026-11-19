package com.msp.dnsreverse;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Standard DNS resolver using JNDI DNS provider with fallback to JDK InetAddress.
 */
public class SystemDnsResolver implements DnsResolver {

    public static final SystemDnsResolver INSTANCE = new SystemDnsResolver();

    private final Hashtable<String, String> env;

    public SystemDnsResolver() {
        this.env = new Hashtable<>();
        this.env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
    }

    @Override
    public List<String> resolvePtr(InetAddress ip) {
        if (ip == null) {
            return Collections.emptyList();
        }

        // Try JNDI PTR lookup first
        try {
            DirContext ctx = new InitialDirContext(env);
            String reverseDnsName = toReverseDnsName(ip);
            Attributes attrs = ctx.getAttributes(reverseDnsName, new String[]{"PTR"});
            Attribute attr = attrs.get("PTR");
            if (attr != null) {
                List<String> results = new ArrayList<>();
                NamingEnumeration<?> en = attr.getAll();
                while (en.hasMore()) {
                    Object val = en.next();
                    if (val != null) {
                        String domain = val.toString().trim();
                        if (domain.endsWith(".")) {
                            domain = domain.substring(0, domain.length() - 1);
                        }
                        results.add(domain.toLowerCase(Locale.ROOT));
                    }
                }
                ctx.close();
                if (!results.isEmpty()) {
                    return results;
                }
            }
            ctx.close();
        } catch (Exception ignored) {
            // Fallback to InetAddress canonical hostname
        }

        // Fallback: InetAddress.getCanonicalHostName()
        String canonical = ip.getCanonicalHostName();
        if (canonical != null && !canonical.equalsIgnoreCase(ip.getHostAddress())) {
            return List.of(canonical.toLowerCase(Locale.ROOT));
        }

        return Collections.emptyList();
    }

    @Override
    public List<InetAddress> resolveA(String domain) {
        return resolveForwardAddresses(domain, true, false);
    }

    @Override
    public List<InetAddress> resolveAaaa(String domain) {
        return resolveForwardAddresses(domain, false, true);
    }

    @Override
    public List<InetAddress> resolveForward(String domain) {
        return resolveForwardAddresses(domain, true, true);
    }

    private List<InetAddress> resolveForwardAddresses(String domain, boolean includeV4, boolean includeV6) {
        if (domain == null || domain.isBlank()) {
            return Collections.emptyList();
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(domain.trim());
            List<InetAddress> filtered = new ArrayList<>();
            for (InetAddress addr : addresses) {
                if (includeV4 && addr instanceof Inet4Address) {
                    filtered.add(addr);
                } else if (includeV6 && addr instanceof Inet6Address) {
                    filtered.add(addr);
                }
            }
            return filtered;
        } catch (UnknownHostException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Converts an IPv4 or IPv6 InetAddress to its in-addr.arpa or ip6.arpa DNS query name.
     */
    public static String toReverseDnsName(InetAddress ip) {
        byte[] bytes = ip.getAddress();
        if (bytes.length == 4) {
            // IPv4: 198.51.100.1 -> 1.100.51.198.in-addr.arpa
            return String.format("%d.%d.%d.%d.in-addr.arpa",
                    bytes[3] & 0xFF, bytes[2] & 0xFF, bytes[1] & 0xFF, bytes[0] & 0xFF);
        } else if (bytes.length == 16) {
            // IPv6: nibble reverse in ip6.arpa
            StringBuilder sb = new StringBuilder();
            for (int i = 15; i >= 0; i--) {
                int b = bytes[i] & 0xFF;
                int lowNibble = b & 0x0F;
                int highNibble = (b >>> 4) & 0x0F;
                sb.append(Integer.toHexString(lowNibble)).append(".");
                sb.append(Integer.toHexString(highNibble)).append(".");
            }
            sb.append("ip6.arpa");
            return sb.toString();
        }
        throw new IllegalArgumentException("Unsupported address length: " + bytes.length);
    }
}
