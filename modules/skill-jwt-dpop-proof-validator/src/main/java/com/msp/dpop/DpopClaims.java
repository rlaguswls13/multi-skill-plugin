package com.msp.dpop;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Validated RFC 9449 DPoP JWT claims.
 */
public record DpopClaims(
        String jti,
        String htm,
        String htu,
        long iat,
        Optional<String> ath,
        Map<String, Object> allClaims
) {
    public DpopClaims {
        allClaims = Collections.unmodifiableMap(allClaims);
    }
}
