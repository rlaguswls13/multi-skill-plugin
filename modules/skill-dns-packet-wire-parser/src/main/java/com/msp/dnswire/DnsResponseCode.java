package com.msp.dnswire;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Standard DNS RCODE (Response Code) values according to RFC 1035.
 */
public enum DnsResponseCode {
    NOERROR(0, "No error condition"),
    FORMERR(1, "Format error in query"),
    SERVFAIL(2, "Server failure"),
    NXDOMAIN(3, "Non-existent domain"),
    NOTIMP(4, "Not implemented"),
    REFUSED(5, "Query refused"),
    YXDOMAIN(6, "Name exists when it should not"),
    YXRRSET(7, "RR set exists when it should not"),
    NXRRSET(8, "RR set that should exist does not"),
    NOTAUTH(9, "Server not authoritative for zone"),
    NOTZONE(10, "Name not contained in zone");

    private final int code;
    private final String description;

    private static final Map<Integer, DnsResponseCode> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toMap(DnsResponseCode::code, Function.identity()));

    DnsResponseCode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int code() {
        return code;
    }

    public String description() {
        return description;
    }

    public static DnsResponseCode fromCode(int code) {
        return BY_CODE.get(code);
    }
}
