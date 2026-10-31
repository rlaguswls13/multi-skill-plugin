package com.msp.ipasn;

import java.util.Objects;

/**
 * Geo-IP and Autonomous System Number (ASN) metadata record.
 *
 * @param countryCode ISO-3166-1 alpha-2 two-letter country code (e.g., "KR", "US")
 * @param asn Autonomous System Number (e.g., 15169 for Google, 13335 for Cloudflare)
 * @param asName Autonomous System organization/provider name
 */
public record IpGeoAsnRecord(String countryCode, int asn, String asName) {

    public IpGeoAsnRecord {
        Objects.requireNonNull(countryCode, "countryCode cannot be null");
        Objects.requireNonNull(asName, "asName cannot be null");
        countryCode = countryCode.trim().toUpperCase();
    }
}
