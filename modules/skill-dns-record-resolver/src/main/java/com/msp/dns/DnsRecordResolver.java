package com.msp.dns;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java DNS record resolver utilizing JNDI DNS Service Provider Interface (SPI)
 * with robust error handling and fallback capabilities.
 */
public class DnsRecordResolver {

    private static final String JNDI_FACTORY = "com.sun.jndi.dns.DnsContextFactory";
    private static final Pattern QUOTE_PATTERN = Pattern.compile("^\"(.*)\"$");

    private final String dnsServer;
    private final int timeoutMillis;
    private final int retries;

    /**
     * Creates a resolver using default operating system DNS configuration.
     */
    public DnsRecordResolver() {
        this(null, 3000, 2);
    }

    /**
     * Creates a resolver using a specific upstream DNS server (e.g., "8.8.8.8" or "1.1.1.1").
     *
     * @param dnsServer the IP address or hostname of DNS server, or null for OS default
     */
    public DnsRecordResolver(String dnsServer) {
        this(dnsServer, 3000, 2);
    }

    /**
     * Creates a resolver with custom DNS server and timeout settings.
     *
     * @param dnsServer     DNS server IP/host, or null for default
     * @param timeoutMillis initial timeout in milliseconds
     * @param retries       number of retry attempts
     */
    public DnsRecordResolver(String dnsServer, int timeoutMillis, int retries) {
        this.dnsServer = (dnsServer != null && !dnsServer.isBlank()) ? dnsServer.trim() : null;
        this.timeoutMillis = Math.max(500, timeoutMillis);
        this.retries = Math.max(1, retries);
    }

    /**
     * Resolves DNS records of the specified type for the given host.
     * Never throws checked network exceptions; captures failures in {@link DnsLookupResult}.
     *
     * @param host domain name or IP (cannot be null)
     * @param type DNS record type (cannot be null)
     * @return resolution result containing status, records, and elapsed time
     */
    public DnsLookupResult resolve(String host, DnsRecordType type) {
        Objects.requireNonNull(host, "host cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        String normalizedHost = host.trim();
        if (normalizedHost.isEmpty()) {
            return DnsLookupResult.failure(host, type, "Host cannot be empty", 0);
        }

        long start = System.currentTimeMillis();
        List<DnsRecord> records = new ArrayList<>();

        DirContext dirContext = null;
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, JNDI_FACTORY);
            if (dnsServer != null) {
                env.put(Context.PROVIDER_URL, "dns://" + dnsServer);
            }
            env.put("com.sun.jndi.dns.timeout.initial", String.valueOf(timeoutMillis));
            env.put("com.sun.jndi.dns.timeout.retries", String.valueOf(retries));

            dirContext = new InitialDirContext(env);
            Attributes attrs = dirContext.getAttributes(normalizedHost, new String[]{type.name()});

            if (attrs != null) {
                Attribute attr = attrs.get(type.name());
                if (attr != null) {
                    NamingEnumeration<?> enumeration = attr.getAll();
                    while (enumeration.hasMore()) {
                        Object item = enumeration.next();
                        if (item != null) {
                            String value = cleanRecordValue(item.toString(), type);
                            records.add(new DnsRecord(normalizedHost, type, value));
                        }
                    }
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            return DnsLookupResult.success(normalizedHost, type, records, elapsed);
        } catch (NamingException e) {
            long elapsed = System.currentTimeMillis() - start;
            return DnsLookupResult.failure(normalizedHost, type, e.getMessage(), elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return DnsLookupResult.failure(normalizedHost, type, "DNS query exception: " + e.getMessage(), elapsed);
        } finally {
            if (dirContext != null) {
                try {
                    dirContext.close();
                } catch (NamingException ignored) {
                }
            }
        }
    }

    /**
     * Resolves IPv4 addresses (A records) for the given host.
     * If JNDI lookup returns empty or fails (e.g. for localhost), falls back to standard {@link InetAddress}.
     *
     * @param host domain name or IP
     * @return list of IPv4 address strings, never null
     */
    public List<String> resolveA(String host) {
        if (host == null || host.isBlank()) {
            return List.of();
        }

        DnsLookupResult result = resolve(host, DnsRecordType.A);
        if (result.success() && !result.records().isEmpty()) {
            return result.records().stream()
                    .map(DnsRecord::value)
                    .toList();
        }

        // Fallback to InetAddress lookup (ideal for localhost / local hosts)
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            return Arrays.stream(addresses)
                    .filter(Inet4Address.class::isInstance)
                    .map(InetAddress::getHostAddress)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * Resolves TXT record values for the given host.
     *
     * @param host domain name
     * @return list of TXT record values, never null
     */
    public List<String> resolveTxt(String host) {
        if (host == null || host.isBlank()) {
            return List.of();
        }

        DnsLookupResult result = resolve(host, DnsRecordType.TXT);
        if (result.success()) {
            return result.records().stream()
                    .map(DnsRecord::value)
                    .toList();
        }
        return List.of();
    }

    private String cleanRecordValue(String raw, DnsRecordType type) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (type == DnsRecordType.TXT) {
            // TXT records often wrapped in quotes or multiple quoted strings
            Matcher matcher = QUOTE_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                return matcher.group(1).replace("\" \"", "");
            }
        }
        return trimmed;
    }
}
