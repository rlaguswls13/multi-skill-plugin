package com.msp.crl;

/**
 * Thrown when parsing of a CRL (PEM or DER format) fails.
 */
public class CrlParseException extends RuntimeException {

    public CrlParseException(String message) {
        super(message);
    }

    public CrlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
