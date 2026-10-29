package com.msp.jwt;

import java.time.Instant;

/**
 * Thrown when a JWT is received before its "not before" time (`nbf`).
 */
public class JwtPrematureException extends JwtException {

    private final Instant notBefore;

    public JwtPrematureException(Instant notBefore) {
        super("JWT token is not valid yet (nbf: " + notBefore + ")");
        this.notBefore = notBefore;
    }

    public Instant getNotBefore() {
        return notBefore;
    }
}
