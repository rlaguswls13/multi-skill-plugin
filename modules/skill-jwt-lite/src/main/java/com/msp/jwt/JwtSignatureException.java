package com.msp.jwt;

/**
 * Thrown when a JWT digital signature verification fails or signature is tampered with.
 */
public class JwtSignatureException extends JwtException {

    public JwtSignatureException(String message) {
        super(message);
    }

    public JwtSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
