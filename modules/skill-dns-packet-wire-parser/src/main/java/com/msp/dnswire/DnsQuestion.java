package com.msp.dnswire;

import java.util.Objects;

/**
 * DNS Question section entry according to RFC 1035 section 4.1.2.
 *
 * @param name     QNAME: domain name being queried
 * @param type     QTYPE: two-octet code specifying the record type (e.g., A=1, AAAA=28)
 * @param dnsClass QCLASS: two-octet code specifying the class of query (usually IN=1)
 */
public record DnsQuestion(String name, int type, int dnsClass) {

    public DnsQuestion {
        Objects.requireNonNull(name, "name must not be null");
        if (dnsClass <= 0) {
            dnsClass = DnsClass.IN;
        }
    }

    public DnsQuestion(String name, DnsRecordType recordType) {
        this(name, recordType.code(), DnsClass.IN);
    }

    public DnsRecordType recordType() {
        return DnsRecordType.fromCode(type);
    }
}
