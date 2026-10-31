package com.msp.dns;

import java.util.List;
import java.util.Objects;

/**
 * Result of a DNS resolution operation.
 *
 * @param host          the host queried
 * @param type          the requested DNS record type
 * @param records       the resolved records list (empty if failed or no records found)
 * @param elapsedMillis total query time in milliseconds
 * @param success       true if query completed without fatal error
 * @param errorMessage  error message or exception detail if resolution failed
 */
public record DnsLookupResult(
        String host,
        DnsRecordType type,
        List<DnsRecord> records,
        long elapsedMillis,
        boolean success,
        String errorMessage
) {
    public DnsLookupResult {
        Objects.requireNonNull(host, "host cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        records = records == null ? List.of() : List.copyOf(records);
    }

    /**
     * Factory for a successful query result.
     */
    public static DnsLookupResult success(String host, DnsRecordType type, List<DnsRecord> records, long elapsedMillis) {
        return new DnsLookupResult(host, type, records, elapsedMillis, true, null);
    }

    /**
     * Factory for a failed query result.
     */
    public static DnsLookupResult failure(String host, DnsRecordType type, String errorMessage, long elapsedMillis) {
        return new DnsLookupResult(host, type, List.of(), elapsedMillis, false, errorMessage);
    }
}
