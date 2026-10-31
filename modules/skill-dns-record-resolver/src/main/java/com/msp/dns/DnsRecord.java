package com.msp.dns;

import java.util.Objects;

/**
 * Represents an individual resolved DNS record.
 *
 * @param host       the domain or host queried
 * @param type       the DNS record type
 * @param value      the raw or normalized record value
 * @param ttlSeconds TTL in seconds, or 0 if unspecified/unavailable
 */
public record DnsRecord(
        String host,
        DnsRecordType type,
        String value,
        long ttlSeconds
) {
    public DnsRecord {
        Objects.requireNonNull(host, "host cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        if (ttlSeconds < 0) {
            ttlSeconds = 0;
        }
    }

    /**
     * Creates a DNS record with default 0 TTL.
     */
    public DnsRecord(String host, DnsRecordType type, String value) {
        this(host, type, value, 0);
    }
}
