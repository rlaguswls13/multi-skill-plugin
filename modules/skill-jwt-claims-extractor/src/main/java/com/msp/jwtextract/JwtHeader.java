package com.msp.jwtextract;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable representation of a decoded JWT Header.
 *
 * @param alg    signature algorithm (e.g. "HS256", "RS256")
 * @param typ    token type (typically "JWT")
 * @param claims all raw header claims
 */
public record JwtHeader(
        String alg,
        String typ,
        Map<String, Object> claims
) {
    public JwtHeader {
        claims = claims != null ? Collections.unmodifiableMap(claims) : Collections.emptyMap();
    }

    public Object getClaim(String name) {
        return claims.get(name);
    }

    public String getString(String name) {
        Object val = claims.get(name);
        return val != null ? val.toString() : null;
    }
}
