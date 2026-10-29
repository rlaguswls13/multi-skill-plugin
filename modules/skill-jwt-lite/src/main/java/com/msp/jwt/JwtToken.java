package com.msp.jwt;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Model representing a parsed and validated JSON Web Token (JWT).
 *
 * @param header    JWT header parameters (e.g., alg, typ)
 * @param claims    JWT payload claims
 * @param signature Raw digital signature bytes
 * @param rawToken  Original compact serialized token string ("header.claims.signature")
 */
public record JwtToken(
        Map<String, Object> header,
        JwtClaims claims,
        byte[] signature,
        String rawToken
) {
    public JwtToken {
        Objects.requireNonNull(header, "header must not be null");
        Objects.requireNonNull(claims, "claims must not be null");
        Objects.requireNonNull(signature, "signature must not be null");
        Objects.requireNonNull(rawToken, "rawToken must not be null");
    }

    public String algorithm() {
        Object alg = header.get("alg");
        return alg != null ? alg.toString() : null;
    }

    public String type() {
        Object typ = header.get("typ");
        return typ != null ? typ.toString() : null;
    }

    public String subject() {
        return claims.subject();
    }

    public String issuer() {
        return claims.issuer();
    }

    public Instant expiresAt() {
        return claims.expiresAt();
    }

    public Instant issuedAt() {
        return claims.issuedAt();
    }
}
