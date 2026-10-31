package com.msp.dns;

/**
 * Standard DNS record types supported by the DNS resolver.
 */
public enum DnsRecordType {
    /** IPv4 address record */
    A,
    /** IPv6 address record */
    AAAA,
    /** Mail exchange record */
    MX,
    /** Text record (SPF, DKIM, verification tokens) */
    TXT,
    /** Canonical name (alias) record */
    CNAME,
    /** Authoritative name server record */
    NS,
    /** Pointer record (reverse DNS lookup) */
    PTR
}
