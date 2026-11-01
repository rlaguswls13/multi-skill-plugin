package com.msp.httpauth;

import java.util.Objects;

/**
 * Credentials for OAuth 2.0 Bearer Token scheme (RFC 6750).
 *
 * @param token the bearer token string
 */
public record BearerAuthCredentials(String token) implements AuthCredentials {

    public BearerAuthCredentials {
        Objects.requireNonNull(token, "token cannot be null");
        if (token.isBlank()) {
            throw new IllegalArgumentException("Bearer token cannot be blank");
        }
    }

    @Override
    public AuthScheme authScheme() {
        return AuthScheme.BEARER;
    }

    @Override
    public String schemeName() {
        return "Bearer";
    }
}
