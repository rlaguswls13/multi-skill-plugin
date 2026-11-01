package com.msp.httpauth;

/**
 * Common sealed interface for parsed HTTP Authorization credentials.
 */
public sealed interface AuthCredentials permits BasicAuthCredentials, BearerAuthCredentials, CustomAuthCredentials {

    /**
     * Returns the scheme category (BASIC, BEARER, or CUSTOM).
     *
     * @return the {@link AuthScheme}
     */
    AuthScheme authScheme();

    /**
     * Returns the scheme name as a string (e.g., "Basic", "Bearer", "Digest").
     *
     * @return the scheme name
     */
    String schemeName();
}
