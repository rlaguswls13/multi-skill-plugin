package com.msp.httpauth;

/**
 * Supported or recognized HTTP Authorization schemes according to RFC 7235 / RFC 7617 / RFC 6750.
 */
public enum AuthScheme {
    /**
     * HTTP Basic Authentication (RFC 7617)
     */
    BASIC,

    /**
     * OAuth 2.0 Bearer Token (RFC 6750)
     */
    BEARER,

    /**
     * Any other HTTP authorization scheme (e.g. Digest, Hawk, AWS4-HMAC-SHA256, API-Key)
     */
    CUSTOM
}
