package com.msp.httpauth;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Credentials for custom or specialized HTTP authentication schemes (e.g. Digest, Hawk, AWS4-HMAC-SHA256).
 *
 * @param scheme     the custom scheme name
 * @param rawValue   the raw credential string following the scheme
 * @param parameters key-value parameters parsed from the credentials, if structured
 */
public record CustomAuthCredentials(
        String scheme,
        String rawValue,
        Map<String, String> parameters
) implements AuthCredentials {

    public CustomAuthCredentials {
        Objects.requireNonNull(scheme, "scheme cannot be null");
        if (scheme.isBlank()) {
            throw new IllegalArgumentException("scheme cannot be blank");
        }
        rawValue = rawValue == null ? "" : rawValue;
        parameters = parameters == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(parameters));
    }

    @Override
    public AuthScheme authScheme() {
        return AuthScheme.CUSTOM;
    }

    @Override
    public String schemeName() {
        return scheme;
    }
}
