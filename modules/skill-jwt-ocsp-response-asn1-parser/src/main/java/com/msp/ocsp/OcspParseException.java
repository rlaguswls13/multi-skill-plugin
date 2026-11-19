package com.msp.ocsp;

/**
 * Runtime exception thrown when an OCSP ASN.1 DER payload is malformed or violates RFC 6960.
 */
public final class OcspParseException extends RuntimeException {
    public OcspParseException(String message) {
        super(message);
    }

    public OcspParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
