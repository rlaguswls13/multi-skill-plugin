package com.msp.dnswire;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Standard DNS Resource Record Types according to RFC 1035 and RFC 3596.
 */
public enum DnsRecordType {
    A(1, "IPv4 address"),
    NS(2, "Authoritative name server"),
    CNAME(5, "Canonical name for an alias"),
    SOA(6, "Start of a zone of authority"),
    PTR(12, "Domain name pointer"),
    MX(15, "Mail exchange"),
    TXT(16, "Text strings"),
    AAAA(28, "IPv6 address"),
    SRV(33, "Service record"),
    OPT(41, "EDNS0 option"),
    ANY(255, "A request for all records");

    private final int code;
    private final String description;

    private static final Map<Integer, DnsRecordType> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toMap(DnsRecordType::code, Function.identity()));

    DnsRecordType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int code() {
        return code;
    }

    public String description() {
        return description;
    }

    public static DnsRecordType fromCode(int code) {
        return BY_CODE.get(code);
    }
}
