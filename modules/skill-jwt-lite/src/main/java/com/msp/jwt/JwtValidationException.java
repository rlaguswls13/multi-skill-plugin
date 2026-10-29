package com.msp.jwt;

/**
 * Thrown when a JWT header or claim fails expected validation (e.g. issuer or audience mismatch).
 */
public class JwtValidationException extends JwtException {

    public JwtValidationException(String message) {
        super(message);
    }
}
