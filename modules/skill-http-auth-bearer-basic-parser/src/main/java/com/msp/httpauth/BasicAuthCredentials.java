package com.msp.httpauth;

import java.util.Objects;

/**
 * Credentials for HTTP Basic Authentication scheme (RFC 7617).
 *
 * @param username user identifier
 * @param password user secret or token
 */
public record BasicAuthCredentials(String username, String password) implements AuthCredentials {

    public BasicAuthCredentials {
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(password, "password cannot be null");
    }

    @Override
    public AuthScheme authScheme() {
        return AuthScheme.BASIC;
    }

    @Override
    public String schemeName() {
        return "Basic";
    }
}
