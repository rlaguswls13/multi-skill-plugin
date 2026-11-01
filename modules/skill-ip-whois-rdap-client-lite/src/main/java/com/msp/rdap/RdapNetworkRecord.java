package com.msp.rdap;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents an IP Network registration record (RFC 7483 Section 5.4).
 */
public record RdapNetworkRecord(
        String handle,
        String startAddress,
        String endAddress,
        String ipVersion,
        String name,
        String type,
        String country,
        List<String> status,
        String port43,
        List<RdapEntityRecord> entities,
        Map<String, Instant> events,
        List<String> notices
) {
    public RdapNetworkRecord {
        status = status != null ? List.copyOf(status) : Collections.emptyList();
        entities = entities != null ? List.copyOf(entities) : Collections.emptyList();
        events = events != null ? Map.copyOf(events) : Collections.emptyMap();
        notices = notices != null ? List.copyOf(notices) : Collections.emptyList();
    }
}
