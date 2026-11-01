package com.msp.rdap;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents an Autonomous System Number (ASN) registration record (RFC 7483 Section 5.5).
 */
public record RdapAutnumRecord(
        String handle,
        long startAutnum,
        long endAutnum,
        String name,
        String type,
        String country,
        List<String> status,
        String port43,
        List<RdapEntityRecord> entities,
        Map<String, Instant> events,
        List<String> notices
) {
    public RdapAutnumRecord {
        status = status != null ? List.copyOf(status) : Collections.emptyList();
        entities = entities != null ? List.copyOf(entities) : Collections.emptyList();
        events = events != null ? Map.copyOf(events) : Collections.emptyMap();
        notices = notices != null ? List.copyOf(notices) : Collections.emptyList();
    }
}
