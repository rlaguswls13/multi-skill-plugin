package com.msp.ocsp;

/**
 * RFC 6960 §4.2.1 {@code OCSPResponseStatus} enumeration.
 */
public enum OcspResponseStatus {
    SUCCESSFUL(0),
    MALFORMED_REQUEST(1),
    INTERNAL_ERROR(2),
    TRY_LATER(3),
    SIG_REQUIRED(5),
    UNAUTHORIZED(6);

    private final int code;

    OcspResponseStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static OcspResponseStatus fromCode(int code) {
        for (OcspResponseStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new OcspParseException("Unknown OCSPResponseStatus code: " + code);
    }
}
