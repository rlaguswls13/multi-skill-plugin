package com.msp.jwt;

import java.time.Instant;

/**
 * Thrown when a JWT is received after its expiration time (`exp`).
 */
public class JwtExpiredException extends JwtException {

    private final Instant expiresAt;

    public JwtExpiredException(Instant expiresAt) {
        super("JWT token has expired at: " + expiresAt);
        this.expiresAt = expiresAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
