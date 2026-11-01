package com.msp.rdap;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds RFC 7482 compliant RDAP REST query URLs for IP addresses, CIDRs, AS numbers, and domains.
 */
public final class RdapQueryBuilder {

    public static final String ARIN_BASE = "https://rdap.arin.net/registry/";
    public static final String RIPE_BASE = "https://rdap.db.ripe.net/";
    public static final String APNIC_BASE = "https://rdap.apnic.net/";
    public static final String LACNIC_BASE = "https://rdap.lacnic.net/rdap/";
    public static final String AFRINIC_BASE = "https://rdap.afrinic.net/rdap/";
    public static final String DEFAULT_BASE = ARIN_BASE;

    private final String baseUrl;

    public RdapQueryBuilder(String baseUrl) {
        String base = baseUrl != null ? baseUrl.trim() : DEFAULT_BASE;
        this.baseUrl = base.endsWith("/") ? base : base + "/";
    }

    public static RdapQueryBuilder create() {
        return new RdapQueryBuilder(DEFAULT_BASE);
    }

    public static RdapQueryBuilder withBase(String baseUrl) {
        return new RdapQueryBuilder(baseUrl);
    }

    /**
     * Builds an IP query URL (RFC 7482 Section 3.1.1).
     * e.g. {@code /ip/192.0.2.1} or {@code /ip/192.0.2.0/24}
     */
    public URI buildIpQuery(String ipOrCidr) {
        if (ipOrCidr == null || ipOrCidr.isBlank()) {
            throw new IllegalArgumentException("IP or CIDR cannot be null or blank");
        }
        String clean = ipOrCidr.trim();
        return URI.create(baseUrl + "ip/" + clean);
    }

    /**
     * Builds an Autnum / ASN query URL (RFC 7482 Section 3.1.2).
     * e.g. {@code /autnum/15169}
     */
    public URI buildAutnumQuery(long asn) {
        if (asn < 0) {
            throw new IllegalArgumentException("ASN cannot be negative");
        }
        return URI.create(baseUrl + "autnum/" + asn);
    }

    /**
     * Builds a Domain query URL (RFC 7482 Section 3.1.3).
     * e.g. {@code /domain/example.com}
     */
    public URI buildDomainQuery(String domain) {
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("Domain cannot be null or blank");
        }
        String encoded = URLEncoder.encode(domain.trim(), StandardCharsets.UTF_8);
        return URI.create(baseUrl + "domain/" + encoded);
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
